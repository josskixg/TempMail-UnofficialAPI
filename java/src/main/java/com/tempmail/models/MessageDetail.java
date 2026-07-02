package com.tempmail.models;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageDetail extends Message {

    private final String bodyText;
    private final String bodyHtml;
    private final String bodyPreview;
    private final String contentType;
    private final String raw;
    private final Map<String, String> headers;
    private final List<String> cc;
    private final String replyTo;
    private final String messageId;
    private final int size;
    private final boolean isHtml;
    private final List<Map<String, Object>> attachments;

    public MessageDetail(String id, String sender, String subject, LocalDateTime date,
                         String bodyText, String bodyHtml, List<Map<String, Object>> attachments) {
        this(id, sender, subject, date, bodyText, bodyHtml, attachments,
                "", "", "", Collections.emptyMap(), Collections.emptyList(), "", "", 0);
    }

    public MessageDetail(String id, String sender, String subject, LocalDateTime date,
                         String bodyText, String bodyHtml, List<Map<String, Object>> attachments,
                         String bodyPreview, String contentType, String raw,
                         Map<String, String> headers, List<String> cc,
                         String replyTo, String messageId, int size) {
        super(id, sender, subject, date, "", !attachments.isEmpty());
        this.bodyHtml = bodyHtml != null ? bodyHtml : "";
        this.attachments = attachments != null ? attachments : Collections.emptyList();
        this.raw = raw != null ? raw : "";
        this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        this.cc = cc != null ? cc : Collections.emptyList();
        this.replyTo = replyTo != null ? replyTo : "";
        this.messageId = messageId != null ? messageId : "";
        this.size = size;

        // Normalize
        boolean hasHtml = !this.bodyHtml.trim().isEmpty();
        String resolvedText = (bodyText != null && !bodyText.trim().isEmpty())
                ? bodyText
                : (hasHtml ? stripHtml(this.bodyHtml) : "");
        this.bodyText = resolvedText;
        this.isHtml = hasHtml;

        if (hasHtml && !resolvedText.isEmpty()) {
            this.contentType = "multipart/alternative";
        } else if (hasHtml) {
            this.contentType = "text/html";
        } else {
            this.contentType = contentType != null && !contentType.isEmpty() ? contentType : "text/plain";
        }

        this.bodyPreview = (bodyPreview != null && !bodyPreview.isEmpty())
                ? bodyPreview
                : resolvedText.substring(0, Math.min(200, resolvedText.length())).trim();

        if (!this.messageId.isEmpty() && !this.headers.containsKey("Message-ID")) {
            this.headers.put("Message-ID", this.messageId);
        }
    }

    /** Strip HTML tags to plain text. */
    public static String stripHtml(String html) {
        if (html == null || html.isEmpty()) return "";
        String s = html.replaceAll("(?is)<(style|script)[^>]*>.*?</\\1>", "");
        s = s.replaceAll("(?i)<(br\\s*/?|/p|/div|/tr|/li|/h\\d)>", "\n");
        s = s.replaceAll("<[^>]+>", "");
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
              .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    public String getBodyText() { return bodyText; }
    public String getBodyHtml() { return bodyHtml; }
    public String getBodyPreview() { return bodyPreview; }
    public String getContentType() { return contentType; }
    public String getRaw() { return raw; }
    public Map<String, String> getHeaders() { return Collections.unmodifiableMap(headers); }
    public List<String> getCc() { return cc; }
    public String getReplyTo() { return replyTo; }
    public String getMessageId() { return messageId; }
    public int getSize() { return size; }
    public boolean isHtml() { return isHtml; }
    public List<Map<String, Object>> getAttachments() { return attachments; }

    @Override
    public String toString() {
        return "MessageDetail{id='" + getId() + "', subject='" + getSubject()
                + "', contentType='" + contentType + "', isHtml=" + isHtml
                + ", attachments=" + attachments.size() + "}";
    }
}
