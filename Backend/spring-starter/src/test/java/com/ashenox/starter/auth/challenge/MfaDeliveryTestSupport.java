package com.ashenox.starter.auth.challenge;

import com.ashenox.starter.auth.challenge.event.MfaSettingsChanged;
import com.ashenox.starter.auth.challenge.model.*;
import com.ashenox.starter.auth.challenge.port.MfaEmailSender;
import com.ashenox.starter.auth.challenge.repository.*;
import com.ashenox.starter.auth.challenge.service.*;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.shared.error.ApiErrorCode;
import com.ashenox.starter.user.model.*;
import com.ashenox.starter.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
abstract class MfaDeliveryTestSupport {
    @Autowired ChallengeDeliveryService delivery;
    @MockitoSpyBean ChallengeService engine;
    @MockitoBean MfaEmailSender sender;
    @Autowired AuthChallengeRepository challenges;
    @Autowired AuthRateLimitRepository counters;
    @Autowired UserRepository users;
    @Autowired AuthSessionService sessions;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ApplicationEventPublisher events;

    @AfterEach
    void cleanup() {
        challenges.deleteAllInBatch();
    }

    @ParameterizedTest
    @EnumSource(ChallengePurpose.class)
    void sendsEachPurposeAfterCommitWithoutHoldingUserOrChallengeLocks(ChallengePurpose purpose) {
        User user = user();
        String origin = purpose == ChallengePurpose.LOGIN ? null : sessions.createSession(user).session().getId();
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(invocation.getArgument(0, String.class)).isEqualTo(user.getEmail());
            assertThat(invocation.getArgument(1, ChallengePurpose.class)).isEqualTo(purpose);
            code.set(invocation.getArgument(2));
            // A separate connection must see PENDING and acquire both locks before SMTP finishes.
            try (var pool = Executors.newSingleThreadExecutor()) {
                pool.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    users.lockById(user.getId()).orElseThrow();
                    var pending = challenges.lockUnconsumedByUserAndPurpose(user.getId(), purpose).getFirst();
                    assertThat(pending.getDeliveryState()).isEqualTo(ChallengeDeliveryState.PENDING);
                })).get(5, TimeUnit.SECONDS);
            }
            return null;
        }).when(sender).sendCode(anyString(), any(), anyString(), any());

        var delivered = delivery.start(user.getId(), purpose, origin);
        assertThat(delivered.toString()).doesNotContain(delivered.cookieValue(), code.get());
        assertThat(challenges.findById(delivered.challengeId()).orElseThrow().getDeliveryState())
                .isEqualTo(ChallengeDeliveryState.SENT);
        engine.verifyAndConsume(delivered.cookieValue(), purpose,
                purpose == ChallengePurpose.LOGIN ? null : user.getId(), origin, code.get());
    }

    @Test
    void failurePersistsAndRestartWithoutCookieKeepsAggregateBudget(CapturedOutput output) {
        User user = user();
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(invocation -> {
            code.set(invocation.getArgument(2));
            throw new IllegalStateException("provider details must never escape");
        }).when(sender).sendCode(anyString(), any(), anyString(), any());
        assertThatThrownBy(() -> delivery.start(user.getId(), ChallengePurpose.LOGIN, null))
                .isInstanceOf(ChallengeException.class)
                .hasMessageNotContaining("provider details")
                .extracting("code").isEqualTo(ApiErrorCode.MFA_DELIVERY_UNAVAILABLE);
        var failed = challenges.findAll().stream().filter(c -> c.getUser().getId().equals(user.getId())).findFirst().orElseThrow();
        assertThat(failed.getDeliveryState()).isEqualTo(ChallengeDeliveryState.FAILED);
        assertThat(output.getAll()).doesNotContain(code.get(), user.getEmail(), "provider details must never escape");
        // Even a known valid cookie/code from an internal caller cannot consume FAILED.
        var existing = engine.start(user.getId(), ChallengePurpose.LOGIN, null);
        engine.markDelivery(existing.challenge().getId(), 1, ChallengeDeliveryState.FAILED);
        assertThatThrownBy(() -> engine.verifyAndConsume(existing.cookieValue(), ChallengePurpose.LOGIN,
                null, null, existing.code())).isInstanceOf(ChallengeException.class);

        doNothing().when(sender).sendCode(anyString(), any(), anyString(), any());
        delivery.start(user.getId(), ChallengePurpose.LOGIN, null);
        assertThat(challenges.findById(failed.getId()).orElseThrow().getInvalidatedAt()).isNotNull();
        assertThat(issues(user)).isEqualTo(3);
    }

    @Test
    void accreditedChallengeCanRecoverFromFailedResendWithoutResettingExpiryOrAttempts() {
        User user = user();
        var created = engine.start(user.getId(), ChallengePurpose.LOGIN, null);
        engine.markDelivery(created.challenge().getId(), 1, ChallengeDeliveryState.SENT);
        Instant originalExpiry = challenges.findById(created.challenge().getId()).orElseThrow().getExpiresAt();
        String wrong = created.code().equals("999999") ? "000000" : "999999";
        assertThatThrownBy(() -> engine.verifyAndConsume(created.cookieValue(), ChallengePurpose.LOGIN, null, null, wrong))
                .isInstanceOf(ChallengeException.class);
        allowResend(created.challenge().getId());
        doThrow(new IllegalStateException("SMTP timeout")).when(sender).sendCode(anyString(), any(), anyString(), any());
        assertThatThrownBy(() -> delivery.resend(created.cookieValue(), ChallengePurpose.LOGIN, null, null))
                .isInstanceOf(ChallengeException.class).extracting("code").isEqualTo(ApiErrorCode.MFA_DELIVERY_UNAVAILABLE);
        assertThat(challenges.findById(created.challenge().getId()).orElseThrow().getDeliveryState())
                .isEqualTo(ChallengeDeliveryState.FAILED);
        assertThatThrownBy(() -> delivery.resend(created.cookieValue(), ChallengePurpose.LOGIN, null, null))
                .isInstanceOf(ChallengeException.class).extracting("code").isEqualTo(ApiErrorCode.MFA_RATE_LIMITED);
        allowResend(created.challenge().getId());
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(call -> { code.set(call.getArgument(2)); return null; })
                .when(sender).sendCode(anyString(), any(), anyString(), any());
        var recovered = delivery.resend(created.cookieValue(), ChallengePurpose.LOGIN, null, null);
        assertThat(recovered.cookieValue()).isEqualTo(created.cookieValue());
        assertThat(recovered.generation()).isEqualTo(3);
        assertThat(recovered.expiresAt()).isEqualTo(originalExpiry);
        var saved = challenges.findById(recovered.challengeId()).orElseThrow();
        assertThat(saved.getFailedAttempts()).isEqualTo(1);
        assertThat(saved.getResendCount()).isEqualTo(2);
        assertThat(issues(user)).isEqualTo(3);
        engine.verifyAndConsume(recovered.cookieValue(), ChallengePurpose.LOGIN, null, null, code.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void processInterruptionLeavesPendingAndCanRecover(boolean afterSmtp) {
        User user = user();
        var created = engine.start(user.getId(), ChallengePurpose.LOGIN, null);
        assertThatThrownBy(() -> engine.verifyAndConsume(created.cookieValue(), ChallengePurpose.LOGIN,
                null, null, created.code())).isInstanceOf(ChallengeException.class);
        allowResend(created.challenge().getId());
        if (afterSmtp) {
            doThrow(new DataAccessResourceFailureException("simulated loss before delivery commit"))
                    .when(engine).markDelivery(created.challenge().getId(), 2, ChallengeDeliveryState.SENT);
        } else {
            doThrow(new SimulatedProcessCrash()).when(sender).sendCode(anyString(), any(), anyString(), any());
        }
        assertThatThrownBy(() -> delivery.resend(created.cookieValue(), ChallengePurpose.LOGIN, null, null))
                .isInstanceOf(afterSmtp ? DataAccessResourceFailureException.class : SimulatedProcessCrash.class);
        var pending = challenges.findById(created.challenge().getId()).orElseThrow();
        assertThat(pending.getDeliveryState()).isEqualTo(ChallengeDeliveryState.PENDING);
        assertThat(pending.getCodeGeneration()).isEqualTo(2);
        doNothing().when(sender).sendCode(anyString(), any(), anyString(), any());
        allowResend(pending.getId());
        assertThat(delivery.resend(created.cookieValue(), ChallengePurpose.LOGIN, null, null).generation()).isEqualTo(3);
        doCallRealMethod().when(engine).markDelivery(pending.getId(), 2, ChallengeDeliveryState.SENT);
        assertThat(engine.markDelivery(pending.getId(), 2, ChallengeDeliveryState.SENT)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"replace", "resend", "cancel", "expire", "securityVersion"})
    void lateSmtpResultCannotRehabilitateAnObsoleteGeneration(String change) {
        User user = user();
        var created = engine.start(user.getId(), ChallengePurpose.LOGIN, null);
        allowResend(created.challenge().getId());
        doAnswer(call -> {
            switch (change) {
                case "replace" -> engine.start(user.getId(), ChallengePurpose.LOGIN, null);
                case "resend" -> {
                    allowResend(created.challenge().getId());
                    engine.resend(created.cookieValue(), ChallengePurpose.LOGIN, null, null);
                }
                case "cancel" -> engine.cancel(created.cookieValue());
                case "expire" -> {
                    var current = challenges.findById(created.challenge().getId()).orElseThrow();
                    current.setExpiresAt(Instant.now().minusSeconds(1));
                    challenges.saveAndFlush(current);
                }
                case "securityVersion" -> {
                    user.setSecurityVersion(user.getSecurityVersion() + 1);
                    users.saveAndFlush(user);
                }
                default -> throw new AssertionError(change);
            }
            return null;
        }).when(sender).sendCode(anyString(), any(), anyString(), any());
        assertThatThrownBy(() -> delivery.resend(created.cookieValue(), ChallengePurpose.LOGIN, null, null))
                .isInstanceOf(ChallengeException.class).extracting("code").isEqualTo(ApiErrorCode.MFA_CHALLENGE_INVALID);
        assertThat(challenges.findById(created.challenge().getId()).orElseThrow().getDeliveryState())
                .isEqualTo(ChallengeDeliveryState.PENDING);
    }

    @Test
    void rejectsEnclosingTransactionBeforeCreatingOrSendingAnything() {
        User user = user();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                delivery.start(user.getId(), ChallengePurpose.LOGIN, null)))
                .isInstanceOf(IllegalTransactionStateException.class);
        verifyNoInteractions(sender);
        assertThat(counters.findByScopeAndSubjectKey(AuthRateLimitScope.MFA_CODE_ISSUE, user.getId().toString())).isEmpty();
    }

    @Test
    void jdbcDatesAgreeWithJpaAndCooldownInNonUtcTimezone() {
        var previous = java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
            User user = user();
            var created = engine.start(user.getId(), ChallengePurpose.LOGIN, null);
            var counter = counters.findByScopeAndSubjectKey(AuthRateLimitScope.MFA_CODE_ISSUE,
                    user.getId().toString()).orElseThrow();
            assertThat(counter.getWindowStartedAt()).isCloseTo(Instant.now(), within(5, java.time.temporal.ChronoUnit.SECONDS));
            allowResend(created.challenge().getId());
            var delivered = delivery.resend(created.cookieValue(), ChallengePurpose.LOGIN, null, null);
            var saved = challenges.findById(delivered.challengeId()).orElseThrow();
            assertThat(saved.getLastSentAt()).isCloseTo(Instant.now(), within(5, java.time.temporal.ChronoUnit.SECONDS));
            assertThatThrownBy(() -> delivery.resend(created.cookieValue(), ChallengePurpose.LOGIN, null, null))
                    .isInstanceOf(ChallengeException.class).extracting("code").isEqualTo(ApiErrorCode.MFA_RATE_LIMITED);
        } finally {
            java.util.TimeZone.setDefault(previous);
        }
    }

    @Test
    void notificationsOnlyRunAfterCommitAndFailureDoesNotUndoSettings() {
        User user = user();
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(users.findById(user.getId()).orElseThrow().isEmailMfaEnabled()).isTrue();
            throw new IllegalStateException("sensitive provider error");
        }).when(sender).sendSettingsChanged(user.getEmail(), true);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var locked = users.lockById(user.getId()).orElseThrow();
            locked.setEmailMfaEnabled(true);
            events.publishEvent(new MfaSettingsChanged(user.getId(), user.getEmail(), true));
            verifyNoInteractions(sender);
        });
        verify(sender).sendSettingsChanged(user.getEmail(), true);
        assertThat(users.findById(user.getId()).orElseThrow().isEmailMfaEnabled()).isTrue();
    }

    @Test
    void rolledBackOrNonTransactionalEventsDoNotSendNotifications() {
        User user = user();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(new MfaSettingsChanged(user.getId(), user.getEmail(), false));
            status.setRollbackOnly();
        });
        events.publishEvent(new MfaSettingsChanged(user.getId(), user.getEmail(), false));
        verifyNoInteractions(sender);
    }

    private void allowResend(String id) {
        var challenge = challenges.findById(id).orElseThrow();
        challenge.setLastSentAt(Instant.now().minusSeconds(61));
        challenges.saveAndFlush(challenge);
    }

    private int issues(User user) {
        return counters.findByScopeAndSubjectKey(AuthRateLimitScope.MFA_CODE_ISSUE, user.getId().toString())
                .orElseThrow().getAttemptCount();
    }

    private User user() {
        User user = new User();
        user.setEmail("delivery-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$1234567890123456789012345678901234567890123456789012");
        user.setRole(Role.USER);
        user.setEnabled(true);
        return users.saveAndFlush(user);
    }

    private static final class SimulatedProcessCrash extends Error {}
}
