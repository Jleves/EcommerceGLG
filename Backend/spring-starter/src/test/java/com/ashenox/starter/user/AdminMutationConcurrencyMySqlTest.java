package com.ashenox.starter.user;

import com.ashenox.starter.auth.session.repository.AuthSessionRepository;
import com.ashenox.starter.auth.session.service.AuthSessionService;
import com.ashenox.starter.user.dto.CreateUserRequest;
import com.ashenox.starter.user.model.*;
import com.ashenox.starter.user.repository.*;
import com.ashenox.starter.user.service.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.mysql.MySQLContainer;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AdminMutationConcurrencyMySqlTest {
    @Container @ServiceConnection static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired AuthSessionService sessionService;
    @Autowired AdminMutationGuard guard;
    @Autowired AdminMutationLockRepository coordination;
    @Autowired AdminUserService service;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PasswordEncoder encoder;
    @Autowired com.ashenox.starter.auth.service.impl.AuthService login;
    @Autowired com.ashenox.starter.auth.service.MfaSettingsService mfa;
    @Autowired com.ashenox.starter.auth.passwordchange.PasswordChangeService passwords;

    private User user(Role role) {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.com")
                .passwordHash(encoder.encode("initial-password")).role(role).enabled(true).build());
    }

    private AdminActor actor(User user) {
        return new AdminActor(user.getId(), user.getRole(), sessionService.createSession(user).session().getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"disabled", "role", "revoked", "expired"})
    void waitingWriterRejectsStaleActorOrSessionEvenWithPreloadedJpaState(String change) throws Exception {
        var holder = actor(user(Role.SUPER_ADMIN));
        var waiting = actor(user(Role.SUPER_ADMIN));
        var started = new CountDownLatch(1);
        var result = new AtomicReference<Future<?>>();
        var email = UUID.randomUUID() + "@example.com";
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var locked = guard.lock(holder, waiting.id());
                result.set(pool.submit(() -> {
                    assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(worker -> {
                        // Establish both a REPEATABLE READ snapshot and first-level JPA cache before waiting.
                        users.findById(waiting.id()).orElseThrow();
                        sessions.findById(waiting.sessionId()).orElseThrow();
                        users.count();
                        started.countDown();
                        service.create(new CreateUserRequest(email, "initial-password", Role.USER), waiting);
                    })).isInstanceOf(AccessDeniedException.class);
                }));
                await(started);
                awaitDatabaseWait("admin_mutation_lock");
                switch (change) {
                    case "disabled" -> locked.target().setEnabled(false);
                    case "role" -> locked.target().setRole(Role.ADMIN);
                    case "revoked" -> sessions.lockByIdAndUserId(waiting.sessionId(), waiting.id())
                            .orElseThrow().setRevokedAt(Instant.now());
                    case "expired" -> sessions.lockByIdAndUserId(waiting.sessionId(), waiting.id())
                            .orElseThrow().setExpiresAt(Instant.now().minusSeconds(1));
                    default -> throw new AssertionError(change);
                }
            });
            result.get().get(15, TimeUnit.SECONDS);
        }
        assertThat(users.existsByEmail(email)).isFalse();
    }

    @Test void separateGuardInstancesShareCoordinationAndCountCurrentCommittedMembership() throws Exception {
        var holder = actor(user(Role.SUPER_ADMIN));
        var target = user(Role.SUPER_ADMIN);
        var otherGuard = new AdminMutationGuard(coordination, users, sessions, em);
        var started = new CountDownLatch(1);
        var result = new AtomicReference<Future<Long>>();
        long before = jdbc.queryForObject("select count(*) from users where role='SUPER_ADMIN' and enabled=true", Long.class);
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var locked = guard.lock(holder, target.getId());
                result.set(pool.submit(() -> new TransactionTemplate(transactions).execute(worker -> {
                    users.findAll(); // intentionally cache target and snapshot before the competing commit
                    started.countDown();
                    return otherGuard.lock(holder, target.getId()).activeSuperAdmins();
                })));
                await(started);
                awaitDatabaseWait("admin_mutation_lock");
                locked.target().setEnabled(false);
            });
            assertThat(result.get().get(15, TimeUnit.SECONDS)).isEqualTo(before - 1);
        }
    }

    @Test void rollbackReleasesSingletonAndCreationSucceedsOnAnotherConnection() throws Exception {
        var actor = actor(user(Role.SUPER_ADMIN));
        var rolledBack = UUID.randomUUID() + "@example.com";
        var committed = UUID.randomUUID() + "@example.com";
        var result = new AtomicReference<Future<?>>();
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                service.create(new CreateUserRequest(rolledBack, "initial-password", Role.USER), actor);
                result.set(pool.submit(() -> service.create(new CreateUserRequest(committed, "initial-password", Role.USER), actor)));
                awaitDatabaseWait("admin_mutation_lock");
                status.setRollbackOnly();
            });
            result.get().get(15, TimeUnit.SECONDS);
        }
        assertThat(users.existsByEmail(rolledBack)).isFalse();
        assertThat(users.existsByEmail(committed)).isTrue();
        assertThat(jdbc.queryForList("select id from admin_mutation_lock", Integer.class)).containsExactly(1);
    }

    @Test void guardRequiresTransactionAndMissingSingletonFailsClosed() {
        var actor = actor(user(Role.SUPER_ADMIN));
        assertThatThrownBy(() -> guard.lock(actor, null))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.update("delete from admin_mutation_lock where id=1");
            assertThatThrownBy(() -> guard.lock(actor, null))
                    .isInstanceOf(org.springframework.dao.EmptyResultDataAccessException.class);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForList("select id from admin_mutation_lock", Integer.class)).containsExactly(1);
    }

    @Test void loginMfaAndPasswordFinishWhileOnlyAdministrativeCoordinationIsHeld() throws Exception {
        var user = user(Role.USER);
        var actor = actor(user);
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                coordination.acquire();
                try {
                    pool.submit(() -> {
                        var request = new com.ashenox.starter.auth.model.LoginRequest();
                        request.setEmail(user.getEmail());
                        request.setPassword("initial-password");
                        assertThat(login.login(request)).isNotNull();
                        assertThat(mfa.start(user.getId(), actor.sessionId(),
                                com.ashenox.starter.auth.challenge.model.ChallengePurpose.ENABLE, "initial-password")).isNotNull();
                        passwords.change(user.getId(), new com.ashenox.starter.auth.passwordchange.ChangePasswordRequest(
                                "initial-password", "new-password"));
                    }).get(15, TimeUnit.SECONDS);
                } catch (Exception e) { throw new IllegalStateException(e); }
            });
        }
        assertThat(sessions.findById(actor.sessionId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test void userFirstAuthenticationFlowCompletesBeforeWaitingAdministrativeGuard() throws Exception {
        var actor = actor(user(Role.SUPER_ADMIN));
        var result = new AtomicReference<Future<?>>();
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                users.lockById(actor.id()).orElseThrow();
                result.set(pool.submit(() -> assertThatThrownBy(() -> new TransactionTemplate(transactions)
                        .execute(worker -> guard.lock(actor, null))).isInstanceOf(AccessDeniedException.class)));
                awaitDatabaseWait("users");
                passwords.change(actor.id(), new com.ashenox.starter.auth.passwordchange.ChangePasswordRequest(
                        "initial-password", "new-password"));
            });
            result.get().get(15, TimeUnit.SECONDS);
        }
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }

    private static void awaitDatabaseWait(String table) {
        // Observe the actual MySQL lock wait; a scheduled thread or a fixed delay is not evidence of contention.
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             var statement = connection.prepareStatement("""
                     select count(*) from performance_schema.data_lock_waits w
                     join performance_schema.data_locks l on l.engine_lock_id=w.requesting_engine_lock_id
                     where l.object_schema=? and l.object_name=?
                     """)) {
            statement.setString(1, MYSQL.getDatabaseName());
            statement.setString(2, table);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            do {
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    if (rows.getInt(1) > 0) return;
                }
                Thread.sleep(20); // bounded polling of observable database state, not race scheduling
            } while (System.nanoTime() < deadline);
            throw new AssertionError("No observed MySQL lock wait on " + table);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
