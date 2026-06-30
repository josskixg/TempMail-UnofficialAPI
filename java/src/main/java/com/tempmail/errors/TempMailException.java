package com.tempmail.errors;

public class TempMailException extends Exception {

    private final int statusCode;

    public TempMailException(String message) {
        super(message);
        this.statusCode = -1;
    }

    public TempMailException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public TempMailException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public TempMailException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
