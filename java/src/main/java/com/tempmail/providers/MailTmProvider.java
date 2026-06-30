package com.tempmail.providers;

import com.tempmail.TempMailProvider;
import com.tempmail.errors.NotFoundException;
import com.tempmail.errors.RateLimitException;
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
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MailTmProvider implements TempMailProvider {

    private static final String BASE_URL = "https://api.mail.tm";
    private final HttpClient client;
    private final Map<String, AccountInfo> accounts = new ConcurrentHashMap<>();

    public MailTmProvider() {
        this.client = new HttpClient(true, false);
    }

    @Override
    public String generateEmail() throws TempMailException {
        int[] retryDelays = {1, 3, 5};
        for (int attempt = 0; attempt <= retryDelays.length; attempt++) {
            try {
                return generateEmailCore();
            } catch (RateLimitException e) {
                if (attempt < retryDelays.length) {
                    sleep(Duration.ofSeconds(retryDelays[attempt]));
                } else {
                    throw e;
                }
            }
        }
        throw new TempMailException("unreachable");
    }

    private String generateEmailCore() throws TempMailException {
        String domain = getFirstDomain();
        String password = UUID.randomUUID().toString().substring(0, 12);
        String address = "tmp." + UUID.randomUUID().toString().substring(0, 8) + "@" + domain;

        JsonObject accBody = SimpleJson.object();
        accBody.put("address", SimpleJson.val(address));
        accBody.put("password", SimpleJson.val(password));

        // Check account creation response
        String createResp = client.post(BASE_URL + "/accounts", accBody.toJson());
        JsonObject created = SimpleJson.parseObject(createResp);
        if (created == null || created.getString("id") == null) {
            throw new TempMailException("Account creation failed for " + address, 0);
        }

        JsonObject tokenBody = SimpleJson.object();
        tokenBody.put("address", SimpleJson.val(address));
        tokenBody.put("password", SimpleJson.val(password));

        String tokenResp = client.post(BASE_URL + "/token", tokenBody.toJson());
        JsonObject tokenObj = SimpleJson.parseObject(tokenResp);
        String token = tokenObj.getString("token");
        String id = tokenObj.getString("id");
        if (token == null || token.isEmpty()) {
            throw new TempMailException("Token creation failed for " + address, 0);
        }

        accounts.put(address, new AccountInfo(id, token));
        return address;
    }

    @Override
    public List<Message> getInbox(String email) throws TempMailException {
        AccountInfo info = accounts.get(email);
        if (info == null) throw new NotFoundException("No account found for " + email);

        String body = client.get(BASE_URL + "/messages?page=1", Map.of("Authorization", "Bearer " + info.token));
        JsonObject root = SimpleJson.parseObject(body);
        JsonArray items = root.has("hydra:member") ? root.getArray("hydra:member") : SimpleJson.array();
        List<Message> messages = new ArrayList<>();
        for (JsonValue el : items.elements()) {
            JsonObject obj = el.asObject();
            messages.add(new Message(
                    obj.getString("id"),
                    extractAddress(obj.getObject("from")),
                    obj.getString("subject"),
                    parseIsoDate(obj.getString("createdAt"))
            ));
        }
        return messages;
    }

    @Override
    public MessageDetail readMessage(String messageId) throws TempMailException {
        String[] parts = messageId.split(":", 2);
        String id = parts[0];
        String token = parts.length > 1 ? parts[1] : null;

        if (token == null) {
            for (AccountInfo info : accounts.values()) {
                try {
                    String body = client.get(BASE_URL + "/messages/" + id, Map.of("Authorization", "Bearer " + info.token));
                    return parseMessageDetail(body);
                } catch (TempMailException e) {
                    // try next
                }
            }
            throw new NotFoundException("Message not found: " + id);
        }

        String body = client.get(BASE_URL + "/messages/" + id, Map.of("Authorization", "Bearer " + token));
        return parseMessageDetail(body);
    }

    @Override
    public boolean deleteEmail(String email) throws TempMailException {
        AccountInfo info = accounts.get(email);
        if (info == null) return false;
        try {
            String meBody = client.get(BASE_URL + "/me", Map.of("Authorization", "Bearer " + info.token));
            JsonObject me = SimpleJson.parseObject(meBody);
            String accountId = me.getString("id");
            if (accountId != null && !accountId.isEmpty()) {
                client.delete(BASE_URL + "/accounts/" + accountId, Map.of("Authorization", "Bearer " + info.token));
            }
        } catch (TempMailException e) {
            return false;
        }
        accounts.remove(email);
        return true;
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

    private String getFirstDomain() throws TempMailException {
        String body = client.get(BASE_URL + "/domains");
        JsonObject root = SimpleJson.parseObject(body);
        JsonArray domains = root.has("hydra:member") ? root.getArray("hydra:member") : SimpleJson.array();
        if (domains.isEmpty()) throw new TempMailException("No domains available");
        return domains.get(0).asObject().getString("domain");
    }

    private String extractAddress(JsonObject from) {
        return from != null ? from.getString("address") : "";
    }

    private MessageDetail parseMessageDetail(String body) {
        JsonObject obj = SimpleJson.parseObject(body);
        String text = obj.getString("text");
        String html = obj.getString("html");
        return new MessageDetail(
                obj.getString("id"),
                extractAddress(obj.getObject("from")),
                obj.getString("subject"),
                parseIsoDate(obj.getString("createdAt")),
                text != null ? text : "",
                html != null ? html : "",
                extractAttachments(obj)
        );
    }

    private List<Map<String, Object>> extractAttachments(JsonObject message) {
        List<Map<String, Object>> attachments = new ArrayList<>();
        if (message.has("attachments")) {
            JsonArray arr = message.getArray("attachments");
            for (JsonValue el : arr.elements()) {
                JsonObject att = el.asObject();
                Map<String, Object> a = new HashMap<>();
                a.put("downloadUrl", att.getString("downloadUrl"));
                a.put("filename", att.getString("filename"));
                attachments.add(a);
            }
        }
        return attachments;
    }

    private LocalDateTime parseIsoDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }
    }

    private void sleep(Duration d) throws TempMailException {
        try { Thread.sleep(d.toMillis()); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TempMailException("Interrupted while waiting", e);
        }
    }

    private static class AccountInfo {
        final String accountId;
        final String token;
        AccountInfo(String accountId, String token) {
            this.accountId = accountId;
            this.token = token;
        }
    }
}
