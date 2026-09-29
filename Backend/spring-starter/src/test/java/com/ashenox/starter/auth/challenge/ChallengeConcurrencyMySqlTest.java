package com.ashenox.starter.auth.challenge;

import com.ashenox.starter.auth.challenge.model.ChallengeDeliveryState;
import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.auth.challenge.model.AuthRateLimitScope;
import com.ashenox.starter.auth.challenge.service.ChallengeException;
import com.ashenox.starter.auth.challenge.service.ChallengeService;
import com.ashenox.starter.auth.challenge.service.RateLimitService;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.model.User;
import com.ashenox.starter.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ChallengeConcurrencyMySqlTest {
    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Autowired private ChallengeService challenges;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void concurrentConfirmationsConsumeChallengeAtMostOnce() throws Exception {
        User user = user();
        var issued = challenges.start(user.getId(), ChallengePurpose.LOGIN, null);
        assertThat(challenges.markDelivery(issued.challenge().getId(), 1, ChallengeDeliveryState.SENT)).isTrue();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> verify(issued.cookieValue(), issued.code(), ready, go));
            var second = pool.submit(() -> verify(issued.cookieValue(), issued.code(), ready, go));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS) + second.get(10, TimeUnit.SECONDS)).isEqualTo(1);
        }
    }

    @Test
    void concurrentStartsLeaveOneCurrentChallengeAndCountBothIssues() throws Exception {
        User user = user();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> start(user.getId(), ready, go));
            var second = pool.submit(() -> start(user.getId(), ready, go));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("""
                select count(*) from auth_challenges
                where user_id = ? and purpose = 'LOGIN' and consumed_at is null and invalidated_at is null
                """, Integer.class, user.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select attempt_count from auth_rate_limits
                where scope = 'MFA_CODE_ISSUE' and subject_key = ?
                """, Integer.class, Long.toString(user.getId()))).isEqualTo(2);
    }

    @Test
    void separateLimiterInstancesShareTheSameDatabaseWindow() {
        String key = "shared-" + UUID.randomUUID();
        RateLimitService first = new RateLimitService(jdbc);
        RateLimitService second = new RateLimitService(jdbc);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(ignored -> first.lock(AuthRateLimitScope.MFA_CODE_FAILURE,
                key, Duration.ofMinutes(15), Instant.now()).increment());
        transaction.executeWithoutResult(ignored -> second.lock(AuthRateLimitScope.MFA_CODE_FAILURE,
                key, Duration.ofMinutes(15), Instant.now()).increment());
        assertThat(jdbc.queryForObject("""
                select attempt_count from auth_rate_limits
                where scope = 'MFA_CODE_FAILURE' and subject_key = ?
                """, Integer.class, key)).isEqualTo(2);
    }

    private int start(long userId, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        assertThat(go.await(5, TimeUnit.SECONDS)).isTrue();
        challenges.start(userId, ChallengePurpose.LOGIN, null);
        return 1;
    }

    private User user() {
        User user = new User();
        user.setEmail("race-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("$2a$10$1234567890123456789012345678901234567890123456789012");
        user.setRole(Role.USER);
        user.setEnabled(true);
        return users.saveAndFlush(user);
    }

    private int verify(String cookie, String code, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        assertThat(go.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            challenges.verifyAndConsume(cookie, ChallengePurpose.LOGIN, null, null, code);
            return 1;
        } catch (ChallengeException exception) {
            return 0;
        }
    }
}
