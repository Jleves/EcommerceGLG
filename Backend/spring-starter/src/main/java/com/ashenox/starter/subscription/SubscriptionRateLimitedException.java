package com.ashenox.starter.subscription;

public class SubscriptionRateLimitedException extends RuntimeException {
    private final long retryAfterSeconds;

    public SubscriptionRateLimitedException(long retryAfterSeconds) {
        super("Demasiados intentos. Volvé a intentar más tarde.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
