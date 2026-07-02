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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * tempmail.lol provider - REST API, token-based, no auth.
 */
public class TempmailLolProvider implements TempMailProvider {

    private static final String BASE_URL = "https://api.tempmail.lol/v2";

    private final HttpClient client;
    private String email;
    private String token;

    public TempmailLolProvider() {
        this.client = new HttpClient(true, false);
    }

    @Override
    public String generateEmail() throws TempMailException {
        String resp = client.post(BASE_URL + "/inbox/create", "", Map.of("Content-Type", "application/json"));
        JsonObject data = SimpleJson.parseObject(resp);
        email = data.getString("address");
        token = data.getString("token");
        if (email == null || email.isEmpty() || token == null || token.isEmpty()) {
            throw new TempMailException("tempmail.lol: missing address or token in response");
        }
        return email;
    }

    @Override
    public List<Message> getInbox(String email) throws TempMailException {
        if (token == null) throw new TempMailException("tempmail.lol: no token - call generateEmail() first");
        JsonObject data = SimpleJson.parseObject(client.get(BASE_URL + "/inbox?token=" + enc(token)));
        if (data.getBoolean("expired")) throw new TempMailException("tempmail.lol: token expired");
        JsonArray emails = data.getArray("emails");
        List<Message> messages = new ArrayList<>();
        for (JsonValue el : emails.elements()) {
            JsonObject item = el.asObject();
            messages.add(new Message(
                    str(item.get("_id"), item.get("id"), item.get("uid")),
                    str(item.get("from"), item.get("sender")),
                    item.getString("subject"),
                    parseDate(str(item.get("date"), item.get("createdAt")))
            ));
        }
        return messages;
    }

    @Override
    public MessageDetail readMessage(String messageId) throws TempMailException {
        if (token == null) throw new TempMailException("tempmail.lol: no token - call generateEmail() first");
        JsonObject data = SimpleJson.parseObject(client.get(BASE_URL + "/inbox?token=" + enc(token)));
        JsonArray emails = data.getArray("emails");
        for (JsonValue el : emails.elements()) {
            JsonObject item = el.asObject();
            if (str(item.get("_id"), item.get("id"), item.get("uid")).equals(messageId)) {
                String body = str(item.get("body"), item.get("text"));
                String html = item.getString("html");
                return new MessageDetail(
                        messageId,
                        str(item.get("from"), item.get("sender")),
                        item.getString("subject"),
                        parseDate(str(item.get("date"), item.get("createdAt"))),
                        body != null ? body : "",
                        html != null ? html : "",
                        Collections.emptyList()
                );
            }
        }
        throw new NotFoundException("tempmail.lol: message " + messageId + " not found");
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
            return LocalDateTime.parse(s.replace("Z", "").replace("+00:00", ""));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
