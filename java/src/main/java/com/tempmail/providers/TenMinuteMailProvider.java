package com.tempmail.providers;

import com.tempmail.TempMailProvider;
import com.tempmail.errors.NotFoundException;
import com.tempmail.errors.TempMailException;
import com.tempmail.http.HttpClient;
import com.tempmail.models.Message;
import com.tempmail.models.MessageDetail;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 10minutemail.net provider - HTML scraping public mailbox.
 */
public class TenMinuteMailProvider implements TempMailProvider {

    private static final String BASE_URL = "https://10minutemail.net";

    private final HttpClient client;
    private String email;

    public TenMinuteMailProvider() {
        this.client = new HttpClient(true, true);
    }

    private static String decodeCfEmail(String hex) {
        if (hex == null || hex.length() < 4) return "";
        try {
            int k = Integer.parseInt(hex.substring(0, 2), 16);
            StringBuilder sb = new StringBuilder();
            for (int i = 2; i < hex.length(); i += 2) {
                int val = Integer.parseInt(hex.substring(i, i + 2), 16);
                sb.append((char) (val ^ k));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", "").trim();
    }

    @Override
    public String generateEmail() throws TempMailException {
        String html = client.get(BASE_URL + "/");
        Matcher m = Pattern.compile("id=\"fe_text\"[^>]*value=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(html);
        if (!m.find()) {
            throw new TempMailException("10minutemail: no address in response");
        }
        email = m.group(1).trim();
        return email;
    }

    @Override
    public List<Message> getInbox(String email) throws TempMailException {
        String html = client.get(BASE_URL + "/mailbox.ajax.php");
        
        Matcher rowMatcher = Pattern.compile("<tr[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        List<String> rows = new ArrayList<>();
        while (rowMatcher.find()) {
            rows.add(rowMatcher.group(1));
        }

        if (rows.size() <= 1) {
            return Collections.emptyList();
        }

        List<Message> messages = new ArrayList<>();
        // Skip header row
        for (int i = 1; i < rows.size(); i++) {
            String rowHtml = rows.get(i);
            Matcher cellMatcher = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(rowHtml);
            List<String> cells = new ArrayList<>();
            while (cellMatcher.find()) {
                cells.add(cellMatcher.group(1));
            }
            if (cells.size() < 3) continue;

            // Sender
            String sender = "";
            Matcher cfMatcher = Pattern.compile("data-cfemail=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(cells.get(0));
            if (cfMatcher.find()) {
                sender = decodeCfEmail(cfMatcher.group(1));
            } else {
                sender = stripHtml(cells.get(0));
            }

            String subject = stripHtml(cells.get(1));

            // Date
            String dateStr = "";
            Matcher titleMatcher = Pattern.compile("title=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(cells.get(2));
            if (titleMatcher.find()) {
                dateStr = titleMatcher.group(1);
            } else {
                dateStr = stripHtml(cells.get(2));
            }

            if (!dateStr.toLowerCase().contains("utc")) {
                dateStr += " UTC";
            }

            // Message ID
            Matcher midMatcher = Pattern.compile("mid=([^'&\"\\s>]+)", Pattern.CASE_INSENSITIVE).matcher(rowHtml);
            if (!midMatcher.find()) continue;
            String id = midMatcher.group(1);

            messages.add(new Message(
                    id,
                    sender,
                    subject,
                    parseDate(dateStr)
            ));
        }
        return messages;
    }

    @Override
    public MessageDetail readMessage(String messageId) throws TempMailException {
        // Parse composite messageId if it has a colon
        String mid = messageId.contains(":") ? messageId.split(":")[0] : messageId;

        String html = client.get(BASE_URL + "/readmail.html?mid=" + mid);

        Matcher bodyMatcher = Pattern.compile("class=\"mailinhtml\"[^>]*>(.*?)<div[^>]*style=\"clear:both;\"", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (!bodyMatcher.find()) {
            throw new NotFoundException("10minutemail: message " + messageId + " not found");
        }

        String bodyHtml = bodyMatcher.group(1).trim();

        // Decode CF email obfuscation in body html
        Matcher cfLinkMatcher = Pattern.compile("<(?:a|span)[^>]*class=\"__cf_email__\"[^>]*data-cfemail=\"([^\"]+)\"[^>]*>.*?</(?:a|span)>", Pattern.CASE_INSENSITIVE).matcher(bodyHtml);
        StringBuffer sb = new StringBuffer();
        while (cfLinkMatcher.find()) {
            cfLinkMatcher.appendReplacement(sb, Matcher.quoteReplacement(decodeCfEmail(cfLinkMatcher.group(1))));
        }
        cfLinkMatcher.appendTail(sb);
        bodyHtml = sb.toString();

        Matcher cfProtMatcher = Pattern.compile("href=\"/cdn-cgi/l/email-protection#([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(bodyHtml);
        sb = new StringBuffer();
        while (cfProtMatcher.find()) {
            cfProtMatcher.appendReplacement(sb, Matcher.quoteReplacement("href=\"mailto:" + decodeCfEmail(cfProtMatcher.group(1)) + "\""));
        }
        cfProtMatcher.appendTail(sb);
        bodyHtml = sb.toString();

        String bodyText = stripHtml(bodyHtml);

        // Subject
        String subject = "";
        Matcher subMatcher = Pattern.compile("<div class=\"mail_header\">.*?<h2[^>]*>(.*?)</h2>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (subMatcher.find()) {
            subject = stripHtml(subMatcher.group(1));
        }

        // Sender
        String sender = "";
        Matcher fromMatcher = Pattern.compile("<span class=\"mail_from\">(.*?)</span>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (fromMatcher.find()) {
            String fromHtml = fromMatcher.group(1);
            Matcher cfFromMatcher = Pattern.compile("data-cfemail=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE).matcher(fromHtml);
            if (cfFromMatcher.find()) {
                sender = decodeCfEmail(cfFromMatcher.group(1));
            } else {
                sender = stripHtml(fromHtml);
            }
        }

        return new MessageDetail(
                mid,
                sender,
                subject,
                LocalDateTime.now(),
                bodyText,
                bodyHtml,
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

    private LocalDateTime parseDate(String s) {
        if (s == null || s.isEmpty()) return LocalDateTime.now();
        try {
            // e.g. 2026-07-02 15:45:54 UTC
            String clean = s.replaceAll("(?i)\\s*UTC\\s*$", "").trim();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(clean, formatter);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
