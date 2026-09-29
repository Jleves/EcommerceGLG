package com.ashenox.starter.auth.service;

import com.ashenox.starter.auth.challenge.event.MfaSettingsChanged;
import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.auth.challenge.service.ChallengeCreationResult;
import com.ashenox.starter.auth.challenge.service.ChallengeException;
import com.ashenox.starter.auth.challenge.service.ChallengeResendResult;
import com.ashenox.starter.auth.challenge.service.ChallengeService;
import com.ashenox.starter.auth.passwordchange.PasswordChangeException;
import com.ashenox.starter.auth.passwordchange.SensitiveOperationPasswordService;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.security.error.InvalidAuthSessionException;
import com.ashenox.starter.shared.error.ApiErrorCode;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = {ChallengeException.class, PasswordChangeException.class})
public class MfaSettingsService {
    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final AuthSessionService sessionService;
    private final SensitiveOperationPasswordService passwords;
    private final ChallengeService challenges;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher events;
    private final EmailMfaService emailMfa;

    public SettingsStatus status(long userId, String sessionId) {
        User user = requireSession(userId, sessionId);
        return new SettingsStatus(user.isEmailMfaEnabled(), user.getEmailMfaEnabledAt(), emailMfa.maskedEmail(user.getEmail()));
    }

    /** Returns committed PENDING. SMTP must be invoked by the caller after this transaction. */
    public ChallengeCreationResult start(long userId, String sessionId, ChallengePurpose purpose, String password) {
        User user = requireSession(userId, sessionId);
        requireState(user, purpose);
        passwords.verify(user, password);
        // start refreshes the managed user; preserve any expired password-window reset first.
        entityManager.flush();
        return challenges.start(userId, purpose, sessionId);
    }

    public PreparedResend resend(long userId, String sessionId, String cookie) {
        User user = requireSession(userId, sessionId);
        ChallengePurpose purpose = user.isEmailMfaEnabled() ? ChallengePurpose.DISABLE : ChallengePurpose.ENABLE;
        return new PreparedResend(purpose, challenges.resend(cookie, purpose, userId, sessionId));
    }

    public void confirm(long userId, String sessionId, ChallengePurpose purpose, String cookie, String code) {
        User user = requireSession(userId, sessionId);
        requireState(user, purpose);
        challenges.verifyAndConsume(cookie, purpose, userId, sessionId, code);
        // invalidateAll refreshes user, so perform it before changing the configuration.
        challenges.invalidateAll(userId);
        boolean enabled = purpose == ChallengePurpose.ENABLE;
        user.setEmailMfaEnabled(enabled);
        user.setEmailMfaEnabledAt(enabled ? Instant.now() : null);
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        sessionService.revokeAllForUser(userId); // flushes user/challenges before bulk update
        events.publishEvent(new MfaSettingsChanged(userId, user.getEmail(), enabled));
    }

    private User requireSession(long userId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) throw new InvalidAuthSessionException();
        User user = users.lockById(userId).orElseThrow(InvalidAuthSessionException::new);
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        if (!user.isEnabled()) throw new InvalidAuthSessionException();
        // User -> origin session -> counters -> challenge. Keep the session locked through commit
        // so logout/revocation cannot slip between authorization and the configuration change.
        var session = sessions.lockByIdAndUserId(sessionId, userId).orElseThrow(InvalidAuthSessionException::new);
        entityManager.refresh(session, LockModeType.PESSIMISTIC_WRITE);
        if (!session.isActiveAt(Instant.now())) throw new InvalidAuthSessionException();
        return user;
    }

    private void requireState(User user, ChallengePurpose purpose) {
        if (purpose != ChallengePurpose.ENABLE && purpose != ChallengePurpose.DISABLE
                || user.isEmailMfaEnabled() == (purpose == ChallengePurpose.ENABLE)) {
            throw new ChallengeException(ApiErrorCode.MFA_STATE_CONFLICT, "La configuración MFA cambió o no admite esta operación.");
        }
    }

    public record SettingsStatus(boolean enabled, Instant enabledAt, String maskedEmail) {}
    public record PreparedResend(ChallengePurpose purpose, ChallengeResendResult result) {
        @Override public String toString() { return "PreparedResend[redacted]"; }
    }
}
