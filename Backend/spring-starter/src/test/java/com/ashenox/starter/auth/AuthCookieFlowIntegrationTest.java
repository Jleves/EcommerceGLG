package com.ashenox.starter.auth;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.passwordreset.model.PasswordResetToken;
import com.ashenox.starter.auth.session.model.AuthSession;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.shared.config.AppProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthCookieFlowIntegrationTest {

    private static final String EMAIL = "cookie-user@example.com";
    private static final String PASSWORD = "secure-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthSessionRepository sessionRepository;

    @Autowired
    private PasswordResetTokenRepository resetTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthSessionService authSessionService;

    @Autowired
    private AppProperties properties;

    @Autowired
    private com.ashenox.starter.security.jwt.JWTUtil jwt;

    @BeforeEach
    void setUp() {
        resetTokenRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.saveAndFlush(User.builder()
                .email(EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(Role.USER)
                .enabled(true)
                .build());
    }

    @Test
    void completesLoginMeRefreshAndLogoutUsingOnlyCookies() throws Exception {
        Cookie csrf = requestCsrfCookie();

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cookie-user@example.com","password":"secure-password"}
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists(AuthCookieService.ACCESS_TOKEN))
                .andExpect(cookie().exists(AuthCookieService.REFRESH_TOKEN))
                .andExpect(cookie().httpOnly(AuthCookieService.ACCESS_TOKEN, true))
                .andExpect(cookie().httpOnly(AuthCookieService.REFRESH_TOKEN, true))
                .andExpect(cookie().secure(AuthCookieService.ACCESS_TOKEN, false))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        Cookie access = login.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN);
        Cookie refresh = login.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN);
        assertThat(access).isNotNull();
        assertThat(refresh).isNotNull();
        assertJwtIdentity(login, userRepository.findByEmail(EMAIL).orElseThrow().getId());

        mockMvc.perform(get("/api/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));

        MvcResult rotated = mockMvc.perform(post("/api/auth/refresh")
                        .cookie(csrf, refresh)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(AuthCookieService.ACCESS_TOKEN))
                .andExpect(cookie().exists(AuthCookieService.REFRESH_TOKEN))
                .andReturn();

        Cookie rotatedRefresh = rotated.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN);
        assertThat(rotatedRefresh).isNotNull();
        assertThat(rotatedRefresh.getValue()).isNotEqualTo(refresh.getValue());
        assertJwtIdentity(rotated, userRepository.findByEmail(EMAIL).orElseThrow().getId());

        String sessionId = rotatedRefresh.getValue().substring(0, rotatedRefresh.getValue().indexOf('.'));
        mockMvc.perform(post("/api/auth/logout")
                        .cookie(csrf, rotatedRefresh)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthCookieService.ACCESS_TOKEN, 0))
                .andExpect(cookie().maxAge(AuthCookieService.REFRESH_TOKEN, 0))
                .andExpect(cookie().maxAge(AuthCookieService.XSRF_TOKEN, 0));

        AuthSession revoked = sessionRepository.findById(sessionId).orElseThrow();
        assertThat(revoked.getRevokedAt()).isNotNull();
        mockMvc.perform(get("/api/auth/me").cookie(access))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        mockMvc.perform(get("/api/auth/me").cookie(rotated.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(csrf, rotatedRefresh)
                        .header("X-XSRF-TOKEN", csrf.getValue())).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMutationWithoutCsrfUsingSharedErrorContract() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cookie-user@example.com","password":"secure-password"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void reusingRotatedRefreshTokenPersistsSessionRevocation() throws Exception {
        Cookie csrf = requestCsrfCookie();
        MvcResult login = performLogin(csrf);
        Cookie originalRefresh = login.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN);
        assertThat(originalRefresh).isNotNull();

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(csrf, originalRefresh)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .cookie(csrf, originalRefresh)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_TOKEN_INVALID"));

        String sessionId = originalRefresh.getValue().substring(0, originalRefresh.getValue().indexOf('.'));
        assertThat(sessionRepository.findById(sessionId).orElseThrow().getRevokedAt()).isNotNull();
        mockMvc.perform(get("/api/auth/me").cookie(login.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotAcceptBearerAsAlternativeAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer ignored"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void logoutWithoutSessionIsIdempotentAndStillClearsCookies() throws Exception {
        Cookie csrf = requestCsrfCookie();

        mockMvc.perform(post("/api/auth/logout")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthCookieService.ACCESS_TOKEN, 0))
                .andExpect(cookie().maxAge(AuthCookieService.REFRESH_TOKEN, 0));
    }

    @Test
    void passwordResetConsumesTokenAndRevokesEveryUserSession() throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        String firstSessionId = authSessionService.createSession(user).session().getId();
        String secondSessionId = authSessionService.createSession(user).session().getId();
        String plainResetToken = "plain-reset-token";

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(DigestUtils.sha256Hex(plainResetToken));
        resetToken.setExpiresAt(Instant.now().plusSeconds(600));
        resetTokenRepository.saveAndFlush(resetToken);
        Cookie csrf = requestCsrfCookie();

        mockMvc.perform(post("/api/auth/reset-password")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"plain-reset-token","newPassword":"new-secure-password"}
                                """))
                .andExpect(status().isNoContent());

        assertThat(passwordEncoder.matches("new-secure-password",
                userRepository.findById(user.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(resetTokenRepository.findByTokenHash(DigestUtils.sha256Hex(plainResetToken))
                .orElseThrow().getUsedAt()).isNotNull();
        assertThat(sessionRepository.findById(firstSessionId).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(sessionRepository.findById(secondSessionId).orElseThrow().getRevokedAt()).isNotNull();
        for (String sid : new String[]{firstSessionId, secondSessionId}) {
            mockMvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookieService.ACCESS_TOKEN,
                            jwt.generateToken(user.getId(), sid))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "blank", "numeric", "missing", "foreign", "revoked", "expired"})
    void rejectsSignedAccessWithInvalidSession(String scenario) throws Exception {
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        User owner = scenario.equals("foreign") ? userRepository.saveAndFlush(User.builder()
                .email("other@example.com").passwordHash("hash").role(Role.USER).enabled(true).build()) : user;
        var issued = authSessionService.createSession(owner);
        AuthSession session = issued.session();
        Object sid = session.getId();
        switch (scenario) {
            case "absent" -> sid = null;
            case "blank" -> sid = " ";
            case "numeric" -> sid = 42;
            case "missing" -> sid = "nonexistent";
            case "revoked" -> authSessionService.revokeAllForUser(user.getId());
            case "expired" -> {
                session.setExpiresAt(Instant.now().minusSeconds(1));
                sessionRepository.saveAndFlush(session);
            }
        }
        String token = Jwts.builder().setSubject(user.getId().toString()).claim("sid", sid)
                .setExpiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecurity().getJwt().getSecret())))
                .compact();
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookieService.ACCESS_TOKEN, token)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void committedRevocationRejectsAccessAndRefreshWithoutAffectingAnotherUser() throws Exception {
        Cookie csrf = requestCsrfCookie();
        var login = performLogin(csrf);
        User user = userRepository.findByEmail(EMAIL).orElseThrow();
        User other = userRepository.saveAndFlush(User.builder().email("unaffected@example.com")
                .passwordHash("hash").role(Role.USER).enabled(true).build());
        var otherSession = authSessionService.createSession(other);
        assertThat(authSessionService.revokeAllForUser(user.getId())).isEqualTo(1);
        mockMvc.perform(get("/api/auth/me").cookie(login.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        mockMvc.perform(post("/api/auth/refresh").cookie(csrf, login.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN))
                        .header("X-XSRF-TOKEN", csrf.getValue())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookieService.ACCESS_TOKEN,
                        jwt.generateToken(other.getId(), otherSession.session().getId()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(other.getId()));
    }

    @Test
    void emailReassignmentCannotChangeIdentityOfExistingAccessOrRefresh() throws Exception {
        Cookie csrf = requestCsrfCookie();
        MvcResult login = performLogin(csrf);
        Cookie access = login.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN);
        Cookie refresh = login.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN);
        Cookie legacyAccess = signedAccess(EMAIL);
        User original = userRepository.findByEmail(EMAIL).orElseThrow();

        // Fixture-only email reassignment: administrative mutation/invalidation belongs to T6.
        original.setEmail("changed@example.com");
        userRepository.saveAndFlush(original);
        User replacement = userRepository.saveAndFlush(User.builder().email(EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(Role.SUPER_ADMIN).enabled(true).build());
        assertThat(replacement.getId()).isNotEqualTo(original.getId());

        mockMvc.perform(get("/api/auth/me").cookie(access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(original.getId()))
                .andExpect(jsonPath("$.email").value("changed@example.com"))
                .andExpect(jsonPath("$.rol").value("USER"));
        mockMvc.perform(get("/api/auth/me").cookie(legacyAccess))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));

        MvcResult rotated = mockMvc.perform(post("/api/auth/refresh").cookie(csrf, refresh)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent()).andReturn();
        assertJwtIdentity(rotated, original.getId());
        mockMvc.perform(get("/api/auth/me").cookie(rotated.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(original.getId()))
                .andExpect(jsonPath("$.email").value("changed@example.com"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectsAccessWhenOriginalAccountIsDisabledOrDeleted(boolean deleted) throws Exception {
        Cookie access = performLogin(requestCsrfCookie()).getResponse().getCookie(AuthCookieService.ACCESS_TOKEN);
        User original = userRepository.findByEmail(EMAIL).orElseThrow();
        if (deleted) {
            sessionRepository.deleteAll();
            userRepository.delete(original);
        } else {
            original.setEnabled(false);
            userRepository.saveAndFlush(original);
        }

        mockMvc.perform(get("/api/auth/me").cookie(access))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {EMAIL, "0", "-1", "+1", "01", " 1", "1 ", "1.0", "9223372036854775808"})
    void rejectsSignedMissingMalformedOrLegacySubject(String subject) throws Exception {
        mockMvc.perform(get("/api/auth/me").cookie(signedAccess(subject)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void loginStillAcceptsNormalizedEmailAndReturnsIdBasedAccess() throws Exception {
        Cookie csrf = requestCsrfCookie();
        MvcResult login = mockMvc.perform(post("/api/auth/login").cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"COOKIE-USER@EXAMPLE.COM","password":"secure-password"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(EMAIL)).andReturn();
        assertJwtIdentity(login, userRepository.findByEmail(EMAIL).orElseThrow().getId());
    }

    private Cookie signedAccess(String subject) {
        String token = Jwts.builder().setSubject(subject).claim("sid", "test-session")
                .setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecurity().getJwt().getSecret())))
                .compact();
        return new Cookie(AuthCookieService.ACCESS_TOKEN, token);
    }

    @Test
    void rejectsSignedNumericJsonSubjectEvenWhenAccountExists() throws Exception {
        Long userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
        String token = Jwts.builder().claim("sub", userId).claim("sid", "test-session")
                .setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecurity().getJwt().getSecret())))
                .compact();
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookieService.ACCESS_TOKEN, token)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    private void assertJwtIdentity(MvcResult result, Long userId) {
        Cookie access = result.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN);
        Cookie refresh = result.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN);
        assertThat(access).isNotNull();
        assertThat(refresh).isNotNull();
        var claims = Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.getSecurity().getJwt().getSecret())))
                .build().parseClaimsJws(access.getValue()).getBody();
        String sessionId = refresh.getValue().substring(0, refresh.getValue().indexOf('.'));
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("sid", String.class)).isEqualTo(sessionId);
        assertThat(sessionRepository.findById(sessionId).orElseThrow().getUser().getId()).isEqualTo(userId);
    }

    private Cookie requestCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(AuthCookieService.XSRF_TOKEN))
                .andExpect(cookie().httpOnly(AuthCookieService.XSRF_TOKEN, false))
                .andReturn();
        Cookie csrf = result.getResponse().getCookie(AuthCookieService.XSRF_TOKEN);
        assertThat(csrf).isNotNull();
        return csrf;
    }

    private MvcResult performLogin(Cookie csrf) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"cookie-user@example.com","password":"secure-password"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
    }
}
