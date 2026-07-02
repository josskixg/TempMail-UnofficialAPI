package com.tempmail;

import com.tempmail.providers.TenMinuteMailProvider;
import com.tempmail.models.Message;
import com.tempmail.models.MessageDetail;
import java.util.List;

public class Test10Min {
    public static void main(String[] args) throws Exception {
        TenMinuteMailProvider provider = new TenMinuteMailProvider();
        System.out.println("Generating email...");
        String email = provider.generateEmail();
        System.out.println("Generated email: " + email);

        System.out.println("Fetching inbox...");
        List<Message> inbox = provider.getInbox(email);
        System.out.println("Inbox size: " + inbox.size());
        for (Message m : inbox) {
            System.out.println("Message ID: " + m.getId() + ", Sender: " + m.getSender() + ", Subject: " + m.getSubject());
            System.out.println("Reading message...");
            MessageDetail detail = provider.readMessage(m.getId());
            System.out.println("Read message subject: " + detail.getSubject());
            System.out.println("Read message bodyText: " + detail.getBodyText());
        }
        System.out.println("Done!");
    }
}
