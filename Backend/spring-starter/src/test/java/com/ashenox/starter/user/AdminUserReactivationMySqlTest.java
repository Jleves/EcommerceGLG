package com.ashenox.starter.user;

import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.security.error.InvalidTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class AdminUserReactivationMySqlTest extends AdminUserReactivationTest {
    @Container @ServiceConnection static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test void concurrentDeactivationAndReactivationDoNotReviveOldCredentials() throws Exception {
        var oldSession = origin;
        var pending = challenges.start(target.getId(), ChallengePurpose.LOGIN, null);
        var reset = reset();
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var deactivate = pool.submit(() -> {
                start.await();
                service.deactivate(target.getId(), actor);
                return true;
            });
            var reactivate = pool.submit(() -> {
                start.await();
                service.reactivate(target.getId(), actor);
                return true;
            });
            start.countDown();
            assertThat(deactivate.get(20, TimeUnit.SECONDS)).isTrue();
            assertThat(reactivate.get(20, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(current().getSecurityVersion()).isEqualTo(1);
        assertThat(sessionRepository.findById(oldSession.session().getId()).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(challengeRepository.findById(pending.challenge().getId()).orElseThrow().getInvalidatedAt()).isNotNull();
        assertThat(resets.findById(reset.getId()).orElseThrow().getUsedAt()).isNotNull();
        assertThatThrownBy(() -> resetService.validateToken("reset-" + target.getId()))
                .isInstanceOf(InvalidTokenException.class);
    }
}
