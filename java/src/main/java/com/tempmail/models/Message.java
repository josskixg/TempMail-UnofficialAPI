package com.tempmail.models;

import java.time.LocalDateTime;

public class Message {

    private final String id;
    private final String sender;
    private final String subject;
    private final LocalDateTime date;

    public Message(String id, String sender, String subject, LocalDateTime date) {
        this.id = id;
        this.sender = sender;
        this.subject = subject;
        this.date = date;
    }

    public String getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public String getSubject() {
        return subject;
    }

    public LocalDateTime getDate() {
        return date;
    }

    @Override
    public String toString() {
        return "Message{id='" + id + "', sender='" + sender + "', subject='" + subject + "', date=" + date + "}";
    }
}
