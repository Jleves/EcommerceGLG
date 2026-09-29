package com.ashenox.starter.auth.service;

import com.ashenox.starter.auth.challenge.service.ChallengeCreationResult;

/** Internal first-step result; never serialize credentials to HTTP. */
public record LoginAttempt(IssuedAuthentication authentication, ChallengeCreationResult challenge, String email) {
    @Override public String toString() { return "LoginAttempt[redacted]"; }
}
