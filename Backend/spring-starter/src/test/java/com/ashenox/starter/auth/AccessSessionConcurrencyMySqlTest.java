package com.ashenox.starter.auth;

import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.security.error.InvalidRefreshTokenException;
import com.ashenox.starter.security.jwt.JWTUtil;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AccessSessionConcurrencyMySqlTest {
    @Container @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired AuthSessionService service;
    @Autowired JWTUtil jwt;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactionManager;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void revocationWinsAgainstRefreshAndSubsequentRequests(boolean revokeBeforeRefreshWrite) throws Exception {
        User user = users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash("hash").role(Role.USER).enabled(true).build());
        var issued = service.createSession(user);
        String sid = issued.session().getId();
        Cookie access = new Cookie("ACCESS_TOKEN", jwt.generateToken(user.getId(), sid));
        mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isOk());
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch continueRefresh = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var refresh = pool.submit(() -> {
                try {
                    return transaction.execute(ignored -> {
                        // Keep the actual managed version loaded before the competing commit.
                        sessions.findWithUserById(sid).orElseThrow();
                        loaded.countDown();
                        await(continueRefresh);
                        return service.rotate(issued.refreshToken());
                    });
                } catch (InvalidRefreshTokenException expectedConflict) {
                    return null;
                }
            });
            try {
                assertThat(loaded.await(10, TimeUnit.SECONDS)).isTrue();
                if (revokeBeforeRefreshWrite) service.revokeAllForUser(user.getId());
            } finally {
                continueRefresh.countDown();
            }
            var rotated = refresh.get(15, TimeUnit.SECONDS);
            if (revokeBeforeRefreshWrite) assertThat(rotated).isNull();
            else {
                assertThat(rotated).isNotNull();
                service.revokeAllForUser(user.getId());
                assertThatThrownBy(() -> service.rotate(rotated.refreshToken()))
                        .isInstanceOf(InvalidRefreshTokenException.class);
            }
        }
        assertThat(sessions.findById(sid).orElseThrow().getRevokedAt()).isNotNull();
        assertThatThrownBy(() -> service.rotate(issued.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
        // Each request begins after revocation has committed, without sharing the refresh transaction.
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/auth/me").cookie(access)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
