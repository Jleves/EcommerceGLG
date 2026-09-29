package com.ashenox.starter.user;

import com.ashenox.starter.auth.challenge.model.ChallengePurpose;
import com.ashenox.starter.security.error.InvalidAuthSessionException;
import com.ashenox.starter.auth.model.LoginRequest;
import com.ashenox.starter.security.error.InvalidCredentialsException;
import com.ashenox.starter.security.error.InvalidRefreshTokenException;
import com.ashenox.starter.security.error.InvalidTokenException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.DriverManager;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class AdminUserCrossRaceMySqlTest extends AdminUserReactivationTest {
    @Container @ServiceConnection static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");

    @Test void resetWaitingForDeactivationCannotChangePassword() throws Exception {
        reset();
        var originalHash = current().getPasswordHash();
        var waiting = new AtomicReference<java.util.concurrent.Future<?>>();
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                service.deactivate(target.getId(), actor);
                waiting.set(pool.submit(() -> assertThatThrownBy(() ->
                        resetService.resetPassword("reset-" + target.getId(), "another-password-123"))
                        .isInstanceOf(InvalidTokenException.class)));
                awaitWait("users");
            });
            waiting.get().get(20, TimeUnit.SECONDS);
        }
        assertThat(current().isEnabled()).isFalse();
        assertThat(current().getPasswordHash()).isEqualTo(originalHash);
    }

    @Test void resetCommittedBeforeDeactivationStillLeavesOldCredentialsInvalid() throws Exception {
        reset();
        var waiting = new AtomicReference<java.util.concurrent.Future<?>>();
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                resetService.resetPassword("reset-" + target.getId(), "another-password-123");
                waiting.set(pool.submit(() -> service.deactivate(target.getId(), actor)));
                awaitWait("users");
            });
            waiting.get().get(20, TimeUnit.SECONDS);
        }
        assertThat(current().isEnabled()).isFalse();
        assertThatThrownBy(() -> resetService.validateToken("reset-" + target.getId()))
                .isInstanceOf(InvalidTokenException.class);
        assertThat(sessionRepository.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test void resetCannotBecomeUsableWhileReactivationWaitsForDeactivation() throws Exception {
        reset();
        var waiting = new AtomicReference<java.util.concurrent.Future<?>>();
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                service.deactivate(target.getId(), actor);
                waiting.set(pool.submit(() -> service.reactivate(target.getId(), actor)));
                awaitWait("admin_mutation_lock");
            });
            waiting.get().get(20, TimeUnit.SECONDS);
        }
        assertThat(current().isEnabled()).isTrue();
        assertThatThrownBy(() -> resetService.validateToken("reset-" + target.getId()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test void refreshWaitingForDeactivationCannotReviveSession() throws Exception {
        var waiting = new AtomicReference<java.util.concurrent.Future<?>>();
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                service.deactivate(target.getId(), actor);
                waiting.set(pool.submit(() -> assertThatThrownBy(() -> sessions.rotate(origin.refreshToken()))
                        .isInstanceOf(InvalidRefreshTokenException.class)));
                awaitWait("auth_sessions");
            });
            waiting.get().get(20, TimeUnit.SECONDS);
        }
        assertThat(sessionRepository.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test void loginWaitingForDeactivationCannotCreateSession() throws Exception {
        var request = new LoginRequest();
        request.setEmail(target.getEmail());
        request.setPassword("secure-password");
        var waiting = new AtomicReference<java.util.concurrent.Future<?>>();
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                service.deactivate(target.getId(), actor);
                waiting.set(pool.submit(() -> assertThatThrownBy(() -> login.login(request))
                        .isInstanceOf(InvalidCredentialsException.class)));
                awaitWait("users");
            });
            waiting.get().get(20, TimeUnit.SECONDS);
        }
        assertThat(current().isEnabled()).isFalse();
    }

    @Test void mfaEnableWaitingForDeactivationCannotRestoreFactorOrSession() throws Exception {
        var pending = mfa.start(target.getId(), origin.session().getId(), ChallengePurpose.ENABLE, "secure-password");
        delivery.deliverCreated(pending);
        var waiting = new AtomicReference<java.util.concurrent.Future<?>>();
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                service.deactivate(target.getId(), actor);
                waiting.set(pool.submit(() -> assertThatThrownBy(() -> mfa.confirm(target.getId(),
                        origin.session().getId(), ChallengePurpose.ENABLE, pending.cookieValue(), pending.code()))
                        .isInstanceOf(InvalidAuthSessionException.class)));
                awaitWait("users");
            });
            waiting.get().get(20, TimeUnit.SECONDS);
        }
        assertThat(current().isEnabled()).isFalse();
        assertThat(current().isEmailMfaEnabled()).isFalse();
        assertThat(challengeRepository.findById(pending.challenge().getId()).orElseThrow().getInvalidatedAt()).isNotNull();
    }

    @Test void loginCommittedBeforeDeactivationHasItsNewSessionRevoked() throws Exception {
        var request = new LoginRequest();
        request.setEmail(target.getEmail());
        request.setPassword("secure-password");
        var newSession = new AtomicReference<String>();
        var waiting = new AtomicReference<java.util.concurrent.Future<?>>();
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                newSession.set(login.login(request).authentication().sessionId());
                waiting.set(pool.submit(() -> service.deactivate(target.getId(), actor)));
                awaitWait("users");
            });
            waiting.get().get(20, TimeUnit.SECONDS);
        }
        assertThat(sessionRepository.findById(newSession.get()).orElseThrow().getRevokedAt()).isNotNull();
    }

    @Test void refreshCommittedBeforeDeactivationCannotLeaveRotatedTokenUsable() throws Exception {
        var rotated = new AtomicReference<com.ashenox.starter.auth.session.service.IssuedSession>();
        var waiting = new AtomicReference<java.util.concurrent.Future<?>>();
        try (var pool = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                rotated.set(sessions.rotate(origin.refreshToken()));
                waiting.set(pool.submit(() -> service.deactivate(target.getId(), actor)));
                awaitWait("auth_sessions");
            });
            waiting.get().get(20, TimeUnit.SECONDS);
        }
        assertThatThrownBy(() -> sessions.rotate(rotated.get().refreshToken()))
                .isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(sessionRepository.findById(origin.session().getId()).orElseThrow().getRevokedAt()).isNotNull();
    }

    private static void awaitWait(String table) {
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             var statement = connection.prepareStatement("select count(*) from performance_schema.data_lock_waits w join performance_schema.data_locks l on l.engine_lock_id=w.requesting_engine_lock_id where l.object_schema=? and l.object_name=?")) {
            statement.setString(1, MYSQL.getDatabaseName());
            statement.setString(2, table);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            do {
                try (var rows = statement.executeQuery()) { rows.next(); if (rows.getInt(1) > 0) return; }
                Thread.sleep(20);
            } while (System.nanoTime() < deadline);
            throw new AssertionError("No MySQL lock wait on " + table);
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
