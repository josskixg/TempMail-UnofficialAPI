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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Zoromail provider - REST API, no auth. Response envelope: {success, data, error}.
 */
public class ZoromailProvider implements TempMailProvider {

    private static final String BASE_URL = "https://zoromail.com/public_api.php/v1";
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HttpClient client;
    private final Random random = new Random();
    private String email;

    public ZoromailProvider() {
        this.client = new HttpClient(true, false);
    }

    @Override
    public String generateEmail() throws TempMailException {
        JsonArray domains = api("GET", "/domains", null).asArray();
        if (domains == null || domains.isEmpty()) throw new TempMailException("zoromail: no domains available");
        JsonValue d = domains.get(random.nextInt(domains.size()));
        String domain = d.isObject() ? d.asObject().getString("domain") : d.asString();
        String username = randUser();
        JsonObject body = SimpleJson.object();
        body.put("username", SimpleJson.val(username));
        body.put("domain", SimpleJson.val(domain));
        JsonObject data = api("POST", "/emails", body.toJson()).asObject();
        email = data.getString("email");
        if (email == null || email.isEmpty()) throw new TempMailException("zoromail: no email in response");
        return email;
    }

    @Override
    public List<Message> getInbox(String email) throws TempMailException {
        JsonArray items = api("GET", "/emails/" + email + "/messages", null).asArray();
        List<Message> messages = new ArrayList<>();
        if (items == null) return messages;
        for (JsonValue el : items.elements()) {
            JsonObject item = el.asObject();
            messages.add(new Message(
                    item.getString("id"),
                    item.getString("from"),
                    item.getString("subject"),
                    parseDate(item.getString("date"))
            ));
        }
        return messages;
    }

    @Override
    public MessageDetail readMessage(String messageId) throws TempMailException {
        JsonObject data = api("GET", "/messages/" + messageId, null).asObject();
        String text = data.getString("text");
        String html = data.getString("html");
        return new MessageDetail(
                data.getString("id"),
                data.getString("from"),
                data.getString("subject"),
                parseDate(data.getString("date")),
                text != null ? text : "",
                html != null ? html : "",
                Collections.emptyList()
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

    // Returns the "data" field after checking success.
    private JsonValue api(String method, String path, String body) throws TempMailException {
        String resp = "GET".equals(method)
                ? client.get(BASE_URL + path)
                : client.post(BASE_URL + path, body, Map.of("Content-Type", "application/json"));
        JsonObject root = SimpleJson.parseObject(resp);
        if (!root.getBoolean("success")) {
            throw new TempMailException("zoromail API error: " + root.getString("error"));
        }
        return root.get("data");
    }

    private String randUser() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        return sb.toString();
    }

    private LocalDateTime parseDate(String s) {
        if (s == null || s.isEmpty()) return LocalDateTime.now();
        try {
            String t = s.replace("Z", "").replace("+00:00", "");
            if (t.contains("T")) return LocalDateTime.parse(t);
            return LocalDateTime.parse(t, DATE_FMT);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
