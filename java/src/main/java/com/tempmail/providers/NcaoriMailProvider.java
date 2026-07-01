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
import java.util.Optional;
import java.util.Random;

public class NcaoriMailProvider implements TempMailProvider {

    private static final String BASE = "https://www.nca.my.id";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_DATE_TIME;
    private static final String[] DOMAINS = { "ncaori.my.id", "nca.my.id" };

    private static final String[] WORDS = {
        "swift", "crystal", "storm", "frost", "shadow", "ember", "azure",
        "phantom", "silver", "iron", "crimson", "golden", "neo", "cosmic", "lunar",
        "solar", "dark", "light", "void", "flux",
    };

    private static final String[] WORDS2 = {
        "core", "leaf", "forge", "wave", "peak", "gate", "pulse",
        "blade", "shard", "drift", "hive", "node", "edge", "beacon", "nova",
        "storm", "cloud", "moon", "star", "wind",
    };

    private final HttpClient client;
    private final Random random;

    public NcaoriMailProvider() {
        this.client = new HttpClient(false, true);
        this.random = new Random();
    }

    @Override
    public String generateEmail() throws TempMailException {
        String name = WORDS[random.nextInt(WORDS.length)] + "_" + WORDS2[random.nextInt(WORDS2.length)];
        String domain = DOMAINS[random.nextInt(DOMAINS.length)];
        return name + "@" + domain;
    }

    @Override
    public List<Message> getInbox(String email) throws TempMailException {
        String respStr = client.get(BASE + "/api/emails?recipient=" + email);
        JsonObject root = SimpleJson.parse(respStr).asObject();
        JsonArray arr = root.getArray("emails");
        if (arr == null) return Collections.emptyList();

        List<Message> msgs = new ArrayList<>();
        for (JsonValue v : arr.elements()) {
            JsonObject m = v.asObject();
            String id = firstOf(m.getString("id"), "");
            String sender = firstOf(m.getString("sender"), "unknown");
            String subject = firstOf(m.getString("subject"), "(no subject)");
            LocalDateTime date = parseDate(m.getString("created_at"));
            String bodyText = m.getString("body_text");
            String bodyHtml = m.getString("body_html");

            if ((bodyText != null && !bodyText.isEmpty()) || (bodyHtml != null && !bodyHtml.isEmpty())) {
                msgs.add(new MessageDetail(id, sender, subject, date,
                    bodyText != null ? bodyText : "",
                    bodyHtml != null ? bodyHtml : "",
                    Collections.emptyList()));
            } else {
                msgs.add(new Message(id, sender, subject, date));
            }
        }
        return msgs;
    }

    @Override
    public MessageDetail readMessage(String messageId) throws TempMailException {
        throw new TempMailException("Ncaori Mail+ returns full message in getInbox(). Use getInbox() then filter by id.");
    }

    @Override
    public boolean deleteEmail(String email) throws TempMailException {
        return true;
    }

    private static String firstOf(String first, String fallback) {
        return first != null && !first.isEmpty() ? first : fallback;
    }

    private static LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(dateStr, DATE_FMT);
        } catch (Exception e) {
            return LocalDateTime.now();
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
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
