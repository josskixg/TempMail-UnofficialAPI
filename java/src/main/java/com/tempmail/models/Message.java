package com.tempmail.models;

import java.time.LocalDateTime;

public class Message {

    private final String id;
    private final String sender;
    private final String subject;
    private final LocalDateTime date;
    private final String preview;
    private final boolean hasAttachments;

    public Message(String id, String sender, String subject, LocalDateTime date) {
        this(id, sender, subject, date, "", false);
    }

    public Message(String id, String sender, String subject, LocalDateTime date,
                   String preview, boolean hasAttachments) {
        this.id = id;
        this.sender = sender;
        this.subject = subject;
        this.date = date;
        this.preview = preview != null ? preview : "";
        this.hasAttachments = hasAttachments;
    }

    public String getId() { return id; }
    public String getSender() { return sender; }
    public String getSubject() { return subject; }
    public LocalDateTime getDate() { return date; }
    public String getPreview() { return preview; }
    public boolean hasAttachments() { return hasAttachments; }

    @Override
    public String toString() {
        return "Message{id='" + id + "', sender='" + sender + "', subject='" + subject + "', date=" + date + "}";
    }
}
