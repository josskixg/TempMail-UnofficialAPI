package com.tempmail.providers;

import com.tempmail.TempMailProvider;
import com.tempmail.errors.NotFoundException;
import com.tempmail.errors.TempMailException;
import com.tempmail.http.HttpClient;
import com.tempmail.models.Message;
import com.tempmail.models.MessageDetail;

import java.net.HttpCookie;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * email-temp.com provider - HTML scraping with surl cookie.
 * Same backend family as emailfake.com and generator.email.
 */
public class EmailTempProvider implements TempMailProvider {

    private static final String BASE_URL = "https://email-temp.com";
    private static final String COOKIE_DOMAIN = ".email-temp.com";
    private static final String CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern OPTION_PATTERN = Pattern.compile("<option[^>]*value=\"([^\"]+)\"");
    private static final Pattern DOMAIN_TEXT_PATTERN = Pattern.compile(">([a-z0-9-]+\\.[a-z.]{2,})<");
    private static final Pattern ANCHOR_PATTERN = Pattern.compile(
            "(<a\\b[^>]*?\\bclass=\"[^\"]*list-group-item[^\"]*\"[^>]*>)(.*?)</a>", Pattern.DOTALL);
    private static final Pattern HREF_PATTERN = Pattern.compile("href=\"([^\"]*)\"");
    private static final Pattern FROM_PATTERN = Pattern.compile(
            "<div[^>]*class=\"[^\"]*from[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL);
    private static final Pattern SUBJ_PATTERN = Pattern.compile(
            "<div[^>]*class=\"[^\"]*subj[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL);
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "<div[^>]*class=\"[^\"]*time[^\"]*\"[^>]*>(.*?)</div>", Pattern.DOTALL);

    private final HttpClient client;
    private final Random random = new Random();
    private String email;
    private String domain;
    private String username;

    public EmailTempProvider() {
        this.client = new HttpClient(true, true);
    }

    @Override
    public String generateEmail() throws TempMailException {
        List<String> domains = getDomains();
        domain = domains.get(random.nextInt(domains.size()));
        username = randUser();
        email = username + "@" + domain;
        setSurlCookie(domain, username);
        return email;
    }

    @Override
    public List<Message> getInbox(String email) throws TempMailException {
        if (domain == null || username == null) {
            throw new TempMailException("email-temp: no email generated - call generateEmail() first");
        }
        String html = client.get(BASE_URL + "/channel" + (random.nextInt(9) + 1) + "/");
        List<Message> messages = new ArrayList<>();
        Matcher am = ANCHOR_PATTERN.matcher(html);
        while (am.find()) {
            String opening = am.group(1);
            String inner = am.group(2);
            String href = group1(HREF_PATTERN, opening);
            String msgId = lastSegment(href);
            if (msgId.length() < 10) continue;
            String sender = stripTags(group1(FROM_PATTERN, inner));
            String subject = stripTags(group1(SUBJ_PATTERN, inner));
            String time = stripTags(group1(TIME_PATTERN, inner));
            messages.add(new Message(msgId, sender, subject, parseDate(time)));
        }
        return messages;
    }

    @Override
    public MessageDetail readMessage(String messageId) throws TempMailException {
        if (domain == null || username == null) {
            throw new TempMailException("email-temp: no email generated - call generateEmail() first");
        }
        String html = client.get(BASE_URL + "/" + domain + "/" + username + "/" + messageId);
        int idx = html.indexOf("id=\"message\"");
        if (idx < 0) throw new NotFoundException("email-temp: message " + messageId + " body not found");
        String inner = sliceBody(html, idx);
        String sender = stripTags(group1(FROM_PATTERN, html));
        String subject = stripTags(group1(SUBJ_PATTERN, html));
        String time = stripTags(group1(TIME_PATTERN, html));
        return new MessageDetail(
                messageId,
                sender,
                subject,
                parseDate(time),
                stripTags(inner),
                inner,
                Collections.emptyList()
        );
    }

    @Override
    public boolean deleteEmail(String email) throws TempMailException {
        this.email = null;
        this.domain = null;
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

    private List<String> getDomains() throws TempMailException {
        String html = client.get(BASE_URL + "/channel" + (random.nextInt(9) + 1) + "/");
        List<String> domains = new ArrayList<>();
        Matcher m = OPTION_PATTERN.matcher(html);
        while (m.find()) {
            String val = m.group(1).trim();
            if (val.contains(".") && !val.contains(" ") && !val.contains("@") && !val.contains("email-temp")) {
                domains.add(val);
            }
        }
        if (domains.isEmpty()) {
            Matcher dt = DOMAIN_TEXT_PATTERN.matcher(html);
            while (dt.find()) {
                String val = dt.group(1).trim();
                if (!val.contains("email-temp") && !val.contains("emailfake") && !val.contains("generator")) {
                    domains.add(val);
                }
            }
        }
        if (domains.isEmpty()) throw new TempMailException("email-temp: no domains found on page");
        return domains;
    }

    private void setSurlCookie(String domain, String username) {
        try {
            HttpCookie c = new HttpCookie("surl", domain + "/" + username);
            c.setDomain(COOKIE_DOMAIN);
            c.setPath("/");
            client.getCookieManager().getCookieStore().add(URI.create(BASE_URL), c);
        } catch (Exception ignore) {
        }
    }

    private String sliceBody(String html, int msgIdx) {
        int start = html.indexOf(">", msgIdx) + 1;
        int end = html.length();
        for (String marker : new String[]{"</body>", "<footer", "<div id=\"footer"}) {
            int i = html.indexOf(marker, start);
            if (i >= 0 && i < end) end = i;
        }
        return html.substring(start, end);
    }

    private String randUser() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(CHARS.charAt(random.nextInt(CHARS.length())));
        return sb.toString();
    }

    private String group1(Pattern p, String input) {
        Matcher m = p.matcher(input);
        return m.find() ? m.group(1) : "";
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
            return LocalDateTime.parse(s, DATE_FMT);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
