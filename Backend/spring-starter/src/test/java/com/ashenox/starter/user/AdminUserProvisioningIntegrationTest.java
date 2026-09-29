package com.ashenox.starter.user;

import com.ashenox.starter.auth.cookie.AuthCookieService;
import com.ashenox.starter.auth.passwordreset.repository.PasswordResetTokenRepository;
import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminUserProvisioningIntegrationTest {

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

    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired private com.ashenox.starter.user.service.AdminUserService adminUserService;
    @Autowired private com.ashenox.starter.auth.session.service.AuthSessionService authSessions;

    @Test
    void parallelCommitsKeepCapturedActorAndRequestIdAfterRequestContextChanges() throws Exception {
        saveUser("second-super@example.com", Role.SUPER_ADMIN);
        var actors = java.util.List.of(userRepository.findByEmail("super@example.com").orElseThrow().getId(),
                userRepository.findByEmail("second-super@example.com").orElseThrow().getId());
        var sessions = new java.util.HashMap<Long, String>();
        for (Long id : actors) sessions.put(id, authSessions.createSession(userRepository.findById(id).orElseThrow()).session().getId());
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("OPERATIONS");
        var messages = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>() {
            @Override protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                messages.add(event.getFormattedMessage());
            }
        };
        appender.start();
        logger.addAppender(appender);
        var barrier = new java.util.concurrent.CyclicBarrier(2);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (Long actorId : actors) futures.add(pool.submit(() -> {
                var request = new org.springframework.mock.web.MockHttpServletRequest();
                request.setAttribute(com.ashenox.starter.log.filter.RequestLoggingFilter.REQUEST_ID_ATTRIBUTE, "request-" + actorId);
                org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
                        new org.springframework.web.context.request.ServletRequestAttributes(request));
                try {
                    // T5 serializes writers until commit: rendezvous before acquiring the singleton.
                    try { barrier.await(10, java.util.concurrent.TimeUnit.SECONDS); }
                    catch (Exception e) { throw new IllegalStateException(e); }
                    new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                        adminUserService.create(new com.ashenox.starter.user.dto.CreateUserRequest(
                                        "parallel-" + actorId + "@example.com", "initial-password", Role.USER),
                                new com.ashenox.starter.user.service.AdminActor(actorId, Role.SUPER_ADMIN, sessions.get(actorId)));
                        request.setAttribute(com.ashenox.starter.log.filter.RequestLoggingFilter.REQUEST_ID_ATTRIBUTE, "changed");
                        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
                    });
                } finally { org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes(); }
            }));
            for (var future : futures) future.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(messages).hasSize(2);
            for (Long actor : actors) {
                Long target = userRepository.findByEmail("parallel-" + actor + "@example.com").orElseThrow().getId();
                assertThat(messages).anySatisfy(message -> assertThat(message).contains(
                        "\"actorId\":" + actor + ",", "\"targetId\":" + target + ",", "\"requestId\":\"request-" + actor + "\""));
            }
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test
    void auditFollowsCommitAndRollbackAndLoggingFailureDoesNotUndoCreation() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("OPERATIONS");
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        var user = userRepository.findByEmail("super@example.com").orElseThrow();
        var actor = new com.ashenox.starter.user.service.AdminActor(
                user.getId(), Role.SUPER_ADMIN, authSessions.createSession(user).session().getId());
        try {
            tx.executeWithoutResult(status -> {
                adminUserService.create(new com.ashenox.starter.user.dto.CreateUserRequest(
                        "rollback@example.com", "secret-password-sentinel", Role.USER), actor);
                assertThat(appender.list).isEmpty();
                status.setRollbackOnly();
            });
            assertThat(appender.list).isEmpty();
            assertThat(userRepository.existsByEmail("rollback@example.com")).isFalse();
            var created = tx.execute(status -> {
                var result = adminUserService.create(new com.ashenox.starter.user.dto.CreateUserRequest(
                        "commit@example.com", "secret-password-sentinel", Role.USER), actor);
                assertThat(appender.list).isEmpty();
                return result;
            });
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage())
                    .contains("\"actorId\":" + actor.id(), "\"targetId\":" + created.id(), "\"result\":\"SUCCESS\"")
                    .doesNotContain("secret-password-sentinel", "commit@example.com", "sid", "passwordHash");
            var broken = org.mockito.Mockito.mock(ch.qos.logback.core.Appender.class);
            org.mockito.Mockito.doThrow(new IllegalStateException("audit unavailable"))
                    .when(broken).doAppend(org.mockito.ArgumentMatchers.any());
            logger.addAppender(broken);
            try {
                adminUserService.create(new com.ashenox.starter.user.dto.CreateUserRequest(
                        "logging-failure@example.com", "initial-password", Role.USER), actor);
                assertThat(userRepository.existsByEmail("logging-failure@example.com")).isTrue();
            } finally { logger.detachAppender(broken); }
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test
    void auditsHttpSuccessValidationConflictAndPreControllerDenialsWithoutSecrets() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("OPERATIONS");
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var anonymous = mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized()).andReturn();
            assertThat(appender.list).hasSize(1);
            assertThat(appender.list.getFirst().getFormattedMessage()).contains("\"actorId\":null", "REJECTED",
                    anonymous.getResponse().getHeader("X-Request-Id"));
            var admin = login("admin@example.com");
            createUser(admin, "forbidden@example.com", "USER").andExpect(status().isForbidden());
            assertThat(appender.list.getLast().getFormattedMessage()).contains("\"actorId\":" +
                    userRepository.findByEmail("admin@example.com").orElseThrow().getId());
            var auth = login("super@example.com");
            mockMvc.perform(post("/api/admin/users").cookie(auth.access())).andExpect(status().isForbidden());
            createUser(auth, "invalid", "USER").andExpect(status().isBadRequest());
            createUser(auth, "admin@example.com", "USER").andExpect(status().isConflict());
            var success = createUser(auth, "audit@example.com", "USER").andExpect(status().isCreated()).andReturn();
            assertThat(appender.list).hasSize(6);
            assertThat(appender.list.getLast().getFormattedMessage()).contains("SUCCESS",
                    success.getResponse().getHeader("X-Request-Id"));
            mockMvc.perform(get("/api/admin/users").cookie(auth.access())).andExpect(status().isOk());
            assertThat(appender.list).hasSize(6);
            for (var event : appender.list) assertThat(event.getFormattedMessage())
                    .doesNotContain("initial-password", "@example.com", auth.access().getValue(), auth.csrf().getValue());
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @BeforeEach
    void setUp() {
        resetTokenRepository.deleteAll();
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        saveUser("super@example.com", Role.SUPER_ADMIN);
        saveUser("admin@example.com", Role.ADMIN);
        saveUser("user@example.com", Role.USER);
    }

    @Test
    void superAdminCreatesNormalizedSuperAdminWithoutExposingPassword() throws Exception {
        AuthCookies auth = login("super@example.com");

        mockMvc.perform(post("/api/admin/users")
                        .cookie(auth.csrf(), auth.access())
                        .header("X-XSRF-TOKEN", auth.csrf().getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "New.Admin@Example.COM",
                                  "password": "initial-password",
                                  "role": "SUPER_ADMIN"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new.admin@example.com"))
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User created = userRepository.findByEmail("new.admin@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("initial-password", created.getPasswordHash())).isTrue();
    }

    @Test
    void superAdminCreatesEveryRoleAndNewAccountCanLogin() throws Exception {
        AuthCookies auth = login("super@example.com");
        for (Role role : Role.values()) {
            String email = "created-" + role.name().toLowerCase() + "@example.com";
            createUser(auth, email, role.name()).andExpect(status().isCreated());
            Cookie csrf = requestCsrfCookie();
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email + "\",\"password\":\"initial-password\"}"))
                    .andExpect(status().isOk()).andReturn();
            mockMvc.perform(get("/api/auth/me")
                            .cookie(result.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.rol").value(role.name()));
        }
    }

    @Test
    void adminAndUserCannotCreateAnyRole() throws Exception {
        for (String actor : new String[]{"admin@example.com", "user@example.com"}) {
            AuthCookies auth = login(actor);
            for (Role role : Role.values()) {
                createUser(auth, "forbidden@example.com", role.name())
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
                assertThat(userRepository.existsByEmail("forbidden@example.com")).isFalse();
            }
        }
    }

    @Test
    void rejectsInvalidInputWithoutPersistingUsers() throws Exception {
        AuthCookies auth = login("super@example.com");
        for (String body : new String[]{
                userBody("invalid-email", "USER"),
                "{\"email\":\"invalid@example.com\",\"password\":\"short\",\"role\":\"USER\"}",
                "{\"email\":\"invalid@example.com\",\"password\":\"initial-password\"}",
                userBody("invalid@example.com", "UNKNOWN")}) {
            mockMvc.perform(post("/api/admin/users").cookie(auth.csrf(), auth.access())
                            .header("X-XSRF-TOKEN", auth.csrf().getValue())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
            assertThat(userRepository.count()).isEqualTo(3);
        }
    }
    @Test
    void rejectsRegularUserAndDuplicateEmail() throws Exception {
        AuthCookies regularUser = login("user@example.com");
        createUser(regularUser, "blocked@example.com", "USER")
                .andExpect(status().isForbidden());

        AuthCookies superAdmin = login("super@example.com");
        createUser(superAdmin, "ADMIN@example.com", "USER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    @Test
    void requiresAuthenticationAndValidCsrf() throws Exception {
        Cookie csrf = requestCsrfCookie();
        mockMvc.perform(post("/api/admin/users")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("anonymous@example.com", "USER")))
                .andExpect(status().isUnauthorized());

        AuthCookies auth = login("super@example.com");
        mockMvc.perform(post("/api/admin/users")
                        .cookie(auth.access())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userBody("without-csrf@example.com", "USER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
    }

    @Test
    void readsDefaultsMultiplePagesAndInactiveMfaDetailWithoutSecrets() throws Exception {
        AuthCookies auth = login("super@example.com");
        User inactive = userRepository.findByEmail("user@example.com").orElseThrow();
        inactive.setEnabled(false);
        inactive.setEmailMfaEnabled(true);
        userRepository.saveAndFlush(inactive);
        var ids = userRepository.findAll(org.springframework.data.domain.Sort.by("id")).stream().map(User::getId).toList();
        mockMvc.perform(get("/api/admin/users").cookie(auth.access()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1)).andExpect(jsonPath("$.content.length()").value(3));
        for (int page = 0; page < 3; page++) {
            mockMvc.perform(get("/api/admin/users").param("page", Integer.toString(page)).param("size", "1")
                            .param("sort", "email,desc").cookie(auth.access()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(ids.get(page)))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.content[0].length()").value(7))
                    .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist());
        }
        mockMvc.perform(get("/api/admin/users/" + inactive.getId()).cookie(auth.access()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.mfaEnabled").value(true)).andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.securityVersion").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
        mockMvc.perform(get("/api/admin/users").param("page", "3").param("size", "1").cookie(auth.access()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/api/admin/users").param("size", "100").cookie(auth.access()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.size").value(100));
        mockMvc.perform(get("/api/admin/users").param("page", "2147483647").cookie(auth.access()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(2147483647)).andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void rejectsInvalidPaginationAndReturnsActualMissingUserError() throws Exception {
        AuthCookies auth = login("super@example.com");
        for (String query : new String[]{"page=-1", "size=0", "size=-1", "size=101", "page=abc", "size=2147483648"}) {
            mockMvc.perform(get("/api/admin/users?" + query).cookie(auth.access())).andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/api/admin/users/9223372036854775807").cookie(auth.access()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("El usuario solicitado no existe."));
    }

    @Test
    void restrictsBothExistingReadEndpointsToSuperAdmin() throws Exception {
        Long id = userRepository.findByEmail("user@example.com").orElseThrow().getId();
        for (String path : new String[]{"/api/admin/users", "/api/admin/users/" + id}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
            for (String email : new String[]{"admin@example.com", "user@example.com"}) {
                AuthCookies auth = login(email);
                mockMvc.perform(get(path).cookie(auth.access())).andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
                mockMvc.perform(get("/api/auth/me").cookie(auth.access())).andExpect(status().isOk());
            }
        }
    }

    private org.springframework.test.web.servlet.ResultActions createUser(
            AuthCookies auth, String email, String role) throws Exception {
        return mockMvc.perform(post("/api/admin/users")
                .cookie(auth.csrf(), auth.access())
                .header("X-XSRF-TOKEN", auth.csrf().getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(userBody(email, role)));
    }

    private String userBody(String email, String role) {
        return """
                {"email":"%s","password":"initial-password","role":"%s"}
                """.formatted(email, role);
    }

    private AuthCookies login(String email) throws Exception {
        Cookie csrf = requestCsrfCookie();
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie access = result.getResponse().getCookie(AuthCookieService.ACCESS_TOKEN);
        assertThat(access).isNotNull();
        return new AuthCookies(csrf, access);
    }

    private Cookie requestCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie csrf = result.getResponse().getCookie(AuthCookieService.XSRF_TOKEN);
        assertThat(csrf).isNotNull();
        return csrf;
    }

    private void saveUser(String email, Role role) {
        userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(role)
                .enabled(true)
                .build());
    }

    private record AuthCookies(Cookie csrf, Cookie access) {
    }
}
