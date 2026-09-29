package com.ashenox.starter.auth.challenge.service;

import com.ashenox.starter.auth.challenge.model.AuthRateLimitScope;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class RateLimitService {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.MANDATORY)
    public RateLimitState lock(AuthRateLimitScope scope, String key, Duration window, Instant now) {
        if (key == null || key.isBlank() || key.length() > 320) {
            throw new IllegalArgumentException("Clave de límite inválida");
        }
        jdbc.update("""
                INSERT IGNORE INTO auth_rate_limits (scope, subject_key, window_started_at, attempt_count)
                VALUES (?, ?, ?, 0)
                """, scope.name(), key, LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        RateLimitState state = jdbc.queryForObject("""
                SELECT id, window_started_at, attempt_count FROM auth_rate_limits
                WHERE scope = ? AND subject_key = ? FOR UPDATE
                """, (rs, row) -> new RateLimitState(jdbc, rs.getLong("id"),
                rs.getObject("window_started_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getInt("attempt_count"), window, now),
                scope.name(), key);
        if (!now.isBefore(state.startedAt.plus(window))) {
            jdbc.update("UPDATE auth_rate_limits SET window_started_at = ?, attempt_count = 0 WHERE id = ?",
                    LocalDateTime.ofInstant(now, ZoneOffset.UTC), state.id);
            state.startedAt = now;
            state.count = 0;
        }
        return state;
    }

    public static final class RateLimitState {
        private final JdbcTemplate jdbc;
        private final long id;
        private final Duration window;
        private final Instant now;
        private Instant startedAt;
        private int count;

        private RateLimitState(JdbcTemplate jdbc, long id, Instant startedAt,
                               int count, Duration window, Instant now) {
            this.jdbc = jdbc;
            this.id = id;
            this.startedAt = startedAt;
            this.count = count;
            this.window = window;
            this.now = now;
        }

        public boolean exhausted(int maximum) {
            return count >= maximum;
        }

        public long retryAfterSeconds() {
            return Math.max(1, Duration.between(now, startedAt.plus(window)).toSeconds());
        }

        public void increment() {
            jdbc.update("UPDATE auth_rate_limits SET attempt_count = attempt_count + 1 WHERE id = ?", id);
            count++;
        }
    }
}
