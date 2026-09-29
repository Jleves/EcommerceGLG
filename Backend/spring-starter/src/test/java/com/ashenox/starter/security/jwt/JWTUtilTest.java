package com.ashenox.starter.security.jwt;

import com.ashenox.starter.security.model.AuthenticatedUser;
import com.ashenox.starter.shared.config.AppProperties;
import com.ashenox.starter.user.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class JWTUtilTest {
    private static final byte[] KEY = "0123456789abcdef0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    private final JWTUtil jwt = new JWTUtil(properties());

    @ParameterizedTest
    @ValueSource(longs = {1L, 42L, Long.MAX_VALUE})
    void emitsCanonicalIdAndSessionWithSignatureAndExpiration(long userId) {
        Instant before = Instant.now();
        String token = jwt.generateToken(userId, "session-42");
        Claims claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(KEY)).build()
                .parseClaimsJws(token).getBody();

        assertThat(claims.getSubject()).isEqualTo(Long.toString(userId));
        assertThat(jwt.extractUserId(token)).isEqualTo(userId);
        assertThat(jwt.extractSessionId(token)).isEqualTo("session-42");
        assertThat(claims.keySet()).containsExactlyInAnyOrder("sub", "sid", "iat", "exp");
        assertThat(claims.getExpiration().toInstant())
                .isBetween(before.plusSeconds(899), Instant.now().plusSeconds(900));
        assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime()).isEqualTo(900_000);
    }

    @Test
    void validatesIdentityIndependentlyOfEmailAndRejectsAnotherIdWithSameEmail() {
        String token = jwt.generateToken(42L, "session-42");
        assertThat(jwt.isTokenValid(token, user(42L, "old@example.com"))).isTrue();
        assertThat(jwt.isTokenValid(token, user(42L, "new@example.com"))).isTrue();
        assertThat(jwt.isTokenValid(token, user(43L, "old@example.com"))).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"old@example.com", "0", "-1", "+1", "01", " 1", "1 ", "1.0", "1e2", "abc",
            "9223372036854775808", "12345678901234567890", "１２", "1\n"})
    void rejectsMissingLegacyAndNonCanonicalSubjects(String subject) {
        String token = signed(subject, Date.from(Instant.now().plusSeconds(60)));
        assertThatThrownBy(() -> jwt.extractUserId(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNumericJsonSubjectInsteadOfString() {
        String token = Jwts.builder().setClaims(Map.of("sub", 42L))
                .setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(KEY), SignatureAlgorithm.HS256).compact();
        assertThatThrownBy(() -> jwt.extractUserId(token)).isInstanceOf(JwtException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, -1L, Long.MIN_VALUE})
    void refusesToIssueWithoutPositivePersistedId(Long userId) {
        assertThatThrownBy(() -> jwt.generateToken(userId, "session"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsExpiredSignedToken() {
        String token = signed("42", Date.from(Instant.now().minusSeconds(10)));
        assertThatThrownBy(() -> jwt.extractUserId(token)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTokenWithoutExpiration() {
        assertThatThrownBy(() -> jwt.isTokenValid(signed("42", null), user(42L, "user@example.com")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        String token = Jwts.builder().setSubject("42").setExpiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.secretKeyFor(SignatureAlgorithm.HS256)).compact();
        assertThatThrownBy(() -> jwt.extractUserId(token)).isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }

    @Test
    void rejectsUnsignedToken() {
        String token = Jwts.builder().setSubject("42").setExpiration(Date.from(Instant.now().plusSeconds(60))).compact();
        assertThatThrownBy(() -> jwt.extractUserId(token)).isInstanceOf(JwtException.class);
    }

    private static String signed(String subject, Date expiration) {
        return Jwts.builder().setSubject(subject).setExpiration(expiration)
                .signWith(Keys.hmacShaKeyFor(KEY), SignatureAlgorithm.HS256).compact();
    }

    private static AuthenticatedUser user(Long id, String email) {
        return new AuthenticatedUser(id, email, "hash", Role.USER, true);
    }

    private static AppProperties properties() {
        AppProperties properties = new AppProperties();
        properties.getSecurity().getJwt().setSecret(Base64.getEncoder().encodeToString(KEY));
        properties.getSecurity().getJwt().setAccessExpiration(Duration.ofMinutes(15));
        return properties;
    }
}
