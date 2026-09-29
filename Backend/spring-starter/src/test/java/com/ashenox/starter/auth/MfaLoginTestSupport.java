package com.ashenox.starter.auth;

import com.ashenox.starter.auth.challenge.model.*;
import com.ashenox.starter.auth.challenge.port.MfaEmailSender;
import com.ashenox.starter.auth.challenge.repository.AuthChallengeRepository;
import com.ashenox.starter.auth.challenge.service.*;
import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.passwordchange.ChangePasswordRequest;
import com.ashenox.starter.auth.passwordchange.PasswordChangeService;
import com.ashenox.starter.auth.passwordreset.model.PasswordResetToken;
import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.service.EmailMfaService;
import com.ashenox.starter.auth.service.impl.PasswordResetServiceImpl;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.model.*;
import com.ashenox.starter.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.apache.commons.codec.digest.DigestUtils;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Date;
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
abstract class MfaLoginTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired AuthChallengeRepository challenges;
    @Autowired PasswordEncoder encoder;
    @Autowired EmailMfaService mfa;
    @Autowired ChallengeDeliveryService delivery;
    @MockitoSpyBean ChallengeCrypto crypto;
    @Autowired PasswordChangeService passwords;
    @Autowired PasswordResetServiceImpl resets;
    @Autowired PasswordResetTokenRepository resetTokens;
    @Autowired AppProperties properties;
    @MockitoBean MfaEmailSender sender;
    @MockitoSpyBean AuthSessionService sessionService;
    User user;
    String code;

    @BeforeEach void setup() {
        user = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash(encoder.encode("secure-password")).role(Role.USER).enabled(true)
                .emailMfaEnabled(true).build());
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            code = invocation.getArgument(2);
            return null;
        }).when(sender).sendCode(anyString(), any(), anyString(), any());
    }

    @AfterEach void cleanup() { challenges.deleteAllInBatch(); }

    @ParameterizedTest @EnumSource(Role.class)
    void requiresSecondStepForEveryRole(Role role) throws Exception {
        user.setRole(role);
        users.saveAndFlush(user);
        var first = login().andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("MFA_REQUIRED"))
                .andExpect(jsonPath("$.user").doesNotExist())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(jsonPath("$.maskedEmail").value(user.getEmail().substring(0, 1) + "***@example.com"))
                .andExpect(cookie().httpOnly("MFA_CHALLENGE", true))
                .andExpect(cookie().path("MFA_CHALLENGE", "/api/auth/mfa"))
                .andExpect(cookie().maxAge("ACCESS_TOKEN", 0)).andReturn();
        Cookie pending = first.getResponse().getCookie("MFA_CHALLENGE");
        assertThat(countSessions()).isZero();
        assertThat(first.getRequest().getSession(false)).isNull();
        assertThat(first.getResponse().getContentAsString()).doesNotContain(pending.getValue());
        mvc.perform(get("/api/auth/me").cookie(pending)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(pending)).andExpect(status().isUnauthorized());
        var authenticated = verifyCode(pending, code).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED"))
                .andExpect(cookie().exists("ACCESS_TOKEN"))
                .andExpect(cookie().exists("REFRESH_TOKEN"))
                .andExpect(cookie().maxAge("MFA_CHALLENGE", 0)).andReturn();
        assertJwtIdentity(authenticated);
        mvc.perform(get("/api/auth/me").cookie(authenticated.getResponse().getCookie("ACCESS_TOKEN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value(user.getEmail()));
        var refreshed = mvc.perform(post("/api/auth/refresh").with(csrf())
                        .cookie(authenticated.getResponse().getCookie("REFRESH_TOKEN")))
                .andExpect(status().isNoContent()).andReturn();
        assertJwtIdentity(refreshed);
        verifyCode(pending, code).andExpect(status().isBadRequest());
        assertThat(countSessions()).isEqualTo(1);
    }

    @ParameterizedTest @EnumSource(Role.class)
    void retainsSingleStepWhenDisabled(Role role) throws Exception {
        user.setRole(role); user.setEmailMfaEnabled(false); users.saveAndFlush(user);
        var authenticated = login().andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHENTICATED")).andReturn();
        assertJwtIdentity(authenticated);
        assertThat(countSessions()).isEqualTo(1);
        verifyNoInteractions(sender);
    }

    @Test void persistsFailuresAndReturnsCooldown() throws Exception {
        Cookie pending = pending();
        verifyCode(pending, "bad").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MFA_CODE_INVALID"));
        assertThat(challenges.findById(id(pending)).orElseThrow().getFailedAttempts()).isEqualTo(1);
        mvc.perform(post("/api/auth/mfa/login/resend").with(csrf()).cookie(pending))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        verifyCode(pending, code).andExpect(status().isOk());
    }

    @Test void requiresCodeAsJsonString() throws Exception {
        Cookie pending = pending();
        mvc.perform(post("/api/auth/mfa/login/verify").with(csrf()).cookie(pending)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":" + Integer.parseInt(code) + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MFA_CODE_INVALID"));
        assertThat(countSessions()).isZero();
        verifyCode(pending, code).andExpect(status().isOk());
    }

    @Test void acceptsLeadingZerosAndRejectsOldCodeAfterResend() throws Exception {
        Cookie pending = pending();
        var challenge = challenges.findById(id(pending)).orElseThrow();
        challenge.setLastSentAt(Instant.now().minusSeconds(61));
        challenge.setFailedAttempts(2);
        challenge.setCodeHmac(crypto.codeHmac(challenge.getId(), ChallengePurpose.LOGIN, 1, "000001"));
        challenges.saveAndFlush(challenge);
        mvc.perform(post("/api/auth/mfa/login/resend").with(csrf()).cookie(pending))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.expiresAt").exists());
        var resent = challenges.findById(id(pending)).orElseThrow();
        assertThat(resent.getFailedAttempts()).isEqualTo(2);
        assertThat(resent.getExpiresAt()).isEqualTo(challenge.getExpiresAt());
        // Use a deterministic code for this generation to verify leading-zero parsing.
        resent.setCodeHmac(crypto.codeHmac(resent.getId(), ChallengePurpose.LOGIN, 2, "000002"));
        challenges.saveAndFlush(resent);
        verifyCode(pending, "000001").andExpect(status().isBadRequest());
        verifyCode(pending, "000002").andExpect(status().isOk());
    }

    @Test void smtpFailureDoesNotIssueSessionOrChallengeCookie() throws Exception {
        doThrow(new IllegalStateException("SMTP unavailable")).when(sender).sendCode(anyString(), any(), anyString(), any());
        login().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MFA_DELIVERY_UNAVAILABLE"))
                .andExpect(cookie().doesNotExist("MFA_CHALLENGE"));
        assertThat(countSessions()).isZero();
    }

    @Test void clearsPreviousCookiesAndRevokesPresentedRefresh() throws Exception {
        var previous = sessionService.createSession(user);
        login(new Cookie("REFRESH_TOKEN", previous.refreshToken()), new Cookie("ACCESS_TOKEN", "old"))
                .andExpect(status().isAccepted()).andExpect(cookie().maxAge("ACCESS_TOKEN", 0))
                .andExpect(cookie().maxAge("REFRESH_TOKEN", 0));
        assertThat(sessions.findById(previous.session().getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(countSessions()).isEqualTo(1);
    }

    @Test void csrfRequiredAndExpiredJwtDoesNotBlockPublicMfaRoutes() throws Exception {
        Cookie pending = pending();
        String expired = Jwts.builder().setSubject(user.getId().toString()).setExpiration(Date.from(Instant.now().minusSeconds(10)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecurity().getJwt().getSecret()))).compact();
        Cookie access = new Cookie("ACCESS_TOKEN", expired);
        for (String path : new String[]{"login/verify", "login/resend", "cancel"}) {
            mvc.perform(post("/api/auth/mfa/" + path).cookie(pending, access).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"123456\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        }
        mvc.perform(post("/api/auth/mfa/login/resend").with(csrf()).cookie(pending, access))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/auth/mfa/login/verify").with(csrf()).cookie(pending, access)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/mfa/cancel").with(csrf()).cookie(pending, access)).andExpect(status().isNoContent());
        mvc.perform(get("/api/auth/mfa/status").cookie(pending)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/mfa/enable/start").with(csrf()).cookie(pending)).andExpect(status().isUnauthorized());
    }

    @Test void cancellationIsIdempotentAndOnlyAffectsProvenChallenge() throws Exception {
        Cookie pending = pending();
        Cookie forged = new Cookie("MFA_CHALLENGE", id(pending) + "." + crypto.newSecret());
        mvc.perform(post("/api/auth/mfa/cancel").with(csrf()).cookie(forged)).andExpect(status().isNoContent());
        assertThat(challenges.findById(id(pending)).orElseThrow().getInvalidatedAt()).isNull();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/mfa/cancel").with(csrf()).cookie(pending)).andExpect(status().isNoContent())
                    .andExpect(cookie().maxAge("MFA_CHALLENGE", 0))
                    .andExpect(cookie().path("MFA_CHALLENGE", "/api/auth/mfa"));
        }
        verifyCode(pending, code).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/logout").with(csrf())).andExpect(cookie().maxAge("MFA_CHALLENGE", 0));
    }

    @ParameterizedTest @ValueSource(strings = {"disabled", "state", "version", "expired", "secret"})
    void rejectsStaleOrUnprovenChallenge(String reason) throws Exception {
        Cookie pending = pending();
        switch (reason) {
            case "disabled" -> { user.setEnabled(false); users.saveAndFlush(user); }
            case "state" -> { user.setEmailMfaEnabled(false); users.saveAndFlush(user); }
            case "version" -> { user.setSecurityVersion(1); users.saveAndFlush(user); }
            case "expired" -> {
                var challenge = challenges.findById(id(pending)).orElseThrow();
                challenge.setExpiresAt(Instant.now().minusSeconds(1)); challenges.saveAndFlush(challenge);
            }
            case "secret" -> pending = new Cookie("MFA_CHALLENGE", id(pending) + "." + crypto.newSecret());
        }
        verifyCode(pending, code).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MFA_CHALLENGE_INVALID"));
        mvc.perform(post("/api/auth/mfa/login/resend").with(csrf()).cookie(pending))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MFA_CHALLENGE_INVALID"));
        assertThat(countSessions()).isZero();
    }

    @Test void missingCookieIsDomainRejection() throws Exception {
        mvc.perform(post("/api/auth/mfa/login/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MFA_CHALLENGE_INVALID"));
    }

    @Test void rejectsWrongPurpose() throws Exception {
        var origin = sessionService.createSession(user);
        var delivered = delivery.start(user.getId(), ChallengePurpose.ENABLE, origin.session().getId());
        verifyCode(new Cookie("MFA_CHALLENGE", delivered.cookieValue()), code).andExpect(status().isBadRequest());
        assertThat(countSessions()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void passwordChangeAndResetInvalidatePendingLogin(boolean reset) throws Exception {
        Cookie pending = pending();
        if (reset) {
            var token = new PasswordResetToken();
            token.setUser(user); token.setTokenHash(DigestUtils.sha256Hex("reset-token"));
            token.setExpiresAt(Instant.now().plusSeconds(120)); resetTokens.saveAndFlush(token);
            resets.resetPassword("reset-token", "replacement-password");
        } else {
            passwords.change(user.getId(), new ChangePasswordRequest("secure-password", "replacement-password"));
        }
        verifyCode(pending, code).andExpect(status().isBadRequest());
        var updated = users.findById(user.getId()).orElseThrow();
        assertThat(updated.getSecurityVersion()).isEqualTo(1);
        assertThat(updated.isEmailMfaEnabled()).isTrue();
        assertThat(challenges.findById(id(pending)).orElseThrow().getInvalidatedAt()).isNotNull();
    }

    @Test void sessionFailureRollsBackConsumptionAndSessionInsert() throws Exception {
        Cookie pending = pending();
        doAnswer(invocation -> { invocation.callRealMethod(); throw new IllegalStateException("session failure"); })
                .when(sessionService).createSession(any());
        assertThatThrownBy(() -> mfa.verify(pending.getValue(), code)).isInstanceOf(IllegalStateException.class);
        assertThat(challenges.findById(id(pending)).orElseThrow().getConsumedAt()).isNull();
        assertThat(countSessions()).isZero();
        doCallRealMethod().when(sessionService).createSession(any());
        mfa.verify(pending.getValue(), code);
        assertThat(countSessions()).isEqualTo(1);
    }

    @Test void concurrentConfirmationsCreateOnlyOneSession() throws Exception {
        Cookie pending = pending();
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> confirm = () -> {
                gate.await();
                try { mfa.verify(pending.getValue(), code); return true; }
                catch (ChallengeException expected) { return false; }
            };
            var a = pool.submit(confirm); var b = pool.submit(confirm); gate.countDown();
            assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(countSessions()).isEqualTo(1);
    }

    @Test void newLoginRacingConfirmationCannotReusePreviousChallenge() throws Exception {
        Cookie previous = pending();
        String previousCode = code;
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var confirmation = pool.submit(() -> {
                gate.await();
                return verifyCode(previous, previousCode).andReturn().getResponse().getStatus();
            });
            var replacement = pool.submit(() -> { gate.await(); return pending(); });
            gate.countDown();
            int result = confirmation.get(10, TimeUnit.SECONDS);
            Cookie current = replacement.get(10, TimeUnit.SECONDS);
            assertThat(result).isIn(200, 400);
            assertThat(countSessions()).isEqualTo(result == 200 ? 1 : 0);
            verifyCode(previous, previousCode).andExpect(status().isBadRequest());
            verifyCode(current, code).andExpect(status().isOk());
            assertThat(countSessions()).isEqualTo(result == 200 ? 2 : 1);
        }
    }

    @Test void resendRacingConfirmationCannotAcceptPreviousGeneration() throws Exception {
        doReturn("000001", "000002").when(crypto).newCode();
        Cookie pending = pending();
        String previousCode = code;
        var challenge = challenges.findById(id(pending)).orElseThrow();
        challenge.setLastSentAt(Instant.now().minusSeconds(61));
        challenges.saveAndFlush(challenge);
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var confirmation = pool.submit(() -> {
                gate.await();
                return verifyCode(pending, previousCode).andReturn().getResponse().getStatus();
            });
            var resend = pool.submit(() -> {
                gate.await();
                return mvc.perform(post("/api/auth/mfa/login/resend").with(csrf()).cookie(pending))
                        .andReturn().getResponse().getStatus();
            });
            gate.countDown();
            int result = confirmation.get(10, TimeUnit.SECONDS);
            int resendResult = resend.get(10, TimeUnit.SECONDS);
            assertThat(result).isIn(200, 400);
            assertThat(resendResult).isEqualTo(result == 200 ? 400 : 202);
            assertThat(countSessions()).isEqualTo(result == 200 ? 1 : 0);
            if (result == 400) {
                verifyCode(pending, previousCode).andExpect(status().isBadRequest());
                verifyCode(pending, "000002").andExpect(status().isOk());
            } else {
                verifyCode(pending, previousCode).andExpect(status().isBadRequest());
            }
            assertThat(countSessions()).isEqualTo(1);
        }
    }

    @Test void passwordChangeRacingConfirmationLeavesNoRenewableSession() throws Exception {
        Cookie pending = pending();
        String originalCode = code;
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var confirmation = pool.submit(() -> {
                gate.await();
                return verifyCode(pending, originalCode).andReturn().getResponse().getStatus();
            });
            var change = pool.submit(() -> {
                gate.await();
                passwords.change(user.getId(), new ChangePasswordRequest("secure-password", "replacement-password"));
                return true;
            });
            gate.countDown();
            int result = confirmation.get(10, TimeUnit.SECONDS);
            assertThat(change.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(result).isIn(200, 400);
            assertThat(countSessions()).isEqualTo(result == 200 ? 1 : 0);
        }
        assertThat(sessions.findAll().stream().filter(s -> s.getUser().getId().equals(user.getId())))
                .allSatisfy(session -> assertThat(session.getRevokedAt()).isNotNull());
        verifyCode(pending, originalCode).andExpect(status().isBadRequest());
        var updated = users.findById(user.getId()).orElseThrow();
        assertThat(updated.isEmailMfaEnabled()).isTrue();
        assertThat(updated.getSecurityVersion()).isEqualTo(1);
    }

    private void assertJwtIdentity(MvcResult result) {
        var access = result.getResponse().getCookie("ACCESS_TOKEN");
        var refresh = result.getResponse().getCookie("REFRESH_TOKEN");
        assertThat(access).isNotNull();
        assertThat(refresh).isNotNull();
        var claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecurity().getJwt().getSecret())))
                .build().parseClaimsJws(access.getValue()).getBody();
        String sessionId = refresh.getValue().substring(0, refresh.getValue().indexOf('.'));
        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("sid", String.class)).isEqualTo(sessionId);
        assertThat(sessions.findById(sessionId).orElseThrow().getUser().getId()).isEqualTo(user.getId());
    }

    long countSessions() { return sessions.findAll().stream().filter(s -> s.getUser().getId().equals(user.getId())).count(); }
    String id(Cookie cookie) { return cookie.getValue().substring(0, 36); }
    Cookie pending() throws Exception { return login().andExpect(status().isAccepted()).andReturn().getResponse().getCookie("MFA_CHALLENGE"); }
    ResultActions login(Cookie... cookies) throws Exception {
        var request = post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"secure-password\"}");
        if (cookies.length > 0) request.cookie(cookies);
        return mvc.perform(request);
    }
    ResultActions verifyCode(Cookie pending, String value) throws Exception {
        return mvc.perform(post("/api/auth/mfa/login/verify").with(csrf()).cookie(pending)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + value + "\"}"));
    }
}
