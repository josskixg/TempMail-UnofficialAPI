package com.tempmail.providers;

import com.tempmail.TempMailProvider;
import com.tempmail.errors.TempMailException;
import com.tempmail.http.HttpClient;
import com.tempmail.json.SimpleJson;
import com.tempmail.json.SimpleJson.JsonArray;
import com.tempmail.json.SimpleJson.JsonObject;
import com.tempmail.json.SimpleJson.JsonValue;
import com.tempmail.models.Message;
import com.tempmail.models.MessageDetail;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OneSecEmailProvider implements TempMailProvider {

    private static final String BASE = "https://www.1secemail.com";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] DOMAINS = {
        "qzueos.com", "gaziw.com", "emailgenerator.xyz",
    };
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private final HttpClient client;
    private final Random random;
    private String csrf;
    private String currentEmail;

    public OneSecEmailProvider() {
        this.client = new HttpClient(true, true);
        this.random = new Random();
    }

    @Override
    public String generateEmail() throws TempMailException {
        ensureCSRF();
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        String name = sb.toString();
        String domain = DOMAINS[random.nextInt(DOMAINS.length)];
        postFormJson("/change", Map.of("name", name, "domain", domain));
        currentEmail = name + "@" + domain;
        return currentEmail;
    }

    private void ensureCSRF() throws TempMailException {
        if (csrf != null) return;
        String html = client.get(BASE + "/");
        Matcher csrfM = Pattern.compile("<meta name=\"csrf-token\" content=\"([^\"]+)\">").matcher(html);
        if (!csrfM.find()) throw new TempMailException("CSRF token not found on 1secemail");
        csrf = csrfM.group(1);
    }

    private String postFormJson(String path, Map<String, String> data) throws TempMailException {
        ensureCSRF();
        Map<String, String> body = new HashMap<>();
        body.put("_token", csrf);
        body.putAll(data);
        Map<String, String> headers = new HashMap<>();
        headers.put("X-CSRF-TOKEN", csrf);
        headers.put("x-xsrf-token", csrf);
        headers.put("Referer", BASE + "/");
        return client.post(BASE + path, toJson(body), headers);
    }

    @Override
    public List<Message> getInbox(String email) throws TempMailException {
        String respStr = postFormJson("/get_messages", Collections.emptyMap());
        if (respStr.startsWith("[")) {
            return parseInboxArray(respStr);
        }
        JsonObject result = SimpleJson.parse(respStr).asObject();
        JsonArray arr = result.getArray("messages");
        if (arr == null) return Collections.emptyList();
        List<Message> msgs = new ArrayList<>();
        for (JsonValue v : arr.elements()) {
            JsonObject m = v.asObject();
            String sender = firstOf(m.getString("from_email"), m.getString("from"), "unknown");
            String subject = firstOf(m.getString("subject"), "(no subject)");
            LocalDateTime date = parseDate(m.getString("receivedAt"));
            msgs.add(new Message(m.getString("id"), sender, subject, date));
        }
        return msgs;
    }

    private List<Message> parseInboxArray(String json) {
        JsonArray arr = SimpleJson.parse(json).asArray();
        if (arr == null) return Collections.emptyList();
        List<Message> msgs = new ArrayList<>();
        for (JsonValue v : arr.elements()) {
            JsonObject m = v.asObject();
            String sender = firstOf(m.getString("from_email"), m.getString("from"), "unknown");
            String subject = firstOf(m.getString("subject"), "(no subject)");
            LocalDateTime date = parseDate(m.getString("receivedAt"));
            msgs.add(new Message(m.getString("id"), sender, subject, date));
        }
        return msgs;
    }

    @Override
    public MessageDetail readMessage(String messageId) throws TempMailException {
        ensureCSRF();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-CSRF-TOKEN", csrf);
        headers.put("Referer", BASE + "/");
        String html = client.get(BASE + "/view/" + messageId, headers);
        String text = html.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
        String sender = extractRegex(html, "From:\\s*([^<\\n]+)", "unknown");
        String subject = extractRegex(html, "Subject:\\s*([^<\\n]+)", "(no subject)");
        LocalDateTime date = parseDate(extractRegex(html, "(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})", null));
        return new MessageDetail(messageId, sender, subject, date, text, html, Collections.emptyList());
    }

    @Override
    public boolean deleteEmail(String email) throws TempMailException {
        return true;
    }

    @Override
    public Optional<Message> waitForEmail(String email, java.time.Duration timeout, java.time.Duration interval) throws TempMailException {
        long end = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < end) {
            List<Message> inbox = getInbox(email);
            if (!inbox.isEmpty()) return Optional.of(inbox.get(0));
            try { Thread.sleep(interval.toMillis()); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return Optional.empty();
    }

    private String toJson(Map<String, String> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : m.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(e.getKey())).append("\":\"").append(escapeJson(e.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String firstOf(String... vals) {
        for (String v : vals) if (v != null && !v.isEmpty()) return v;
        return vals.length > 0 ? vals[vals.length - 1] : "";
    }

    private String extractRegex(String html, String regex, String fallback) {
        Matcher m = Pattern.compile(regex).matcher(html);
        return m.find() ? m.group(1).trim() : fallback;
    }

    private LocalDateTime parseDate(String s) {
        if (s == null) return LocalDateTime.now();
        try { return LocalDateTime.parse(s, DATE_FMT); } catch (Exception e) { return LocalDateTime.now(); }
    }
}