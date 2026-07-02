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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * tempmail.plus provider - REST API, no auth, email as query param.
 */
public class TempmailPlusProvider implements TempMailProvider {

    private static final String BASE_URL = "https://tempmail.plus";
    private static final String DOMAIN = "mailto.plus";
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HttpClient client;
    private final Random random = new Random();
    private String email;

    public TempmailPlusProvider() {
        this.client = new HttpClient(true, true);
    }

    @Override
    public String generateEmail() throws TempMailException {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        email = sb.toString() + "@" + DOMAIN;
        return email;
    }

    @Override
    public List<Message> getInbox(String email) throws TempMailException {
        JsonObject data = SimpleJson.parseObject(client.get(BASE_URL + "/api/mails?email=" + enc(email)));
        if (!data.getBoolean("result")) {
            // ponytail: result defaults to true on success; only flag errors when explicitly false
            if (data.has("result")) throw new TempMailException("tempmail.plus: API returned error");
        }
        JsonArray items = data.getArray("mail_list");
        List<Message> messages = new ArrayList<>();
        for (JsonValue el : items.elements()) {
            JsonObject item = el.asObject();
            messages.add(new Message(
                    item.getString("mail_id"),
                    item.getString("from_mail"),
                    item.getString("subject"),
                    parseDate(item.getString("time"))
            ));
        }
        return messages;
    }

    @Override
    public MessageDetail readMessage(String messageId) throws TempMailException {
        if (email == null) throw new TempMailException("tempmail.plus: no email - call generateEmail() first");
        JsonObject data = SimpleJson.parseObject(client.get(
                BASE_URL + "/api/mails/" + enc(messageId) + "?email=" + enc(email)));
        String text = data.getString("text");
        String html = data.getString("html");
        return new MessageDetail(
                data.has("mail_id") ? data.getString("mail_id") : messageId,
                str(data.get("from_mail"), data.get("from")),
                data.getString("subject"),
                parseDate(str(data.get("time"), data.get("date"))),
                text != null ? text : "",
                html != null ? html : "",
                extractAttachments(data)
        );
    }

    @Override
    public boolean deleteEmail(String email) throws TempMailException {
        this.email = null;
        return true;
    }

    @Override
    public Optional<Message> waitForEmail(String email, Duration timeout, Duration interval) throws TempMailException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            List<Message> inbox = getInbox(email);
            if (!inbox.isEmpty()) return Optional.of(inbox.get(0));
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TempMailException("Interrupted while waiting for email", e);
            }
        }
        return Optional.empty();
    }

    private List<Map<String, Object>> extractAttachments(JsonObject data) {
        JsonArray arr = data.getArray("attachments");
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonValue el : arr.elements()) {
            JsonObject a = el.asObject();
            Map<String, Object> m = new HashMap<>();
            m.put("filename", a.getString("filename"));
            m.put("content_type", a.getString("content_type"));
            m.put("size", a.getInt("size"));
            out.add(m);
        }
        return out;
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String str(JsonValue... vals) {
        for (JsonValue v : vals) {
            if (v != null && !v.isNull()) {
                String s = v.asString();
                if (s != null && !s.isEmpty()) return s;
            }
        }
        return "";
    }

    private LocalDateTime parseDate(String s) {
        if (s == null || s.isEmpty()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(s, DATE_FMT);
        } catch (Exception ignore) {}
        try {
            return LocalDateTime.parse(s.replace("Z", "").replace("+00:00", ""));
        } catch (Exception ignore) {}
        try {
            return LocalDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (Exception ignore) {}
        return LocalDateTime.now();
    }
}
