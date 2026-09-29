package com.ashenox.starter.auth.challenge.port;

import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import java.time.Instant;

/** Synchronous mail transport. Successful return means SMTP acceptance, not inbox delivery. */
public interface MfaEmailSender {
    void sendCode(String recipient, ChallengePurpose purpose, String code, Instant expiresAt);
    void sendSettingsChanged(String recipient, boolean enabled);
}
