package com.ashenox.starter.auth.challenge.event;

/** Publish inside the transaction that changes MFA settings; recipient must come from the account. */
public record MfaSettingsChanged(long userId, String recipient, boolean enabled) {
    @Override
    public String toString() {
        return "MfaSettingsChanged[redacted]";
    }
}
