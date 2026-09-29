package com.ashenox.starter.auth.challenge.service;

import java.time.Instant;

public record ChallengeResendResult(String challengeId, int generation, String code,
                                    Instant expiresAt, Instant resendAvailableAt) {
    @Override
    public String toString() {
        return "ChallengeResendResult[redacted]";
    }
}
