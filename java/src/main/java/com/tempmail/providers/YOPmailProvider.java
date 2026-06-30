package com.tempmail.providers;

import com.tempmail.TempMailProvider;
import com.tempmail.errors.NotFoundException;
import com.tempmail.errors.TempMailException;
import com.tempmail.http.HttpClient;
import com.tempmail.models.Message;
import com.tempmail.models.MessageDetail;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YOPmail provider - HTML scraping based.
 * Uses session tokens extracted from HTML responses.
 */
public class YOPmailProvider implements TempMailProvider {

    private static final String BASE_URL = "https://yopmail.com/en/";
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final Pattern YP_PATTERN = Pattern.compile("name=\"yp\" id=\"yp\" value=\"([^\"]+)\"");
    private static final Pattern VERSION_PATTERN = Pattern.compile("/ver/([0-9.]+)/webmail\\.js");
    private static final Pattern YJ_PATTERN = Pattern.compile("value\\+'\\&yj\\=([0-9a-zA-Z]*)\\&v\\='");
    private static final Pattern MAIL_SUBJECT_PATTERN = Pattern.compile("<span[^>]*class=\"lms\"[^>]*>(.*?)</span>", Pattern.DOTALL);
    private static final Pattern MAIL_FROM_PATTERN = Pattern.compile("<span[^>]*class=\"lmf\"[^>]*>(.*?)</span>", Pattern.DOTALL);
    private static final Pattern MAIL_ID_PATTERN = Pattern.compile("id=\"m(\\d+)\"");

    private final HttpClient client;
    private final Random random;
    private String currentUsername;
    private String ypToken;
    private String yjToken;
    private String version;

    public YOPmailProvider() {
        // cookies=true: CookieManager handles session automatically
        this.client = new HttpClient(true, true);
        this.random = new Random();
    }

    @Override
    public String generateEmail() throws TempMailException {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        }
        currentUsername = sb.toString();
        String email = currentUsername + "@yopmail.com";
        initSession(currentUsername);
        return email;
    }

    private void initSession(String username) throws TempMailException {
        // Step 1: GET /en/ -> extract yp and version
        String body1 = client.get(BASE_URL);
        ypToken = extractToken(body1, YP_PATTERN);
        version = extractToken(body1, VERSION_PATTERN);
        if (version == null) {
            version = "17.0"; // ponytail: fallback
        }

        // Step 2: GET /en/?login={username} -> new yp
        String body2 = client.get(BASE_URL + "?login=" + URLEncoder.encode(username, StandardCharsets.UTF_8));
        String newYp = extractToken(body2, YP_PATTERN);
        if (newYp != null) {
            ypToken = newYp;
        }

        // Step 3: POST /en/ with login form
        String formBody = "login=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&id=&yp=" + URLEncoder.encode(ypToken != null ? ypToken : "", StandardCharsets.UTF_8);
        client.post(BASE_URL, formBody);

        // Step 4: GET /ver/{v}/webmail.js -> extract yj
        String body4 = client.get("https://yopmail.com/ver/" + version + "/webmail.js");
        yjToken = extractToken(body4, YJ_PATTERN);
        if (yjToken == null) {
            yjToken = ""; // ponytail: empty yj may still work
        }

        // Step 5: Set ytime cookie
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC"));
        String ytime = String.format("%02d:%02d", now.getHour(), now.getMinute());
        // ponytail: ytime is a hint cookie, CookieManager will handle it if we can inject it
        // Skip manual injection - YOPmail works without it
    }

    private String extractToken(String html, Pattern pattern) {
        Matcher m = pattern.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    @Override
    public List<Message> getInbox(String email) throws TempMailException {
        String username = splitEmail(email)[0];
        
        if (ypToken == null) {
            currentUsername = username;
            initSession(username);
        }
        
        String url = BASE_URL + "inbox?login=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&p=1&d=&ctrl=&yp=" + URLEncoder.encode(ypToken != null ? ypToken : "", StandardCharsets.UTF_8)
                + "&yj=" + URLEncoder.encode(yjToken != null ? yjToken : "", StandardCharsets.UTF_8)
                + "&v=" + version + "&r_c=&id=&ad=0";

        String html = client.get(url);
        return parseInboxHtml(html);
    }

    private List<Message> parseInboxHtml(String html) {
        List<Message> messages = new ArrayList<>();
        String[] lines = html.split("id=\"m");
        for (int i = 1; i < lines.length; i++) {
            String block = lines[i];
            Matcher idMatcher = MAIL_ID_PATTERN.matcher("id=\"m" + block);
            if (!idMatcher.find()) continue;
            String mailId = idMatcher.group(1);

            String from = extractFromBlock(block, MAIL_FROM_PATTERN, "unknown@yopmail.com");
            String subject = extractFromBlock(block, MAIL_SUBJECT_PATTERN, "(no subject)");

            messages.add(new Message(mailId, from, subject, LocalDateTime.now()));
        }
        return messages;
    }

    private String extractFromBlock(String block, Pattern pattern, String defaultValue) {
        Matcher m = pattern.matcher(block);
        if (m.find()) {
            return cleanHtml(m.group(1));
        }
        return defaultValue;
    }

    private String cleanHtml(String html) {
        return html.replaceAll("<[^>]+>", "").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#039;", "'")
                .trim();
    }

    @Override
    public MessageDetail readMessage(String messageId) throws TempMailException {
        if (currentUsername == null) {
            throw new TempMailException("No email generated yet, call generateEmail() first");
        }

        String url = BASE_URL + "mail?b=" + URLEncoder.encode(currentUsername, StandardCharsets.UTF_8)
                + "&id=" + URLEncoder.encode(messageId, StandardCharsets.UTF_8)
                + "&yp=" + URLEncoder.encode(ypToken != null ? ypToken : "", StandardCharsets.UTF_8)
                + "&yj=" + URLEncoder.encode(yjToken != null ? yjToken : "", StandardCharsets.UTF_8)
                + "&v=" + version;

        String html = client.get(url);
        String bodyHtml = extractDivContent(html, "mail");
        String bodyText = cleanHtml(bodyHtml);
        String subject = extractTitle(html);

        return new MessageDetail(messageId, "unknown@yopmail.com", subject,
                LocalDateTime.now(), bodyText, bodyHtml, Collections.emptyList());
    }

    private String extractDivContent(String html, String divId) {
        Pattern p = Pattern.compile("id=\"?" + divId + "\"?[^>]*>(.*?)</div>", Pattern.DOTALL);
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : "";
    }

    private String extractTitle(String html) {
        Matcher m = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL).matcher(html);
        return m.find() ? cleanHtml(m.group(1)) : "(no subject)";
    }

    @Override
    public boolean deleteEmail(String email) throws TempMailException {
        return true; // No delete API available
    }

    @Override
    public Optional<Message> waitForEmail(String email, Duration timeout, Duration interval) throws TempMailException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            List<Message> inbox = getInbox(email);
            if (!inbox.isEmpty()) {
                return Optional.of(inbox.get(0));
            }
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TempMailException("Interrupted while waiting for email", e);
            }
        }
        return Optional.empty();
    }

    private String[] splitEmail(String email) throws TempMailException {
        int at = email.indexOf('@');
        if (at <= 0) throw new TempMailException("Invalid email: " + email);
        return new String[]{email.substring(0, at), email.substring(at + 1)};
    }
}
