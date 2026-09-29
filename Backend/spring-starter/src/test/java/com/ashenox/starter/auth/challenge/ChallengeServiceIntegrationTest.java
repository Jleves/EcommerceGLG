package com.ashenox.starter.auth.challenge;

import com.ashenox.starter.auth.challenge.model.AuthRateLimitScope;
import com.ashenox.starter.auth.challenge.model.ChallengeDeliveryState;
import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.auth.challenge.repository.AuthChallengeRepository;
import com.ashenox.starter.auth.challenge.repository.AuthRateLimitRepository;
import com.ashenox.starter.auth.challenge.service.ChallengeException;
import com.ashenox.starter.auth.challenge.service.ChallengeCleanupService;
import com.ashenox.starter.auth.challenge.service.ChallengeService;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.shared.error.ApiErrorCode;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ChallengeServiceIntegrationTest {
    @Autowired private ChallengeService service;
    @Autowired private AuthChallengeRepository challenges;
    @Autowired private AuthRateLimitRepository counters;
    @Autowired private UserRepository users;
    @Autowired private AuthSessionService sessions;
    @Autowired private ChallengeCleanupService cleanup;

    @AfterEach
    void removeChallengesBeforeOtherTestsDeleteOriginSessions() {
        challenges.deleteAllInBatch();
    }

    @Test
    void wrongCodesPersistAcrossRequestsAndChallengeCanBeConsumedOnlyOnce() {
        User user = user();
        var issued = service.start(user.getId(), ChallengePurpose.LOGIN, null);
        String cookie = issued.cookieValue();
        assertThat(service.markDelivery(issued.challenge().getId(), 1, ChallengeDeliveryState.SENT)).isTrue();

        String wrong = issued.code().equals("999999") ? "000000" : "999999";
        assertThatThrownBy(() -> service.verifyAndConsume(cookie, ChallengePurpose.LOGIN, null, null, wrong))
                .isInstanceOf(ChallengeException.class)
                .extracting("code").isEqualTo(ApiErrorCode.MFA_CODE_INVALID);
        assertThat(challenges.findById(issued.challenge().getId()).orElseThrow().getFailedAttempts()).isEqualTo(1);
        assertThat(counters.findByScopeAndSubjectKey(AuthRateLimitScope.MFA_CODE_FAILURE,
                Long.toString(user.getId())).orElseThrow().getAttemptCount()).isEqualTo(1);

        service.verifyAndConsume(cookie, ChallengePurpose.LOGIN, null, null, issued.code());
        assertThatThrownBy(() -> service.verifyAndConsume(cookie, ChallengePurpose.LOGIN, null, null, issued.code()))
                .isInstanceOf(ChallengeException.class)
                .extracting("code").isEqualTo(ApiErrorCode.MFA_CHALLENGE_INVALID);
    }

    @Test
    void replacingAndResendingInvalidatePreviousCodesWithoutResettingCounters() {
        User user = user();
        var first = service.start(user.getId(), ChallengePurpose.LOGIN, null);
        var second = service.start(user.getId(), ChallengePurpose.LOGIN, null);
        assertThat(challenges.findById(first.challenge().getId()).orElseThrow().getInvalidatedAt()).isNotNull();
        assertThatThrownBy(() -> service.verifyAndConsume(first.cookieValue(), ChallengePurpose.LOGIN,
                null, null, first.code())).isInstanceOf(ChallengeException.class);

        var current = challenges.findById(second.challenge().getId()).orElseThrow();
        current.setLastSentAt(Instant.now().minusSeconds(61));
        challenges.saveAndFlush(current);
        var resent = service.resend(second.cookieValue(), ChallengePurpose.LOGIN, null, null);
        assertThat(resent.generation()).isEqualTo(2);
        assertThat(resent.expiresAt()).isEqualTo(current.getExpiresAt());
        assertThat(service.markDelivery(resent.challengeId(), 1, ChallengeDeliveryState.SENT)).isFalse();
        assertThat(service.markDelivery(resent.challengeId(), 2, ChallengeDeliveryState.SENT)).isTrue();
        assertThatThrownBy(() -> service.verifyAndConsume(second.cookieValue(), ChallengePurpose.LOGIN,
                null, null, second.code())).isInstanceOf(ChallengeException.class)
                .extracting("code").isEqualTo(ApiErrorCode.MFA_CODE_INVALID);
        service.verifyAndConsume(second.cookieValue(), ChallengePurpose.LOGIN, null, null, resent.code());
        assertThat(counters.findByScopeAndSubjectKey(AuthRateLimitScope.MFA_CODE_ISSUE,
                Long.toString(user.getId())).orElseThrow().getAttemptCount()).isEqualTo(3);
    }

    @Test
    void rejectsWrongPurposeAndOriginAndCancellationIsIdempotent() {
        User user = user();
        String origin = sessions.createSession(user).session().getId();
        var issued = service.start(user.getId(), ChallengePurpose.ENABLE, origin);
        service.markDelivery(issued.challenge().getId(), 1, ChallengeDeliveryState.SENT);
        assertThatThrownBy(() -> service.verifyAndConsume(issued.cookieValue(), ChallengePurpose.DISABLE,
                user.getId(), origin, issued.code())).isInstanceOf(ChallengeException.class);
        assertThatThrownBy(() -> service.verifyAndConsume(issued.cookieValue(), ChallengePurpose.ENABLE,
                user.getId(), "other", issued.code())).isInstanceOf(ChallengeException.class);
        service.cancel(issued.cookieValue());
        service.cancel(issued.cookieValue());
        assertThat(challenges.findById(issued.challenge().getId()).orElseThrow().getInvalidatedAt()).isNotNull();
    }

    @Test
    void issueLimitIsSharedAcrossPurposesAndCancellationDoesNotResetIt() {
        User user = user();
        String origin = sessions.createSession(user).session().getId();
        var first = service.start(user.getId(), ChallengePurpose.LOGIN, null);
        service.cancel(first.cookieValue());
        service.start(user.getId(), ChallengePurpose.ENABLE, origin);
        service.start(user.getId(), ChallengePurpose.DISABLE, origin);
        service.start(user.getId(), ChallengePurpose.LOGIN, null);
        service.start(user.getId(), ChallengePurpose.ENABLE, origin);

        assertThatThrownBy(() -> service.start(user.getId(), ChallengePurpose.DISABLE, origin))
                .isInstanceOf(ChallengeException.class)
                .extracting("code").isEqualTo(ApiErrorCode.MFA_RATE_LIMITED);
        assertThat(counters.findByScopeAndSubjectKey(AuthRateLimitScope.MFA_CODE_ISSUE,
                Long.toString(user.getId())).orElseThrow().getAttemptCount()).isEqualTo(5);
    }

    @Test
    void settingsPurposesUseSameVerificationRulesAndRequireMatchingOrigin() {
        User user = user();
        String origin = sessions.createSession(user).session().getId();
        for (ChallengePurpose purpose : new ChallengePurpose[]{ChallengePurpose.ENABLE, ChallengePurpose.DISABLE}) {
            var issued = service.start(user.getId(), purpose, origin);
            assertThatThrownBy(() -> service.verifyAndConsume(issued.cookieValue(), purpose,
                    user.getId(), origin, issued.code())).isInstanceOf(ChallengeException.class)
                    .extracting("code").isEqualTo(ApiErrorCode.MFA_CHALLENGE_INVALID);
            service.markDelivery(issued.challenge().getId(), 1, ChallengeDeliveryState.SENT);
            service.verifyAndConsume(issued.cookieValue(), purpose, user.getId(), origin, issued.code());
        }
    }

    @Test
    void failedCodeLimitSurvivesNewChallengesAndSecurityVersionInvalidatesPendingCode() {
        User user = user();
        var first = service.start(user.getId(), ChallengePurpose.LOGIN, null);
        service.markDelivery(first.challenge().getId(), 1, ChallengeDeliveryState.SENT);
        String wrong = first.code().equals("999999") ? "000000" : "999999";
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.verifyAndConsume(first.cookieValue(), ChallengePurpose.LOGIN,
                    null, null, wrong)).isInstanceOf(ChallengeException.class)
                    .extracting("code").isEqualTo(ApiErrorCode.MFA_CODE_INVALID);
        }
        assertThatThrownBy(() -> service.verifyAndConsume(first.cookieValue(), ChallengePurpose.LOGIN,
                null, null, first.code())).isInstanceOf(ChallengeException.class)
                .extracting("code").isEqualTo(ApiErrorCode.MFA_RATE_LIMITED);
        var second = service.start(user.getId(), ChallengePurpose.LOGIN, null);
        service.markDelivery(second.challenge().getId(), 1, ChallengeDeliveryState.SENT);
        String secondWrong = second.code().equals("999999") ? "000000" : "999999";
        assertThatThrownBy(() -> service.verifyAndConsume(second.cookieValue(), ChallengePurpose.LOGIN,
                null, null, secondWrong)).isInstanceOf(ChallengeException.class)
                .extracting("code").isEqualTo(ApiErrorCode.MFA_CODE_INVALID);
        assertThat(counters.findByScopeAndSubjectKey(AuthRateLimitScope.MFA_CODE_FAILURE,
                Long.toString(user.getId())).orElseThrow().getAttemptCount()).isEqualTo(6);

        user.setSecurityVersion(user.getSecurityVersion() + 1);
        users.saveAndFlush(user);
        assertThatThrownBy(() -> service.verifyAndConsume(second.cookieValue(), ChallengePurpose.LOGIN,
                null, null, second.code())).isInstanceOf(ChallengeException.class)
                .extracting("code").isEqualTo(ApiErrorCode.MFA_CHALLENGE_INVALID);
    }

    @Test
    void cleanupRemovesOldChallengesButKeepsActiveRateWindow() {
        User user = user();
        var issued = service.start(user.getId(), ChallengePurpose.LOGIN, null);
        var challenge = challenges.findById(issued.challenge().getId()).orElseThrow();
        challenge.setExpiresAt(Instant.now().minusSeconds(172_800));
        challenges.saveAndFlush(challenge);

        cleanup.removeExpiredRecords();

        assertThat(challenges.findById(challenge.getId())).isEmpty();
        assertThat(counters.findByScopeAndSubjectKey(AuthRateLimitScope.MFA_CODE_ISSUE,
                Long.toString(user.getId()))).isPresent();
    }

    @Test
    void wrongSecretAndExpiredChallengeCannotBeUsed() {
        User user = user();
        var issued = service.start(user.getId(), ChallengePurpose.LOGIN, null);
        service.markDelivery(issued.challenge().getId(), 1, ChallengeDeliveryState.SENT);
        String wrongCookie = issued.challenge().getId() + "." + "A".repeat(43);
        assertThatThrownBy(() -> service.verifyAndConsume(wrongCookie, ChallengePurpose.LOGIN,
                null, null, issued.code())).isInstanceOf(ChallengeException.class)
                .extracting("code").isEqualTo(ApiErrorCode.MFA_CHALLENGE_INVALID);

        var challenge = challenges.findById(issued.challenge().getId()).orElseThrow();
        challenge.setExpiresAt(Instant.now().minusSeconds(1));
        challenges.saveAndFlush(challenge);
        assertThatThrownBy(() -> service.verifyAndConsume(issued.cookieValue(), ChallengePurpose.LOGIN,
                null, null, issued.code())).isInstanceOf(ChallengeException.class)
                .extracting("code").isEqualTo(ApiErrorCode.MFA_CHALLENGE_INVALID);
    }

    private User user() {
        User user = new User();
        user.setEmail("mfa-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$1234567890123456789012345678901234567890123456789012");
        user.setRole(Role.USER);
        user.setEnabled(true);
        return users.saveAndFlush(user);
    }
}
