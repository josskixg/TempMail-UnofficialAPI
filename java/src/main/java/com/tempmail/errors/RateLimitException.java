package com.tempmail.errors;

public class RateLimitException extends TempMailException {

    private final int retryAfterSeconds;

    public RateLimitException(String message) {
        super(message, 429);
        this.retryAfterSeconds = -1;
    }

    public RateLimitException(String message, int retryAfterSeconds) {
        super(message, 429);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitException(String message, Throwable cause) {
        super(message, 429, cause);
        this.retryAfterSeconds = -1;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
