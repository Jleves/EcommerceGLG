package com.ashenox.starter.auth;

import com.ashenox.starter.auth.challenge.model.*;
import com.ashenox.starter.auth.challenge.port.MfaEmailSender;
import com.ashenox.starter.auth.challenge.repository.AuthChallengeRepository;
import com.ashenox.starter.auth.challenge.service.*;
import com.ashenox.starter.auth.passwordchange.*;
import com.ashenox.starter.auth.passwordreset.model.PasswordResetToken;
import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.service.impl.PasswordResetServiceImpl;
import com.ashenox.starter.auth.service.MfaSettingsService;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.auth.session.service.*;
import com.ashenox.starter.security.error.InvalidAuthSessionException;
import com.ashenox.starter.security.error.InvalidRefreshTokenException;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.user.model.*;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class MfaSettingsTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired AuthChallengeRepository challenges;
    @Autowired PasswordEncoder encoder;
    @Autowired JWTUtil jwt;
    @Autowired MfaSettingsService settings;
    @Autowired ChallengeDeliveryService delivery;
    @Autowired PasswordChangeService passwords;
    @Autowired PasswordResetServiceImpl resets;
    @Autowired PasswordResetTokenRepository resetTokens;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean MfaEmailSender sender;
    @MockitoSpyBean AuthSessionService sessionService;
    User user;
    IssuedSession origin;
    Cookie access;
    String code;

    @BeforeEach void setup() {
        user = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash(encoder.encode("secure-password")).role(Role.USER).enabled(true).build());
        origin = sessionService.createSession(user);
        access = access(user, origin);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(invocation.getArgument(0, String.class)).isEqualTo(user.getEmail());
            code = invocation.getArgument(2);
            return null;
        }).when(sender).sendCode(anyString(), any(), anyString(), any());
    }

    @AfterEach void cleanup() { challenges.deleteAllInBatch(); }

    @ParameterizedTest @EnumSource(Role.class)
    void completesEnableAndDisableForEveryRole(Role role) throws Exception {
        user.setRole(role); users.saveAndFlush(user); access = access(user, origin);
        var otherSession = sessionService.createSession(user);
        var otherChallenge = delivery.start(user.getId(), ChallengePurpose.LOGIN, null);
        mvc.perform(get("/api/auth/mfa/status").cookie(access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.maskedEmail").value(user.getEmail().substring(0, 1) + "***@example.com"));
        Cookie pending = pending("enable");
        assertThat(currentUser().isEmailMfaEnabled()).isFalse();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(currentUser().isEmailMfaEnabled()).isEqualTo(invocation.getArgument(1, Boolean.class));
            assertThat(sessions.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNotNull();
            return null;
        }).when(sender).sendSettingsChanged(eq(user.getEmail()), anyBoolean());
        confirm("enable", pending, code, access).andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("ACCESS_TOKEN", 0)).andExpect(cookie().maxAge("REFRESH_TOKEN", 0))
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0)).andExpect(cookie().maxAge("MFA_CHALLENGE", 0))
                .andExpect(cookie().path("MFA_CHALLENGE", "/api/auth/mfa"));
        assertThat(currentUser().isEmailMfaEnabled()).isTrue();
        assertThat(currentUser().getEmailMfaEnabledAt()).isNotNull();
        assertThat(currentUser().getSecurityVersion()).isEqualTo(1);
        assertThat(challenges.findById(otherChallenge.challengeId()).orElseThrow().getInvalidatedAt()).isNotNull();
        assertThat(sessions.findById(otherSession.session().getId()).orElseThrow().getRevokedAt()).isNotNull();
        verify(sender).sendSettingsChanged(user.getEmail(), true);
        // D02: all authenticated routes reject access tokens from revoked sessions.
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        mvc.perform(get("/api/auth/mfa/status").cookie(access)).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> sessionService.rotate(otherSession.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
        var login = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"secure-password\"}"))
                .andExpect(status().isAccepted()).andReturn();
        var authenticated = mvc.perform(post("/api/auth/mfa/login/verify").with(csrf())
                .cookie(login.getResponse().getCookie("MFA_CHALLENGE")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk()).andReturn();
        access = authenticated.getResponse().getCookie("ACCESS_TOKEN");
        mvc.perform(get("/api/auth/mfa/status").cookie(access)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true)).andExpect(jsonPath("$.enabledAt").isNotEmpty());
        pending = pending("disable");
        assertThat(currentUser().isEmailMfaEnabled()).isTrue();
        confirm("disable", pending, code, access).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isUnauthorized());
        assertThat(currentUser().isEmailMfaEnabled()).isFalse();
        assertThat(currentUser().getEmailMfaEnabledAt()).isNull();
        assertThat(currentUser().getSecurityVersion()).isEqualTo(2);
        verify(sender).sendSettingsChanged(user.getEmail(), false);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"secure-password\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("AUTHENTICATED"));
    }

    @Test void sharesPasswordFailureBudgetWithProfile() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> passwords.change(user.getId(), new ChangePasswordRequest("wrong", "replacement-password")))
                    .isInstanceOf(PasswordChangeException.class);
        }
        Instant window = currentUser().getPasswordChangeWindowStart();
        for (int i = 0; i < 2; i++) start("enable", "wrong").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("currentPassword"));
        start("enable", "secure-password").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("MFA_RATE_LIMITED")).andExpect(header().exists("Retry-After"));
        assertThatThrownBy(() -> passwords.change(user.getId(), new ChangePasswordRequest("secure-password", "replacement-password")))
                .isInstanceOf(PasswordChangeException.class);
        assertThat(currentUser().getPasswordChangeFailures()).isEqualTo(5);
        assertThat(currentUser().getPasswordChangeWindowStart()).isEqualTo(window);
        assertThat(currentUser().isEmailMfaEnabled()).isFalse();
        verifyNoInteractions(sender);
    }

    @Test void successfulStartPreservesActiveFailuresAndResetsOnlyExpiredWindow() throws Exception {
        start("enable", "wrong").andExpect(status().isBadRequest());
        pending("enable");
        assertThat(currentUser().getPasswordChangeFailures()).isEqualTo(1);
        User updated = currentUser();
        updated.setPasswordChangeWindowStart(Instant.now().minusSeconds(901));
        updated.setPasswordChangeFailures(5); users.saveAndFlush(updated);
        pending("enable");
        assertThat(currentUser().getPasswordChangeFailures()).isZero();
        assertThat(currentUser().getPasswordChangeWindowStart()).isAfter(Instant.now().minusSeconds(30));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void rejectsIncompatibleStateWithoutChangingConfiguration(boolean enabled) throws Exception {
        user.setEmailMfaEnabled(enabled); users.saveAndFlush(user);
        String operation = enabled ? "enable" : "disable";
        start(operation, "secure-password").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MFA_STATE_CONFLICT"));
        confirm(operation, new Cookie("MFA_CHALLENGE", "missing"), "123456", access)
                .andExpect(status().isConflict());
        assertThat(currentUser().isEmailMfaEnabled()).isEqualTo(enabled);
        verifyNoInteractions(sender);
    }

    @ParameterizedTest @ValueSource(strings = {"other-session", "other-user", "foreign-sid", "revoked", "expired", "missing", "disabled"})
    void rejectsInvalidOrDifferentOrigin(String reason) throws Exception {
        Cookie pending = pending("enable");
        Cookie caller = access;
        int expected = 401;
        switch (reason) {
            case "other-session" -> { caller = access(user, sessionService.createSession(user)); expected = 400; }
            case "other-user" -> {
                User other = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                        .passwordHash("unused").role(Role.USER).enabled(true).build());
                caller = access(other, sessionService.createSession(other)); expected = 400;
            }
            case "foreign-sid" -> {
                User other = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                        .passwordHash("unused").role(Role.USER).enabled(true).build());
                caller = access(user, sessionService.createSession(other));
            }
            case "revoked" -> sessionService.revoke(origin.refreshToken());
            case "expired" -> {
                var session = sessions.findById(origin.session().getId()).orElseThrow();
                session.setExpiresAt(Instant.now().minusSeconds(1)); sessions.saveAndFlush(session);
            }
            case "missing" -> caller = new Cookie("ACCESS_TOKEN", jwt.generateToken(user.getId(), null));
            case "disabled" -> { user.setEnabled(false); users.saveAndFlush(user); }
        }
        confirm("enable", pending, code, caller).andExpect(status().is(expected));
        mvc.perform(post("/api/auth/mfa/settings/resend").with(csrf()).cookie(caller, pending))
                .andExpect(status().is(expected));
        assertThat(currentUser().isEmailMfaEnabled()).isFalse();
        assertThat(challenges.findById(id(pending)).orElseThrow().getConsumedAt()).isNull();
        verify(sender, never()).sendSettingsChanged(anyString(), anyBoolean());
    }

    @Test void ignoresClientIdentityAndSessionFields() throws Exception {
        var result = mvc.perform(post("/api/auth/mfa/enable/start").with(csrf()).cookie(access)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"secure-password\",\"userId\":999999,\"sessionId\":\"forged\",\"purpose\":\"DISABLE\",\"email\":\"other@example.com\"}"))
                .andExpect(status().isAccepted()).andReturn();
        var challenge = challenges.findById(id(result.getResponse().getCookie("MFA_CHALLENGE"))).orElseThrow();
        assertThat(challenge.getUser().getId()).isEqualTo(user.getId());
        assertThat(challenge.getOriginSessionId()).isEqualTo(origin.session().getId());
        assertThat(challenge.getPurpose()).isEqualTo(ChallengePurpose.ENABLE);
    }

    @Test void rejectsWrongPurposeAndExpiredChallenge() throws Exception {
        var login = delivery.start(user.getId(), ChallengePurpose.LOGIN, null);
        confirm("enable", new Cookie("MFA_CHALLENGE", login.cookieValue()), code, access).andExpect(status().isBadRequest());
        Cookie pending = pending("enable");
        var challenge = challenges.findById(id(pending)).orElseThrow();
        challenge.setExpiresAt(Instant.now().minusSeconds(1)); challenges.saveAndFlush(challenge);
        confirm("enable", pending, code, access).andExpect(status().isBadRequest());
        assertThat(currentUser().isEmailMfaEnabled()).isFalse();
    }

    @Test void wrongCodePersistsAndCancellationDoesNotChangeSettings() throws Exception {
        Cookie pending = pending("enable");
        confirm("enable", pending, "wrong", access).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MFA_CODE_INVALID"));
        assertThat(challenges.findById(id(pending)).orElseThrow().getFailedAttempts()).isEqualTo(1);
        mvc.perform(post("/api/auth/mfa/cancel").with(csrf()).cookie(pending)).andExpect(status().isNoContent());
        confirm("enable", pending, code, access).andExpect(status().isBadRequest());
        assertThat(currentUser().isEmailMfaEnabled()).isFalse();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void smtpFailurePreservesStateAndExistingChallengeCanBeResent(boolean enabled) throws Exception {
        user.setEmailMfaEnabled(enabled); users.saveAndFlush(user);
        String operation = enabled ? "disable" : "enable";
        Cookie pending = pending(operation);
        mvc.perform(post("/api/auth/mfa/settings/resend").with(csrf()).cookie(access, pending))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        makeResendAvailable(pending);
        doThrow(new IllegalStateException("SMTP unavailable")).when(sender).sendCode(anyString(), any(), anyString(), any());
        mvc.perform(post("/api/auth/mfa/settings/resend").with(csrf()).cookie(access, pending))
                .andExpect(status().isServiceUnavailable());
        assertThat(currentUser().isEmailMfaEnabled()).isEqualTo(enabled);
        assertThat(challenges.findById(id(pending)).orElseThrow().getDeliveryState()).isEqualTo(ChallengeDeliveryState.FAILED);
        confirm(operation, pending, code, access).andExpect(status().isBadRequest());
        makeResendAvailable(pending);
        doAnswer(invocation -> { code = invocation.getArgument(2); return null; })
                .when(sender).sendCode(anyString(), any(), anyString(), any());
        mvc.perform(post("/api/auth/mfa/settings/resend").with(csrf()).cookie(access, pending))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.expiresAt").exists());
        confirm(operation, pending, code, access).andExpect(status().isNoContent());
        assertThat(currentUser().isEmailMfaEnabled()).isEqualTo(!enabled);
    }

    @Test void initialSmtpFailureHasNoCookieOrConfigurationChange() throws Exception {
        doThrow(new IllegalStateException("SMTP unavailable")).when(sender).sendCode(anyString(), any(), anyString(), any());
        start("enable", "secure-password").andExpect(status().isServiceUnavailable())
                .andExpect(cookie().doesNotExist("MFA_CHALLENGE"));
        assertThat(currentUser().isEmailMfaEnabled()).isFalse();
        assertThat(sessions.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNull();
    }

    @Test void notificationFailureDoesNotUndoConfirmedSettings() throws Exception {
        Cookie pending = pending("enable");
        doThrow(new IllegalStateException("notification failed")).when(sender).sendSettingsChanged(anyString(), anyBoolean());
        confirm("enable", pending, code, access).andExpect(status().isNoContent());
        assertThat(currentUser().isEmailMfaEnabled()).isTrue();
        assertThat(challenges.findById(id(pending)).orElseThrow().getConsumedAt()).isNotNull();
    }

    @Test void rollbackRestoresSettingsConsumptionOtherChallengesAndSessions() throws Exception {
        Cookie pending = pending("enable");
        var other = delivery.start(user.getId(), ChallengePurpose.LOGIN, null);
        String settingsCode;
        // Starting another purpose changes only the test capture, not the pending ENABLE code.
        var replacement = delivery.deliverCreated(settings.start(user.getId(), origin.session().getId(), ChallengePurpose.ENABLE, "secure-password"));
        pending = new Cookie("MFA_CHALLENGE", replacement.cookieValue()); settingsCode = code;
        String cookieValue = pending.getValue();
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            settings.confirm(user.getId(), origin.session().getId(), ChallengePurpose.ENABLE, cookieValue, settingsCode);
            throw new IllegalStateException("rollback after event publication");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(currentUser().isEmailMfaEnabled()).isFalse();
        assertThat(currentUser().getSecurityVersion()).isZero();
        assertThat(currentUser().getEmailMfaEnabledAt()).isNull();
        assertThat(challenges.findById(id(pending)).orElseThrow().getConsumedAt()).isNull();
        assertThat(challenges.findById(other.challengeId()).orElseThrow().getInvalidatedAt()).isNull();
        assertThat(sessions.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNull();
        verify(sender, never()).sendSettingsChanged(anyString(), anyBoolean());
        confirm("enable", pending, settingsCode, access).andExpect(status().isNoContent());
    }

    @Test void allSettingsMutationsRequireAuthenticationAndCsrf() throws Exception {
        for (String path : new String[]{"enable/start", "enable/confirm", "disable/start", "disable/confirm", "settings/resend"}) {
            mvc.perform(post("/api/auth/mfa/" + path).cookie(access).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPassword\":\"secure-password\",\"code\":\"123456\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
            mvc.perform(post("/api/auth/mfa/" + path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/auth/mfa/status")).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void passwordChangeAndResetRevokeOriginAndPreventConfirmation(boolean reset) throws Exception {
        Cookie pending = pending("enable");
        if (reset) {
            String raw = UUID.randomUUID().toString();
            var token = new PasswordResetToken(); token.setUser(user);
            token.setTokenHash(org.apache.commons.codec.digest.DigestUtils.sha256Hex(raw));
            token.setExpiresAt(Instant.now().plusSeconds(120)); resetTokens.saveAndFlush(token);
            resets.resetPassword(raw, "replacement-password");
        } else passwords.change(user.getId(), new ChangePasswordRequest("secure-password", "replacement-password"));
        confirm("enable", pending, code, access).andExpect(status().isUnauthorized());
        assertThat(currentUser().isEmailMfaEnabled()).isFalse();
        assertThat(challenges.findById(id(pending)).orElseThrow().getInvalidatedAt()).isNotNull();
    }

    @Test void disabledAccountCannotRefreshAnExistingSession() {
        user.setEnabled(false); users.saveAndFlush(user);
        assertThatThrownBy(() -> sessionService.rotate(origin.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test void refreshLoadedBeforeConfirmationRejectsAfterCommitWithoutLockTimeout() throws Exception {
        Cookie pending = pending("enable");
        var loaded = new CountDownLatch(1);
        var confirmed = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var refresh = pool.submit(() -> {
                assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(tx -> {
                    // Establish a stale persistence context and MySQL repeatable-read snapshot.
                    sessions.findWithUserById(origin.session().getId()).orElseThrow();
                    loaded.countDown(); await(confirmed);
                    return sessionService.rotate(origin.refreshToken());
                })).isInstanceOf(InvalidRefreshTokenException.class);
            });
            try {
                assertThat(loaded.await(5, TimeUnit.SECONDS)).isTrue();
                settings.confirm(user.getId(), origin.session().getId(), ChallengePurpose.ENABLE, pending.getValue(), code);
            } finally { confirmed.countDown(); }
            refresh.get(10, TimeUnit.SECONDS);
        }
        assertThat(currentUser().isEmailMfaEnabled()).isTrue();
        assertThat(sessions.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test void simultaneousConfirmationsChangeStateOnlyOnce() throws Exception {
        Cookie pending = pending("enable");
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> {
                gate.await();
                try { settings.confirm(user.getId(), origin.session().getId(), ChallengePurpose.ENABLE, pending.getValue(), code); return true; }
                catch (InvalidAuthSessionException | ChallengeException expected) { return false; }
            };
            var a = pool.submit(task); var b = pool.submit(task); gate.countDown();
            assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(currentUser().getSecurityVersion()).isEqualTo(1);
        verify(sender, times(1)).sendSettingsChanged(user.getEmail(), true);
    }

    @Test void refreshRacingConfirmationCannotRestoreRevokedSession() throws Exception {
        Cookie pending = pending("enable");
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var confirm = pool.submit(() -> {
                await(gate);
                settings.confirm(user.getId(), origin.session().getId(), ChallengePurpose.ENABLE, pending.getValue(), code);
            });
            var refresh = pool.submit(() -> {
                await(gate);
                try { return sessionService.rotate(origin.refreshToken()); }
                catch (InvalidRefreshTokenException expected) { return null; }
            });
            gate.countDown(); confirm.get(15, TimeUnit.SECONDS);
            var rotated = refresh.get(15, TimeUnit.SECONDS);
            if (rotated != null) assertThatThrownBy(() -> sessionService.rotate(rotated.refreshToken()))
                    .isInstanceOf(InvalidRefreshTokenException.class);
        }
        assertThat(currentUser().isEmailMfaEnabled()).isTrue();
        assertThat(sessions.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test void logoutRacingConfirmationCannotAuthorizeAfterRevocation() throws Exception {
        Cookie pending = pending("enable");
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var confirmation = pool.submit(() -> {
                await(gate);
                try { settings.confirm(user.getId(), origin.session().getId(), ChallengePurpose.ENABLE, pending.getValue(), code); return true; }
                catch (InvalidAuthSessionException expected) { return false; }
            });
            var logout = pool.submit(() -> { await(gate); sessionService.revoke(origin.refreshToken()); });
            gate.countDown();
            boolean confirmed = confirmation.get(15, TimeUnit.SECONDS); logout.get(15, TimeUnit.SECONDS);
            assertThat(currentUser().isEmailMfaEnabled()).isEqualTo(confirmed);
            assertThat(challenges.findById(id(pending)).orElseThrow().getConsumedAt() != null).isEqualTo(confirmed);
        }
        assertThat(sessions.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    void await(CountDownLatch gate) {
        try { if (!gate.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("concurrent operation timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    User currentUser() { return users.findById(user.getId()).orElseThrow(); }
    Cookie access(User owner, IssuedSession session) { return new Cookie("ACCESS_TOKEN", jwt.generateToken(owner.getId(), session.session().getId())); }
    String id(Cookie cookie) { return cookie.getValue().substring(0, 36); }
    void makeResendAvailable(Cookie pending) {
        var challenge = challenges.findById(id(pending)).orElseThrow();
        challenge.setLastSentAt(Instant.now().minusSeconds(61)); challenges.saveAndFlush(challenge);
    }
    Cookie pending(String operation) throws Exception { return start(operation, "secure-password").andExpect(status().isAccepted())
            .andExpect(jsonPath("$.expiresAt").exists()).andExpect(jsonPath("$.resendAvailableAt").exists())
            .andExpect(jsonPath("$.code").doesNotExist()).andReturn().getResponse().getCookie("MFA_CHALLENGE"); }
    ResultActions start(String operation, String password) throws Exception {
        return mvc.perform(post("/api/auth/mfa/" + operation + "/start").with(csrf()).cookie(access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"" + password + "\"}"));
    }
    ResultActions confirm(String operation, Cookie pending, String value, Cookie caller) throws Exception {
        return mvc.perform(post("/api/auth/mfa/" + operation + "/confirm").with(csrf()).cookie(caller, pending)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + value + "\"}"));
    }
}
