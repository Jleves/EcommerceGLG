package com.ashenox.starter.auth.challenge.service;

import com.ashenox.starter.auth.challenge.model.AuthChallenge;

public record ChallengeCreationResult(AuthChallenge challenge, String challengeSecret, String code) {
    @Override
    public String toString() {
        return "ChallengeCreationResult[redacted]";
    }

    public String cookieValue() {
        return challenge.getId() + "." + challengeSecret;
    }
}
