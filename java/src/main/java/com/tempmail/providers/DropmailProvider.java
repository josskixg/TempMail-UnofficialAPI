package com.tempmail.providers;

import com.tempmail.TempMailProvider;
import com.tempmail.errors.NotFoundException;
import com.tempmail.errors.TempMailException;
import com.tempmail.http.HttpClient;
import com.tempmail.json.SimpleJson;
import com.tempmail.json.SimpleJson.JsonArray;
import com.tempmail.json.SimpleJson.JsonObject;
import com.tempmail.json.SimpleJson.JsonValue;
import com.tempmail.models.Message;
import com.tempmail.models.MessageDetail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Dropmail.me provider - GraphQL based.
 * Uses token-based authentication with GraphQL API.
 * Supports 90d token generation via PaddleOCR captcha solving.
 */
public class DropmailProvider implements TempMailProvider {

    private static final Logger LOG = Logger.getLogger(DropmailProvider.class.getName());
    private static final String TOKEN_URL = "https://dropmail.me/api/token/generate";
    private static final String API_BASE = "https://dropmail.me/api/graphql/";
    private static final String PADDLE_OCR_URL = "https://mamamacjdjj-padle-predict.hf.space/predict";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_ZONED_DATE_TIME;
    private static final DateTimeFormatter DATE_FMT_FALLBACK = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final HttpClient client;

    // Dedicated java.net.http.HttpClient with cookie jar for the captcha session flow.
    private final java.net.http.HttpClient sessionClient;

    private final List<Function<byte[], String>> captchaSolvers;

    private String token;
    private String sessionId;
    private String currentAddressId;
    private String currentEmail;

    public DropmailProvider() {
        this(null);
    }

    public DropmailProvider(List<Function<byte[], String>> captchaSolvers) {
        this.client = new HttpClient(true, false);
        CookieManager jar = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        this.sessionClient = java.net.http.HttpClient.newBuilder()
                .cookieHandler(jar)
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.captchaSolvers = captchaSolvers;
    }

    /**
     * Built-in PaddleOCR solver via HuggingFace space.
     * Tries up to 3 times, returns trimmed text on success or null.
     */
    public static String paddleOcrSolver(byte[] imgBytes) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                String boundary = "----TempMail" + Long.toHexString(Double.doubleToLongBits(Math.random()));
                String body = "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"cap.png\"\r\n"
                        + "Content-Type: image/png\r\n\r\n";
                byte[] header = body.getBytes(StandardCharsets.UTF_8);
                byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
                byte[] multipart = new byte[header.length + imgBytes.length + footer.length];
                System.arraycopy(header, 0, multipart, 0, header.length);
                System.arraycopy(imgBytes, 0, multipart, header.length, imgBytes.length);
                System.arraycopy(footer, 0, multipart, header.length + imgBytes.length, footer.length);

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(PADDLE_OCR_URL))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(multipart))
                        .timeout(Duration.ofSeconds(30))
                        .build();
                HttpResponse<String> resp = java.net.http.HttpClient.newHttpClient()
                        .send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject data = SimpleJson.parse(resp.body()).asObject();
                JsonArray results = data.getArray("results");
                if (results != null && !results.isEmpty()) {
                    JsonObject first = results.get(0).asObject();
                    double confidence = 0;
                    try { confidence = Double.parseDouble(first.get("confidence").asString()); } catch (Exception ignored) {}
                    if (confidence >= 0.7) {
                        String text = first.getString("text").trim();
                        if (!text.isEmpty()) return text;
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    @Override
    public String generateEmail() throws TempMailException {
        token = generateToken();
        String mutation = "mutation { introduceSession { id addresses { id address restoreKey } } }";
        JsonObject data = graphqlRequest(mutation);

        JsonObject introduceSession = data.getObject("introduceSession");
        sessionId = introduceSession.getString("id");

        JsonArray addresses = introduceSession.getArray("addresses");
        if (addresses.isEmpty()) {
            throw new TempMailException("No addresses returned from Dropmail");
        }

        JsonObject firstAddress = addresses.get(0).asObject();
        currentAddressId = firstAddress.getString("id");
        currentEmail = firstAddress.getString("address");

        return currentEmail;
    }

    /** Try 1d first; if 402 attempt captcha→90d; fallback to 1d on failure. */
    private String generateToken() throws TempMailException {
        // Step 1: try 1d token via session client (cookies stored for captcha flow)
        String body1d = "{\"type\":\"af\",\"lifetime\":\"1d\"}";
        HttpRequest req1d = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body1d))
                .timeout(Duration.ofSeconds(30))
                .build();
        try {
            HttpResponse<String> resp = sessionClient.send(req1d, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                JsonObject obj = SimpleJson.parseObject(resp.body());
                String t = obj.getString("token");
                if (t != null && !t.isEmpty()) return t;
                throw new TempMailException("Failed to get Dropmail token");
            } else if (resp.statusCode() == 402) {
                // Captcha required — attempt 90d solve
                JsonObject respObj = SimpleJson.parseObject(resp.body());
                JsonValue captchaVal = respObj.get("captcha");
                if (captchaVal != null && !captchaVal.isNull()) {
                    String solved = solveCaptchaAndGetToken(captchaVal.asObject());
                    if (solved != null && !solved.isEmpty()) return solved;
                }
                LOG.warning("Dropmail: captcha solve failed, falling back to 1d token");
                // Fallback via the main HttpClient
                String fallback = client.post(TOKEN_URL, body1d);
                JsonObject obj = SimpleJson.parseObject(fallback);
                String t = obj.getString("token");
                if (t == null || t.isEmpty()) throw new TempMailException("Failed to get Dropmail token (fallback)");
                return t;
            } else {
                throw new TempMailException("Token generation failed: HTTP " + resp.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TempMailException("Token generation request failed: " + e.getMessage());
        }
    }

    /**
     * Steps 2-5 of the captcha flow.
     * Uses sessionClient (cookie jar shared with step 1) for Dropmail; plain HttpClient for PaddleOCR.
     */
    private String solveCaptchaAndGetToken(JsonObject captcha) {
        String v     = Optional.ofNullable(captcha.getString("v")).orElse("3");
        String nonce = Optional.ofNullable(captcha.getString("nonce")).orElse("");
        String key   = Optional.ofNullable(captcha.getString("key")).orElse("");
        String sig   = Optional.ofNullable(captcha.getString("_sig")).orElse("");

        // Step 2: download captcha image — same session (cookie jar)
        String imgUrl = "https://dropmail.me/captcha/image.png?_r=0"
                + "&v=" + encode(v) + "&nonce=" + encode(nonce)
                + "&key=" + encode(key) + "&_sig=" + encode(sig);
        byte[] imgBytes;
        try {
            HttpRequest imgReq = HttpRequest.newBuilder()
                    .uri(URI.create(imgUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<byte[]> imgResp = sessionClient.send(imgReq, HttpResponse.BodyHandlers.ofByteArray());
            if (imgResp.statusCode() != 200) return null;
            imgBytes = imgResp.body();
        } catch (Exception e) {
            LOG.warning("Dropmail: captcha image download failed: " + e.getMessage());
            return null;
        }

        // Step 3: run solver chain
        String ocrText = null;
        List<Function<byte[], String>> solvers = (this.captchaSolvers != null && !this.captchaSolvers.isEmpty())
                ? this.captchaSolvers
                : List.of(DropmailProvider::paddleOcrSolver);
        for (Function<byte[], String> solver : solvers) {
            try {
                String result = solver.apply(imgBytes);
                if (result != null && !result.trim().isEmpty()) {
                    ocrText = result.trim();
                    break;
                }
            } catch (Exception e) { /* ignore */ }
        }
        if (ocrText == null) return null;

        // Step 4: submit solution — same session, form-encoded
        String formBody = "response=" + encode(ocrText)
                + "&v=" + encode(v) + "&nonce=" + encode(nonce)
                + "&key=" + encode(key) + "&_sig=" + encode(sig);
        try {
            HttpRequest solReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://dropmail.me/captcha/solution"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<String> solResp = sessionClient.send(solReq, HttpResponse.BodyHandlers.ofString());
            JsonObject solData = SimpleJson.parseObject(solResp.body());
            if (!"correct".equals(solData.getString("result"))) {
                LOG.warning("Dropmail: captcha solution rejected");
                return null;
            }
        } catch (Exception e) {
            LOG.warning("Dropmail: captcha solution request failed: " + e.getMessage());
            return null;
        }

        // Step 5: retry token generation with 90d — same session
        String body90d = "{\"type\":\"af\",\"lifetime\":\"90d\"}";
        try {
            HttpRequest tokenReq = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body90d))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> tokenResp = sessionClient.send(tokenReq, HttpResponse.BodyHandlers.ofString());
            if (tokenResp.statusCode() != 200) return null;
            JsonObject obj = SimpleJson.parseObject(tokenResp.body());
            return obj.getString("token");
        } catch (Exception e) {
            LOG.warning("Dropmail: 90d token request failed: " + e.getMessage());
            return null;
        }
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Override
    public List<Message> getInbox(String email) throws TempMailException {
        ensureSession();

        String query = "query { session(id: \"" + sessionId + "\") { mails { id fromAddr headerSubject receivedAt } } }";
        JsonObject data = graphqlRequest(query);
        JsonObject session = data.getObject("session");
        JsonArray mails = session.getArray("mails");

        List<Message> messages = new ArrayList<>();
        for (JsonValue mail : mails.elements()) {
            JsonObject m = mail.asObject();
            messages.add(new Message(
                    m.getString("id"),
                    m.getString("fromAddr"),
                    m.getString("headerSubject"),
                    parseDate(m.getString("receivedAt"))
            ));
        }
        return messages;
    }

    @Override
    public MessageDetail readMessage(String messageId) throws TempMailException {
        ensureSession();

        String query = "query { session(id: \"" + sessionId + "\") { mails { id fromAddr headerSubject receivedAt text html attachments { id name mime rawSize } } } }";
        JsonObject data = graphqlRequest(query);
        JsonObject session = data.getObject("session");
        JsonArray mails = session.getArray("mails");

        for (JsonValue mail : mails.elements()) {
            JsonObject m = mail.asObject();
            if (messageId.equals(m.getString("id"))) {
                return new MessageDetail(
                        m.getString("id"),
                        m.getString("fromAddr"),
                        m.getString("headerSubject"),
                        parseDate(m.getString("receivedAt")),
                        orEmpty(m.getString("text")),
                        orEmpty(m.getString("html")),
                        extractAttachments(m)
                );
            }
        }
        throw new NotFoundException("Message not found: " + messageId);
    }

    @Override
    public boolean deleteEmail(String email) throws TempMailException {
        if (currentAddressId == null) return true;
        ensureSession();
        String mutation = "mutation { deleteAddress(input: { addressId: \"" + escapeJson(currentAddressId) + "\" }) { id } }";
        try {
            graphqlRequest(mutation);
        } catch (TempMailException ignored) { /* best-effort */ }
        token = null; sessionId = null; currentAddressId = null; currentEmail = null;
        return true;
    }

    @Override
    public Optional<Message> waitForEmail(String email, Duration timeout, Duration interval) throws TempMailException {
        long end = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < end) {
            List<Message> inbox = getInbox(email);
            if (!inbox.isEmpty()) return Optional.of(inbox.get(0));
            try { Thread.sleep(interval.toMillis()); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return Optional.empty();
    }

    private JsonObject graphqlRequest(String query) throws TempMailException {
        String body = "{\"query\":\"" + escapeJson(query) + "\"}";
        String resp = client.post(API_BASE + token, body);
        JsonObject respObj = SimpleJson.parseObject(resp);
        JsonValue errors = respObj.get("errors");
        if (errors != null && errors.isArray() && !errors.asArray().isEmpty()) {
            JsonObject firstError = errors.asArray().get(0).asObject();
            throw new TempMailException("GraphQL error: " + firstError.getString("message"));
        }
        return respObj.getObject("data");
    }

    private void ensureSession() throws TempMailException {
        if (token == null || sessionId == null) {
            throw new TempMailException("No session active, call generateEmail() first");
        }
    }

    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(dateStr, DATE_FMT);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(dateStr, DATE_FMT_FALLBACK);
            } catch (DateTimeParseException e2) {
                return LocalDateTime.now();
            }
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String orEmpty(String s) { return s != null ? s : ""; }

    private List<Map<String, Object>> extractAttachments(JsonObject mail) {
        JsonValue attVal = mail.get("attachments");
        if (attVal == null || !attVal.isArray()) return Collections.emptyList();
        List<Map<String, Object>> attachments = new ArrayList<>();
        for (JsonValue a : attVal.asArray().elements()) {
            JsonObject att = a.asObject();
            Map<String, Object> m = new HashMap<>();
            m.put("id",      att.getString("id"));
            m.put("name",    att.getString("name"));
            m.put("mime",    att.getString("mime"));
            m.put("rawSize", att.getString("rawSize"));
            attachments.add(m);
        }
        return attachments;
    }
}
