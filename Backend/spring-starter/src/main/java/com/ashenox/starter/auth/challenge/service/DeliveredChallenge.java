package com.ashenox.starter.auth.challenge.service;

import java.time.Instant;

/** Internal result; cookieValue is only for the HttpOnly cookie, never a response body. */
public record DeliveredChallenge(String challengeId, int generation, String cookieValue,
                                 Instant expiresAt, Instant resendAvailableAt) {
    @Override
    public String toString() {
        return "DeliveredChallenge[redacted]";
    }
}
