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
import java.util.Optional;

/**
 * tempmailc.com provider - REST API, no auth, no cookies.
 */
public class TempmailcProvider implements TempMailProvider {

    private static final String BASE_URL = "https://tempmailc.com/api/v1";

    private final HttpClient client;
    private String email;

    public TempmailcProvider() {
        this.client = new HttpClient(true, false);
    }

    @Override
    public String generateEmail() throws TempMailException {
        JsonObject data = SimpleJson.parseObject(client.get(BASE_URL + "/new"));
        if (!data.getBoolean("ok")) throw new TempMailException("tempmailc: API returned not ok");
        email = data.getString("email");
        if (email == null || email.isEmpty()) throw new TempMailException("tempmailc: no email in response");
        return email;
    }

    @Override
    public List<Message> getInbox(String email) throws TempMailException {
        JsonObject data = SimpleJson.parseObject(client.get(BASE_URL + "/inbox?email=" + enc(email)));
        JsonArray items = data.getArray("messages");
        List<Message> messages = new ArrayList<>();
        for (JsonValue el : items.elements()) {
            JsonObject item = el.asObject();
            messages.add(new Message(
                    str(item.get("id"), item.get("msg_id")),
                    str(item.get("from"), item.get("from_mail")),
                    item.getString("subject"),
                    parseDate(str(item.get("date"), item.get("time")))
            ));
        }
        return messages;
    }

    @Override
    public MessageDetail readMessage(String messageId) throws TempMailException {
        if (email == null) throw new TempMailException("tempmailc: no email - call generateEmail() first");
        JsonObject data = SimpleJson.parseObject(client.get(
                BASE_URL + "/message?msg_id=" + enc(messageId) + "&email=" + enc(email)));
        String text = str(data.get("text"), data.get("body_text"));
        String html = str(data.get("html"), data.get("body_html"));
        return new MessageDetail(
                str(data.get("id"), SimpleJson.val(messageId)),
                str(data.get("from"), data.get("from_mail")),
                data.getString("subject"),
                parseDate(str(data.get("date"), data.get("time"))),
                text,
                html,
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
