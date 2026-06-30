package com.tempmail.errors;

public class NotFoundException extends TempMailException {

    public NotFoundException(String message) {
        super(message, 404);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, 404, cause);
    }
}
