package com.tempmail.models;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MessageDetail extends Message {

    private final String bodyText;
    private final String bodyHtml;
    private final List<Map<String, Object>> attachments;

    public MessageDetail(String id, String sender, String subject, LocalDateTime date,
                         String bodyText, String bodyHtml, List<Map<String, Object>> attachments) {
        super(id, sender, subject, date);
        this.bodyText = bodyText;
        this.bodyHtml = bodyHtml;
        this.attachments = attachments != null ? attachments : Collections.emptyList();
    }

    public String getBodyText() {
        return bodyText;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public List<Map<String, Object>> getAttachments() {
        return attachments;
    }

    @Override
    public String toString() {
        return "MessageDetail{id='" + getId() + "', sender='" + getSender()
                + "', subject='" + getSubject() + "', date=" + getDate()
                + ", attachments=" + attachments.size() + "}";
    }
}
