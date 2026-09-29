package com.ashenox.starter.auth.challenge.service;

import com.ashenox.starter.auth.challenge.model.ChallengeDeliveryState;
import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.auth.challenge.port.MfaEmailSender;
import com.ashenox.starter.auth.challenge.repository.AuthChallengeRepository;
import com.ashenox.starter.shared.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Must be called without an enclosing transaction: even suspending it could leave DB locks held.
 * Application services are responsible for password/session/MFA-state preconditions.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class ChallengeDeliveryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChallengeDeliveryService.class);
    private final ChallengeService challenges;
    private final AuthChallengeRepository repository;
    private final MfaEmailSender sender;
    private final AppProperties properties;

    public DeliveredChallenge start(long userId, ChallengePurpose purpose, String originSessionId) {
        var created = challenges.start(userId, purpose, originSessionId);
        return deliverCreated(created);
    }

    public DeliveredChallenge deliverCreated(ChallengeCreationResult created) {
        var challenge = created.challenge();
        var purpose = challenge.getPurpose();
        deliver(challenge.getId(), challenge.getCodeGeneration(), purpose, created.code(), challenge.getExpiresAt());
        return new DeliveredChallenge(challenge.getId(), challenge.getCodeGeneration(), created.cookieValue(),
                challenge.getExpiresAt(), challenge.getLastSentAt().plus(properties.getSecurity().getMfa().getResendCooldown()));
    }

    public DeliveredChallenge resend(String cookieValue, ChallengePurpose expectedPurpose,
                                     Long expectedUserId, String expectedOriginSessionId) {
        var resent = challenges.resend(cookieValue, expectedPurpose, expectedUserId, expectedOriginSessionId);
        return deliverResent(cookieValue, expectedPurpose, resent);
    }

    public DeliveredChallenge resendLogin(String cookieValue) {
        return deliverResent(cookieValue, ChallengePurpose.LOGIN, challenges.resendLogin(cookieValue));
    }

    public DeliveredChallenge deliverResent(String cookieValue, ChallengePurpose expectedPurpose,
                                             ChallengeResendResult resent) {
        deliver(resent.challengeId(), resent.generation(), expectedPurpose, resent.code(), resent.expiresAt());
        return new DeliveredChallenge(resent.challengeId(), resent.generation(), cookieValue,
                resent.expiresAt(), resent.resendAvailableAt());
    }

    private void deliver(String id, int generation, ChallengePurpose purpose, String code, java.time.Instant expiresAt) {
        String recipient = repository.findRecipientByChallengeId(id).orElseThrow(ChallengeException::invalid);
        try {
            sender.sendCode(recipient, purpose, code, expiresAt);
        } catch (RuntimeException exception) {
            // Never log the transport exception: it may contain recipient, message body or credentials.
            try {
                boolean recorded = challenges.markDelivery(id, generation, ChallengeDeliveryState.FAILED);
                LOGGER.warn("MFA delivery failed challengeId={} generation={} recorded={}", id, generation, recorded);
            } catch (RuntimeException recordingFailure) {
                // A DB outage leaves PENDING; only a controlled resend may recover it.
                LOGGER.error("MFA delivery failure could not be recorded challengeId={} generation={}", id, generation);
            }
            throw ChallengeException.deliveryUnavailable();
        }
        if (!challenges.markDelivery(id, generation, ChallengeDeliveryState.SENT)) {
            LOGGER.info("MFA delivery result obsolete challengeId={} generation={}", id, generation);
            throw ChallengeException.invalid();
        }
        LOGGER.info("MFA SMTP accepted challengeId={} generation={}", id, generation);
    }
}
