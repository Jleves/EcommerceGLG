package com.ashenox.starter.auth.challenge.service;

import com.ashenox.starter.shared.error.ApiErrorCode;
import lombok.Getter;

@Getter
public class ChallengeException extends RuntimeException {
    private final ApiErrorCode code;
    private final long retryAfterSeconds;

    public ChallengeException(ApiErrorCode code, String message) {
        this(code, message, 0);
    }

    public ChallengeException(ApiErrorCode code, String message, long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ChallengeException invalid() {
        return new ChallengeException(ApiErrorCode.MFA_CHALLENGE_INVALID, "El desafío es inválido o expiró.");
    }

    public static ChallengeException invalidCode() {
        return new ChallengeException(ApiErrorCode.MFA_CODE_INVALID, "El código es inválido.");
    }

    public static ChallengeException deliveryUnavailable() {
        return new ChallengeException(ApiErrorCode.MFA_DELIVERY_UNAVAILABLE,
                "No se pudo enviar el código. Volvé a intentar más tarde.");
    }

    public static ChallengeException limited(long retryAfterSeconds) {
        return new ChallengeException(ApiErrorCode.MFA_RATE_LIMITED,
                "Demasiados intentos. Volvé a intentar más tarde.", Math.max(1, retryAfterSeconds));
    }
}
