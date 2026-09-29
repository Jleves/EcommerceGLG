package com.ashenox.starter.user;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import com.ashenox.starter.user.model.Role;
import com.ashenox.starter.user.service.AdminActor;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminUserDeactivationMySqlTest extends AdminUserDeactivationTest {
    @Container @ServiceConnection static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test void mutualDeactivationRevalidatesSecondActor() throws Exception {
        var other = user(Role.SUPER_ADMIN);
        var otherActor = new AdminActor(other.getId(), Role.SUPER_ADMIN,
                sessions.createSession(other).session().getId());
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = pool.submit(() -> attempt(start, other.getId(), actor));
            Future<Boolean> second = pool.submit(() -> attempt(start, admin.getId(), otherActor));
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS) ^ second.get(20, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(users.findById(admin.getId()).orElseThrow().isEnabled()
                || users.findById(other.getId()).orElseThrow().isEnabled()).isTrue();
    }

    private boolean attempt(CountDownLatch start, Long targetId, AdminActor acting) throws Exception {
        start.await();
        try {
            service.deactivate(targetId, acting);
            return true;
        } catch (org.springframework.security.access.AccessDeniedException exception) {
            return false;
        }
    }
}
