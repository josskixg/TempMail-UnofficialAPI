package com.tempmail;

import com.tempmail.errors.TempMailException;
import com.tempmail.models.Message;
import com.tempmail.models.MessageDetail;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface TempMailProvider {

    String generateEmail() throws TempMailException;

    List<Message> getInbox(String email) throws TempMailException;

    MessageDetail readMessage(String messageId) throws TempMailException;

    boolean deleteEmail(String email) throws TempMailException;

    Optional<Message> waitForEmail(String email, Duration timeout, Duration interval) throws TempMailException;
}
