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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class GuerrillaMailProvider implements TempMailProvider {

    private static final String BASE_URL = "https://api.guerrillamail.com/ajax.php";
    private final HttpClient client;

    public GuerrillaMailProvider() {
        this.client = new HttpClient(true, true);
    }

    @Override
    public String generateEmail() throws TempMailException {
        String body = client.get(BASE_URL + "?f=get_email_address&lang=en");
        JsonObject obj = SimpleJson.parseObject(body);
        return obj.getString("email_addr");
    }

    @Override
    public List<Message> getInbox(String email) throws TempMailException {
        String body = client.get(BASE_URL + "?f=check_email&offset=0");
        JsonObject root = SimpleJson.parseObject(body);
        if (root == null) return new ArrayList<>();
        JsonArray emails = root.has("list") ? root.getArray("list") : SimpleJson.array();
        List<Message> messages = new ArrayList<>();

        for (JsonValue el : emails.elements()) {
            JsonObject obj = el.asObject();
            messages.add(new Message(
                    obj.getString("mail_id"),
                    obj.getString("mail_from"),
                    obj.getString("mail_subject"),
                    parseTimestamp(obj.getString("mail_timestamp"))
            ));
        }
        return messages;
    }

    @Override
    public MessageDetail readMessage(String messageId) throws TempMailException {
        String body = client.get(BASE_URL + "?f=fetch_email&id=" + messageId);
        JsonObject obj = SimpleJson.parseObject(body);
        if (obj == null) throw new NotFoundException("Message not found: " + messageId);

        return new MessageDetail(
                messageId,
                orEmpty(obj.getString("mail_from")),
                orEmpty(obj.getString("mail_subject")),
                parseTimestamp(obj.getString("mail_timestamp")),
                orEmpty(obj.getString("text_body")),
                orEmpty(obj.getString("html")),
                Collections.emptyList()
        );
    }

    @Override
    public boolean deleteEmail(String email) throws TempMailException {
        try {
            String body = client.get(BASE_URL + "?f=delete_email&ids=all");
            JsonObject obj = SimpleJson.parseObject(body);
            return obj != null && "true".equals(obj.getString("result"));
        } catch (TempMailException e) {
            return false;
        }
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

    private String orEmpty(String s) {
        return s != null ? s : "";
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return LocalDateTime.now();
        try {
            return LocalDateTime.ofEpochSecond(Long.parseLong(timestamp), 0, ZoneOffset.UTC);
        } catch (NumberFormatException e) {
            try {
                return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (DateTimeParseException ex) {
                return LocalDateTime.now();
            }
        }
    }
}
