package com.ashenox.starter.user;

import com.ashenox.starter.auth.challenge.model.*;
import com.ashenox.starter.auth.challenge.port.MfaEmailSender;
import com.ashenox.starter.auth.challenge.repository.AuthChallengeRepository;
import com.ashenox.starter.auth.challenge.service.*;
import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.passwordreset.model.PasswordResetToken;
import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.service.MfaSettingsService;
import com.ashenox.starter.auth.service.impl.AuthService;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.auth.session.service.*;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.user.dto.*;
import com.ashenox.starter.user.model.*;
import com.ashenox.starter.user.repository.UserRepository;
import com.ashenox.starter.user.service.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

abstract class AdminUserEmailTestSupport {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired AdminUserService service;
    @Autowired AuthChallengeRepository challengeRepository;
    @Autowired ChallengeService challenges;
    @Autowired ChallengeDeliveryService delivery;
    @Autowired PasswordResetTokenRepository resets;
    @Autowired com.ashenox.starter.auth.service.PasswordResetService resetService;
    @Autowired PasswordEncoder encoder;
    @Autowired JWTUtil jwt;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MfaSettingsService mfa;
    @Autowired AuthService login;
    @MockitoSpyBean AuthSessionService sessions;
    @Autowired AuthSessionRepository sessionRepository;
    @MockitoBean MfaEmailSender sender;
    User admin;
    User target;
    AdminActor actor;
    IssuedSession origin;
    Cookie adminAccess;

    User user(Role role) {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@example.com")
                .passwordHash(encoder.encode("secure-password")).role(role).enabled(true).build());
    }
    @BeforeEach void setupEmail() {
        admin = user(Role.SUPER_ADMIN);
        actor = new AdminActor(admin.getId(), admin.getRole(), sessions.createSession(admin).session().getId());
        adminAccess = new Cookie(AuthCookieService.ACCESS_TOKEN, jwt.generateToken(admin.getId(), actor.sessionId()));
        target = user(Role.USER);
        origin = sessions.createSession(target);
    }
    @AfterEach void cleanupEmailChallenges() {
        if (target != null) jdbc.update("delete from auth_challenges where user_id=?", target.getId());
    }
    Cookie access(User user, IssuedSession session) {
        return new Cookie(AuthCookieService.ACCESS_TOKEN, jwt.generateToken(user.getId(), session.session().getId()));
    }
    User current() { return users.findById(target.getId()).orElseThrow(); }
    ResultActions change(Long id, String email, Cookie cookie) throws Exception {
        return mvc.perform(patch("/api/admin/users/{id}/email", id).cookie(cookie).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+email+"\"}"));
    }
    PasswordResetToken reset() {
        var token = new PasswordResetToken(); token.setUser(target);
        token.setTokenHash(org.apache.commons.codec.digest.DigestUtils.sha256Hex("reset-"+target.getId()));
        token.setExpiresAt(Instant.now().plusSeconds(600));
        return resets.saveAndFlush(token);
    }

    @Test void realChangeInvalidatesEverythingAndPreservesStateAndCounters() throws Exception {
        target.setPasswordChangeFailures(2); target.setPasswordChangeWindowStart(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        target=users.saveAndFlush(target);
        var pending=challenges.start(target.getId(), ChallengePurpose.ENABLE, origin.session().getId());
        var reset=reset();
        var counters=jdbc.queryForList("select scope, subject_key, attempt_count from auth_rate_limits where subject_key=?", target.getId().toString());
        assertThat(counters).isNotEmpty();
        var result=change(target.getId(), "  New."+target.getId()+"@Example.COM  ", adminAccess)
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("new."+target.getId()+"@example.com"))
                .andExpect(jsonPath("$.length()").value(7)).andReturn();
        var saved=current();
        assertThat(saved.getSecurityVersion()).isEqualTo(1);
        assertThat(saved.getPasswordHash()).isEqualTo(target.getPasswordHash());
        assertThat(saved.getRole()).isEqualTo(target.getRole());
        assertThat(saved.isEnabled()).isTrue(); assertThat(saved.isEmailMfaEnabled()).isFalse();
        assertThat(saved.getPasswordChangeFailures()).isEqualTo(2);
        assertThat(saved.getPasswordChangeWindowStart()).isEqualTo(target.getPasswordChangeWindowStart());
        assertThat(jdbc.queryForList("select scope, subject_key, attempt_count from auth_rate_limits where subject_key=?", target.getId().toString())).isEqualTo(counters);
        assertThat(challengeRepository.findById(pending.challenge().getId()).orElseThrow().getInvalidatedAt()).isNotNull();
        assertThat(resets.findById(reset.getId()).orElseThrow().getUsedAt()).isNotNull();
        assertThatThrownBy(()->resetService.validateToken("reset-"+target.getId()))
                .isInstanceOf(com.ashenox.starter.security.error.InvalidTokenException.class);
        assertThatThrownBy(()->challenges.verifyAndConsume(pending.cookieValue(),ChallengePurpose.ENABLE,
                target.getId(),origin.session().getId(),pending.code())).isInstanceOf(ChallengeException.class);
        assertThatThrownBy(()->sessions.rotate(origin.refreshToken())).isInstanceOf(com.ashenox.starter.security.error.InvalidRefreshTokenException.class);
        mvc.perform(get("/api/auth/me").cookie(access(target,origin))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+saved.getEmail()+"\",\"password\":\"secure-password\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\""+target.getEmail()+"\",\"password\":\"secure-password\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(result.getResponse().getContentAsString()).doesNotContain("passwordHash", "securityVersion");
    }

    @Test void noOpWithMfaDoesNotInvalidateOrUpdateAndAuditsNoOp() throws Exception {
        target.setEmailMfaEnabled(true); target=users.saveAndFlush(target);
        var pending=challenges.start(target.getId(), ChallengePurpose.LOGIN, null);
        var reset=reset(); var before=current();
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("OPERATIONS");
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            change(target.getId(), target.getEmail().toUpperCase(), adminAccess).andExpect(status().isOk());
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage()).contains("UPDATE_EMAIL", "NO_OP").doesNotContain(target.getEmail());
        } finally {logger.detachAppender(appender);appender.stop();}
        assertThat(current().getSecurityVersion()).isZero();
        assertThat(current().getUpdatedAt()).isEqualTo(before.getUpdatedAt());
        assertThat(sessionRepository.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNull();
        assertThat(challengeRepository.findById(pending.challenge().getId()).orElseThrow().getInvalidatedAt()).isNull();
        assertThat(resets.findById(reset.getId()).orElseThrow().getUsedAt()).isNull();
        change(target.getId(), "blocked@example.com", adminAccess).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_EMAIL_MFA_ENABLED"));
        assertThat(current().getEmail()).isEqualTo(target.getEmail());
    }

    @Test void duplicateMissingInvalidAndExtraPrivilegeFields() throws Exception {
        change(target.getId(),admin.getEmail(),adminAccess).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ADMIN_EMAIL_IN_USE"));
        change(Long.MAX_VALUE,"new@example.com",adminAccess).andExpect(status().isNotFound());
        for(String value:new String[]{"", "bad", "a".repeat(310)+"@example.com"})
            change(target.getId(),value,adminAccess).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/users/{id}/email",target.getId()).cookie(adminAccess).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\""+target.getEmail()+"\",\"role\":\"SUPER_ADMIN\",\"enabled\":false,\"mfaEnabled\":true}"))
                .andExpect(status().isOk());
        assertThat(current().getRole()).isEqualTo(Role.USER);
        assertThat(current().isEnabled()).isTrue(); assertThat(current().isEmailMfaEnabled()).isFalse();
    }

    @Test void requiresSuperAdminAuthenticationAndCsrf() throws Exception {
        String path="/api/admin/users/"+target.getId()+"/email";
        mvc.perform(patch(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.com\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch(path).cookie(adminAccess).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.com\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        for(Role role:new Role[]{Role.ADMIN,Role.USER}) {
            var user=user(role); var session=sessions.createSession(user);
            change(target.getId(),"forbidden@example.com",access(user,session)).andExpect(status().isForbidden());
        }
        assertThat(current().getEmail()).isEqualTo(target.getEmail());
    }

    @Test void selfChangeRevokesCurrentSessionAndInactiveTargetStaysInactive() throws Exception {
        change(admin.getId(),"self."+admin.getId()+"@example.com",adminAccess).andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").cookie(adminAccess)).andExpect(status().isUnauthorized());
        var session=sessions.createSession(users.findById(admin.getId()).orElseThrow());
        target.setEnabled(false);users.saveAndFlush(target);
        change(target.getId(),"inactive."+target.getId()+"@example.com",access(admin,session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        assertThat(current().isEnabled()).isFalse();
    }

    @Test void reusedEmailCannotAuthenticatePreviousIdentityJwt() throws Exception {
        var oldAccess=access(target,origin);
        change(target.getId(),"moved."+target.getId()+"@example.com",adminAccess).andExpect(status().isOk());
        var other=service.create(new CreateUserRequest(target.getEmail(),"secure-password",Role.USER),actor);
        assertThat(other.id()).isNotEqualTo(target.getId());
        mvc.perform(get("/api/auth/me").cookie(oldAccess)).andExpect(status().isUnauthorized());
    }

    @Test void failureAfterInvalidationRollsBackEmailVersionTokensAndAudit() {
        var pending=challenges.start(target.getId(),ChallengePurpose.ENABLE,origin.session().getId());
        var reset=reset();
        doAnswer(invocation->{invocation.callRealMethod();throw new IllegalStateException("injected after revocation");})
                .when(sessions).revokeAllForUser(target.getId());
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("OPERATIONS");
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();logger.addAppender(appender);
        try {
            assertThatThrownBy(()->service.updateEmail(target.getId(),new UpdateUserEmailRequest("rollback@example.com"),actor))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(appender.list).isEmpty();
        } finally {logger.detachAppender(appender);appender.stop();}
        assertThat(current().getEmail()).isEqualTo(target.getEmail()); assertThat(current().getSecurityVersion()).isZero();
        assertThat(challengeRepository.findById(pending.challenge().getId()).orElseThrow().getInvalidatedAt()).isNull();
        assertThat(resets.findById(reset.getId()).orElseThrow().getUsedAt()).isNull();
        assertThat(sessionRepository.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNull();
    }

    @Test void auditSuccessIsAfterCommitAndHttpRejectionsHaveOperationAndTarget() throws Exception {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger("OPERATIONS");
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();logger.addAppender(appender);
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx->{
                service.updateEmail(target.getId(),new UpdateUserEmailRequest("audit."+target.getId()+"@example.com"),actor);
                assertThat(appender.list).isEmpty();
            });
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage()).contains("UPDATE_EMAIL","SUCCESS",
                    "\"actorId\":"+actor.id()+",","\"targetId\":"+target.getId()+",")
                    .doesNotContain("@example.com",origin.refreshToken(),adminAccess.getValue());
            change(target.getId(),admin.getEmail(),adminAccess).andExpect(status().isConflict());
            assertThat(appender.list).hasSize(2);
            assertThat(appender.list.getLast().getFormattedMessage()).contains("UPDATE_EMAIL","REJECTED","\"targetId\":"+target.getId()+",");
            mvc.perform(patch("/api/admin/users/email").cookie(adminAccess).with(csrf()))
                    .andExpect(status().isMethodNotAllowed());
        } finally {logger.detachAppender(appender);appender.stop();}
    }
}
