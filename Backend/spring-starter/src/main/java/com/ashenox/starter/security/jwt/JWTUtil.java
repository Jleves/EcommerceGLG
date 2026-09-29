package com.ashenox.starter.security.jwt;

import com.ashenox.starter.security.model.AuthenticatedUser;
import com.ashenox.starter.shared.config.AppProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class JWTUtil {
    private final AppProperties appProperties;

    private Key getSignInKey() {
        String secret = appProperties.getSecurity().getJwt().getSecret();
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // El subject es exclusivamente el ID persistido, nunca el correo de login.
    public Long extractUserId(String token) {
        String subject = extractClaim(token, claims -> claims.get(Claims.SUBJECT, String.class));
        if (subject == null || !subject.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("JWT subject must be a positive user ID");
        }
        return Long.valueOf(subject);
    }

    // Extrae la fecha de expiración
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Método genérico para extraer cualquier claim
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Extrae todos los claims del token
    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // Solo acepta una identidad persistida; getUsername() sigue siendo el email.
    public String generateToken(Long userId, String sessionId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("JWT requires a positive user ID");
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("sid", sessionId);
        return generateToken(claims, userId);
    }

    public String extractSessionId(String token) {
        return extractClaim(token, claims -> claims.get("sid", String.class));
    }

    // Podés agregar claims personalizados (roles, permisos, etc.)
    private String generateToken(Map<String, Object> extraClaims, Long userId) {
        long expiracionTime = appProperties.getSecurity().getJwt().getAccessExpiration().toMillis();
        long issuedAt = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userId.toString())
                .setIssuedAt(new Date(issuedAt))
                .setExpiration(new Date(issuedAt + expiracionTime))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // Valida si el token corresponde al usuario y no está expirado
    public boolean isTokenValid(String token, AuthenticatedUser user) {
        return extractUserId(token).equals(user.id()) && !isTokenExpired(token);
    }

    // Verifica si el token expiró
    private boolean isTokenExpired(String token) {
        Date expiration = extractExpiration(token);
        if (expiration == null) {
            throw new IllegalArgumentException("JWT expiration is required");
        }
        return !expiration.after(new Date());
    }

}
