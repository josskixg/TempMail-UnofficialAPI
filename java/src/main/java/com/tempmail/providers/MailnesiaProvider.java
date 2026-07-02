package com.tempmail.providers;

import com.tempmail.TempMailProvider;
import com.tempmail.errors.NotFoundException;
import com.tempmail.errors.TempMailException;
import com.tempmail.http.HttpClient;
import com.tempmail.models.Message;
import com.tempmail.models.MessageDetail;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * mailnesia.com provider - public mailbox, no auth, HTML scraping.
 * Inbox parsed from table rows; message body from &lt;div id="message"&gt;.
 */
public class MailnesiaProvider implements TempMailProvider {

    private static final String BASE_URL = "https://mailnesia.com";
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private static final Pattern ROW_PATTERN = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern CELL_PATTERN = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);
    private static final Pattern LINK_PATTERN = Pattern.compile("<a[^>]*href=\"([^\"]*)\"");

    private final HttpClient client;
    private final Random random = new Random();
    private String username;

    public MailnesiaProvider() {
        this.client = new HttpClient(true, true);
    }

    @Override
    public String generateEmail() throws TempMailException {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        username = sb.toString();
        return username + "@mailnesia.com";
    }

    @Override
    public List<Message> getInbox(String email) throws TempMailException {
        String user = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        Map<String, String> headers = getHeadersWithIPRotation();
        String html = client.get(BASE_URL + "/mailbox/" + user, headers);
        List<Message> messages = new ArrayList<>();
        Matcher rm = ROW_PATTERN.matcher(html);
        while (rm.find()) {
            String row = rm.group(1);
            List<String> cells = new ArrayList<>();
            Matcher cm = CELL_PATTERN.matcher(row);
            while (cm.find()) cells.add(stripTags(cm.group(1)));
            if (cells.size() < 3) continue;
            String sender = cells.get(0);
            String subject = cells.get(1);
            String time = cells.get(2);
            if (sender.isEmpty() && subject.isEmpty()) continue;
            String msgId = "";
            Matcher lm = LINK_PATTERN.matcher(row);
            if (lm.find()) msgId = lastSegment(lm.group(1));
            messages.add(new Message(msgId, sender, subject, parseDate(time)));
        }
        return messages;
    }

    @Override
    public MessageDetail readMessage(String messageId) throws TempMailException {
        if (username == null) throw new TempMailException("mailnesia: no email generated - call generateEmail() first");
        Map<String, String> headers = getHeadersWithIPRotation();
        String html = client.get(BASE_URL + "/mailbox/" + username + "/" + messageId, headers);
        int idx = html.indexOf("id=\"message\"");
        String bodyText;
        String bodyHtml;
        if (idx < 0) {
            bodyText = stripTags(html);
            bodyHtml = "";
        } else {
            int start = html.indexOf(">", idx) + 1;
            int end = html.length();
            for (String marker : new String[]{"</body>", "<footer", "<div id=\"footer"}) {
                int i = html.indexOf(marker, start);
                if (i >= 0 && i < end) end = i;
            }
            String inner = html.substring(start, end);
            bodyText = stripTags(inner);
            bodyHtml = inner;
        }
        return new MessageDetail(
                messageId,
                "",
                "",
                LocalDateTime.now(),
                bodyText,
                bodyHtml,
                Collections.emptyList()
        );
    }

    @Override
    public boolean deleteEmail(String email) throws TempMailException {
        this.username = null;
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

    private String lastSegment(String href) {
        if (href == null || href.isEmpty() || !href.contains("/")) return "";
        String s = href.endsWith("/") ? href.substring(0, href.length() - 1) : href;
        int i = s.lastIndexOf('/');
        return i >= 0 ? s.substring(i + 1) : s;
    }

    private String stripTags(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private LocalDateTime parseDate(String s) {
        if (s == null || s.isEmpty()) return LocalDateTime.now();
        try {
            return LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private String generateRandomIP() {
        return String.format("%d.%d.%d.%d",
            random.nextInt(254) + 1,
            random.nextInt(256),
            random.nextInt(256),
            random.nextInt(254) + 1);
    }

    private Map<String, String> getHeadersWithIPRotation() {
        String ip = generateRandomIP();
        Map<String, String> headers = new java.util.HashMap<>();
        headers.put("X-Forwarded-For", ip);
        headers.put("X-Real-IP", ip);
        headers.put("CF-Connecting-IP", ip);
        headers.put("True-Client-IP", ip);
        return headers;
    }
}
