package com.ashenox.starter.auth.challenge.service;

import com.ashenox.starter.auth.challenge.model.AuthChallenge;
import com.ashenox.starter.auth.challenge.model.ChallengeDeliveryState;
import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChallengeFactory {
    private final ChallengeCrypto crypto;
    private final AppProperties properties;

    public ChallengeCreationResult create(User user, ChallengePurpose purpose,
                                          long securityVersion, String originSessionId) {
        if (user == null || user.getId() == null || purpose == null) {
            throw new IllegalArgumentException("Usuario y propósito son obligatorios");
        }
        if ((purpose == ChallengePurpose.LOGIN && originSessionId != null)
                || (purpose != ChallengePurpose.LOGIN && (originSessionId == null || originSessionId.isBlank()))) {
            throw new IllegalArgumentException("Sesión de origen incompatible con el propósito");
        }

        Instant now = Instant.now();
        String id = UUID.randomUUID().toString();
        String secret = crypto.newSecret();
        String code = crypto.newCode();
        AuthChallenge challenge = new AuthChallenge();
        challenge.setId(id);
        challenge.setUser(user);
        challenge.setPurpose(purpose);
        challenge.setSecurityVersion(securityVersion);
        challenge.setOriginSessionId(originSessionId);
        challenge.setSecretHash(crypto.hashSecret(secret));
        challenge.setCodeGeneration(1);
        challenge.setCodeHmac(crypto.codeHmac(id, purpose, 1, code));
        challenge.setDeliveryState(ChallengeDeliveryState.PENDING);
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plus(properties.getSecurity().getMfa().getChallengeTtl()));
        challenge.setFailedAttempts(0);
        challenge.setResendCount(0);
        challenge.setLastSentAt(now);
        return new ChallengeCreationResult(challenge, secret, code);
    }
}
