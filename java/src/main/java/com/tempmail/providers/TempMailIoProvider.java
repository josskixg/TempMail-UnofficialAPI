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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * temp-mail.io provider - REST API, Bearer token.
 */
public class TempMailIoProvider implements TempMailProvider {

    private static final String BASE_URL = "https://api.internal.temp-mail.io/api/v3";

    private final HttpClient client;
    private String email;
    private String token;

    public TempMailIoProvider() {
        this.client = new HttpClient(true, false);
    }

    @Override
    public String generateEmail() throws TempMailException {
        JsonObject body = SimpleJson.object();
        body.put("min_name_length", SimpleJson.val(6));
        body.put("max_name_length", SimpleJson.val(12));
        String resp = client.post(BASE_URL + "/email/new", body.toJson(), authHeaders(true));
        JsonObject data = SimpleJson.parseObject(resp);
        email = data.getString("email");
        token = data.getString("token");
        if (email == null || email.isEmpty()) throw new TempMailException("temp-mail.io: missing email in response");
        return email;
    }

    @Override
    public List<Message> getInbox(String email) throws TempMailException {
        String resp = client.get(BASE_URL + "/email/" + enc(email) + "/messages", authHeaders(false));
        JsonValue v = SimpleJson.parse(resp);
        JsonArray items = v.isArray() ? v.asArray() : v.asObject().getArray("messages");
        List<Message> messages = new ArrayList<>();
        for (JsonValue el : items.elements()) {
            JsonObject item = el.asObject();
            messages.add(new Message(
                    str(item.get("id"), item.get("uid")),
                    extractSender(item),
                    item.getString("subject"),
                    parseDate(str(item.get("created_at"), item.get("date")))
            ));
        }
        return messages;
    }

    @Override
    public MessageDetail readMessage(String messageId) throws TempMailException {
        String resp = client.get(BASE_URL + "/email/" + enc(email) + "/messages", authHeaders(false));
        JsonValue v = SimpleJson.parse(resp);
        JsonArray items = v.isArray() ? v.asArray() : v.asObject().getArray("messages");
        for (JsonValue el : items.elements()) {
            JsonObject item = el.asObject();
            if (str(item.get("id"), item.get("uid")).equals(messageId)) {
                String text = str(item.get("body_text"), item.get("text"));
                String html = str(item.get("body_html"), item.get("html"));
                return new MessageDetail(
                        messageId,
                        extractSender(item),
                        item.getString("subject"),
                        parseDate(str(item.get("created_at"), item.get("date"))),
                        text,
                        html,
                        extractAttachments(item)
                );
            }
        }
        throw new NotFoundException("temp-mail.io: message " + messageId + " not found");
    }

    @Override
    public boolean deleteEmail(String email) throws TempMailException {
        this.token = null;
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

    private Map<String, String> authHeaders(boolean withContentType) {
        Map<String, String> h = new HashMap<>();
        h.put("Content-Type", "application/json");
        if (token != null && !token.isEmpty()) h.put("Authorization", "Bearer " + token);
        return h;
    }

    private String extractSender(JsonObject item) {
        JsonValue from = item.get("from");
        if (from == null || from.isNull()) return "";
        if (from.isObject()) {
            JsonObject o = from.asObject();
            return str(o.get("address"), o.get("name"));
        }
        return from.asString();
    }

    private List<Map<String, Object>> extractAttachments(JsonObject item) {
        JsonArray arr = item.getArray("attachments");
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
        // ponytail: don't encode @ — temp-mail.io API expects raw email in URL path
        return s;
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
            return LocalDateTime.parse(s.replace("Z", "").replace("+00:00", ""));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
