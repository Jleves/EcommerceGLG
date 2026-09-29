package com.ashenox.starter.auth.challenge.service;

import com.ashenox.starter.auth.challenge.model.AuthChallenge;
import com.ashenox.starter.auth.challenge.model.AuthRateLimitScope;
import com.ashenox.starter.auth.challenge.model.ChallengeDeliveryState;
import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.auth.challenge.repository.AuthChallengeRepository;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChallengeService {
    private final UserRepository users;
    private final AuthChallengeRepository challenges;
    private final RateLimitService limits;
    private final ChallengeFactory factory;
    private final ChallengeCrypto crypto;
    private final AppProperties properties;
    private final EntityManager entityManager;
    private final JdbcTemplate jdbc;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY,
            noRollbackFor = ChallengeException.class)
    public void requireLoginUser(String cookieValue) {
        LocatedChallenge located = locate(cookieValue);
        User user = users.lockById(located.userId()).orElseThrow(ChallengeException::invalid);
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (!user.isEnabled() || !user.isEmailMfaEnabled()) throw ChallengeException.invalid();
    }

    @Transactional(noRollbackFor = ChallengeException.class)
    public ChallengeResendResult resendLogin(String cookieValue) {
        requireLoginUser(cookieValue);
        return resend(cookieValue, ChallengePurpose.LOGIN, null, null);
    }

    @Transactional(noRollbackFor = ChallengeException.class)
    public ChallengeCreationResult start(long userId, ChallengePurpose purpose, String originSessionId) {
        User user = users.lockById(userId).orElseThrow(ChallengeException::invalid);
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (!user.isEnabled()) throw ChallengeException.invalid();
        Instant now = Instant.now();
        var config = properties.getSecurity().getMfa();
        var issues = limits.lock(AuthRateLimitScope.MFA_CODE_ISSUE, Long.toString(userId),
                config.getAggregateWindow(), now);
        if (issues.exhausted(config.getMaxCodeIssuesPerWindow())) {
            throw ChallengeException.limited(issues.retryAfterSeconds());
        }
        for (AuthChallenge previous : challenges.lockUnconsumedByUserAndPurpose(userId, purpose)) {
            previous.setInvalidatedAt(now);
        }
        ChallengeCreationResult result = factory.create(user, purpose, user.getSecurityVersion(), originSessionId);
        challenges.saveAndFlush(result.challenge());
        issues.increment();
        return result;
    }

    @Transactional(noRollbackFor = ChallengeException.class)
    public ChallengeResendResult resend(String cookieValue, ChallengePurpose expectedPurpose,
                                        Long expectedUserId, String expectedOriginSessionId) {
        LocatedChallenge located = locate(cookieValue);
        if (expectedUserId != null && !expectedUserId.equals(located.userId())) throw ChallengeException.invalid();
        User user = users.lockById(located.userId()).orElseThrow(ChallengeException::invalid);
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        Instant now = Instant.now();
        var config = properties.getSecurity().getMfa();
        var issues = limits.lock(AuthRateLimitScope.MFA_CODE_ISSUE, Long.toString(user.getId()),
                config.getAggregateWindow(), now);
        AuthChallenge challenge = challenges.lockById(located.id()).orElseThrow(ChallengeException::invalid);
        entityManager.refresh(challenge, LockModeType.PESSIMISTIC_WRITE);
        validate(challenge, located.secret(), expectedPurpose, expectedUserId, expectedOriginSessionId, user, now);
        if (challenge.getFailedAttempts() >= config.getMaxFailedAttempts()
                || challenge.getResendCount() >= config.getMaxResends()) {
            throw ChallengeException.limited(secondsUntil(challenge.getExpiresAt(), now));
        }
        if (issues.exhausted(config.getMaxCodeIssuesPerWindow())) {
            throw ChallengeException.limited(issues.retryAfterSeconds());
        }
        Instant available = challenge.getLastSentAt().plus(config.getResendCooldown());
        if (now.isBefore(available)) {
            throw ChallengeException.limited(secondsUntil(available, now));
        }
        int nextGeneration = challenge.getCodeGeneration() + 1;
        String code = crypto.newCode();
        int updated = jdbc.update("""
                UPDATE auth_challenges
                SET code_generation = ?, code_hmac = ?, delivery_state = 'PENDING',
                    resend_count = resend_count + 1, last_sent_at = ?, version = version + 1
                WHERE id = ? AND code_generation = ? AND consumed_at IS NULL
                  AND invalidated_at IS NULL AND expires_at > ? AND security_version = ?
                  AND failed_attempts < ? AND resend_count < ?
                """, nextGeneration, crypto.codeHmac(challenge.getId(), challenge.getPurpose(), nextGeneration, code),
                LocalDateTime.ofInstant(now, ZoneOffset.UTC), challenge.getId(), challenge.getCodeGeneration(),
                LocalDateTime.ofInstant(now, ZoneOffset.UTC), user.getSecurityVersion(), config.getMaxFailedAttempts(),
                config.getMaxResends());
        if (updated != 1) throw ChallengeException.invalid();
        entityManager.refresh(challenge, LockModeType.PESSIMISTIC_WRITE);
        issues.increment();
        return new ChallengeResendResult(challenge.getId(), nextGeneration, code,
                challenge.getExpiresAt(), now.plus(config.getResendCooldown()));
    }

    // Callers that wrap this method in another transaction must also preserve expected rejection counters.
    @Transactional(noRollbackFor = ChallengeException.class)
    public AuthChallenge verifyAndConsume(String cookieValue, ChallengePurpose expectedPurpose,
                                          Long expectedUserId, String expectedOriginSessionId, String code) {
        LocatedChallenge located = locate(cookieValue);
        if (expectedUserId != null && !expectedUserId.equals(located.userId())) throw ChallengeException.invalid();
        User user = users.lockById(located.userId()).orElseThrow(ChallengeException::invalid);
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        Instant now = Instant.now();
        var config = properties.getSecurity().getMfa();
        var failures = limits.lock(AuthRateLimitScope.MFA_CODE_FAILURE, Long.toString(user.getId()),
                config.getAggregateWindow(), now);
        AuthChallenge challenge = challenges.lockById(located.id()).orElseThrow(ChallengeException::invalid);
        entityManager.refresh(challenge, LockModeType.PESSIMISTIC_WRITE);
        validate(challenge, located.secret(), expectedPurpose, expectedUserId, expectedOriginSessionId, user, now);
        if (failures.exhausted(config.getMaxCodeFailuresPerWindow())) {
            throw ChallengeException.limited(failures.retryAfterSeconds());
        }
        if (challenge.getFailedAttempts() >= config.getMaxFailedAttempts()) {
            throw ChallengeException.limited(secondsUntil(challenge.getExpiresAt(), now));
        }
        if (challenge.getDeliveryState() != ChallengeDeliveryState.SENT) {
            throw ChallengeException.invalid();
        }
        if (!crypto.matchesCode(challenge.getId(), challenge.getPurpose(), challenge.getCodeGeneration(),
                code, challenge.getCodeHmac())) {
            int updated = jdbc.update("""
                    UPDATE auth_challenges
                    SET failed_attempts = failed_attempts + 1, version = version + 1
                    WHERE id = ? AND code_generation = ? AND delivery_state = 'SENT'
                      AND consumed_at IS NULL AND invalidated_at IS NULL AND expires_at > ?
                      AND security_version = ? AND failed_attempts < ?
                    """, challenge.getId(), challenge.getCodeGeneration(), LocalDateTime.ofInstant(now, ZoneOffset.UTC),
                    user.getSecurityVersion(), config.getMaxFailedAttempts());
            if (updated != 1) throw ChallengeException.invalid();
            entityManager.refresh(challenge, LockModeType.PESSIMISTIC_WRITE);
            failures.increment();
            throw ChallengeException.invalidCode();
        }
        int updated = jdbc.update("""
                UPDATE auth_challenges
                SET consumed_at = ?, version = version + 1
                WHERE id = ? AND code_generation = ? AND delivery_state = 'SENT'
                  AND consumed_at IS NULL AND invalidated_at IS NULL AND expires_at > ?
                  AND security_version = ? AND failed_attempts < ?
                """, LocalDateTime.ofInstant(now, ZoneOffset.UTC), challenge.getId(), challenge.getCodeGeneration(),
                LocalDateTime.ofInstant(now, ZoneOffset.UTC), user.getSecurityVersion(), config.getMaxFailedAttempts());
        if (updated != 1) throw ChallengeException.invalid();
        entityManager.refresh(challenge, LockModeType.PESSIMISTIC_WRITE);
        return challenge;
    }

    @Transactional
    public boolean markDelivery(String challengeId, int generation, ChallengeDeliveryState result) {
        if (result == ChallengeDeliveryState.PENDING) {
            throw new IllegalArgumentException("Resultado de entrega inválido");
        }
        Long userId = challenges.findUserIdByChallengeId(challengeId).orElse(null);
        if (userId == null) return false;
        User user = users.lockById(userId).orElse(null);
        if (user == null) return false;
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        AuthChallenge challenge = challenges.lockById(challengeId).orElse(null);
        if (challenge != null) entityManager.refresh(challenge, LockModeType.PESSIMISTIC_WRITE);
        Instant now = Instant.now();
        if (challenge == null || !usable(challenge, user, now)) return false;
        int updated = jdbc.update("""
                UPDATE auth_challenges SET delivery_state = ?, version = version + 1
                WHERE id = ? AND code_generation = ? AND delivery_state = 'PENDING'
                  AND consumed_at IS NULL AND invalidated_at IS NULL AND expires_at > ?
                  AND security_version = ?
                """, result.name(), challengeId, generation, LocalDateTime.ofInstant(now, ZoneOffset.UTC), user.getSecurityVersion());
        if (updated == 1) entityManager.refresh(challenge, LockModeType.PESSIMISTIC_WRITE);
        return updated == 1;
    }

    @Transactional
    public void invalidateAll(long userId) {
        User user = users.lockById(userId).orElseThrow(ChallengeException::invalid);
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        Instant now = Instant.now();
        for (AuthChallenge challenge : challenges.lockUnconsumedByUser(userId)) {
            challenge.setInvalidatedAt(now);
        }
    }

    @Transactional
    public void cancel(String cookieValue) {
        LocatedChallenge located;
        try {
            located = locate(cookieValue);
        } catch (ChallengeException exception) {
            return;
        }
        User user = users.lockById(located.userId()).orElse(null);
        if (user == null) return;
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        AuthChallenge challenge = challenges.lockById(located.id()).orElse(null);
        if (challenge != null) entityManager.refresh(challenge, LockModeType.PESSIMISTIC_WRITE);
        if (challenge != null && crypto.matchesSecret(located.secret(), challenge.getSecretHash())
                && challenge.getConsumedAt() == null && challenge.getInvalidatedAt() == null) {
            jdbc.update("""
                    UPDATE auth_challenges SET invalidated_at = ?, version = version + 1
                    WHERE id = ? AND consumed_at IS NULL AND invalidated_at IS NULL
                    """, LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC), challenge.getId());
            entityManager.refresh(challenge, LockModeType.PESSIMISTIC_WRITE);
        }
    }

    private LocatedChallenge locate(String cookieValue) {
        if (cookieValue == null) throw ChallengeException.invalid();
        int separator = cookieValue.indexOf('.');
        if (separator <= 0 || separator == cookieValue.length() - 1) throw ChallengeException.invalid();
        String id = cookieValue.substring(0, separator);
        String secret = cookieValue.substring(separator + 1);
        if (id.length() != 36 || !secret.matches("[A-Za-z0-9_-]{43}")) {
            throw ChallengeException.invalid();
        }
        try {
            if (!UUID.fromString(id).toString().equals(id)) throw ChallengeException.invalid();
        } catch (IllegalArgumentException exception) {
            throw ChallengeException.invalid();
        }
        Long userId = challenges.findUserIdByChallengeId(id).orElseThrow(ChallengeException::invalid);
        return new LocatedChallenge(id, secret, userId);
    }

    private void validate(AuthChallenge challenge, String secret, ChallengePurpose purpose,
                          Long expectedUserId, String originSessionId, User user, Instant now) {
        if (purpose == null || challenge.getPurpose() != purpose
                || expectedUserId != null && !expectedUserId.equals(user.getId())
                || purpose == ChallengePurpose.LOGIN && originSessionId != null
                || purpose != ChallengePurpose.LOGIN && (expectedUserId == null || originSessionId == null
                    || !originSessionId.equals(challenge.getOriginSessionId()))
                || !crypto.matchesSecret(secret, challenge.getSecretHash())
                || !usable(challenge, user, now)) {
            throw ChallengeException.invalid();
        }
    }

    private boolean usable(AuthChallenge challenge, User user, Instant now) {
        return user.isEnabled() && challenge.getSecurityVersion() == user.getSecurityVersion()
                && challenge.getConsumedAt() == null && challenge.getInvalidatedAt() == null
                && challenge.getExpiresAt().isAfter(now);
    }

    private long secondsUntil(Instant until, Instant now) {
        return Math.max(1, Duration.between(now, until).toSeconds());
    }

    private record LocatedChallenge(String id, String secret, Long userId) {}
}
