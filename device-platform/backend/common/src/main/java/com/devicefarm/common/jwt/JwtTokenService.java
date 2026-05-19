package com.devicefarm.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.Objects;

/**
 * Stateless JWT issuer + verifier. HS512 shared secret.
 * For production deployments switch to RS256 + JWKS (Phase 11).
 */
public class JwtTokenService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtTokenService(JwtProperties props) {
        this.props = Objects.requireNonNull(props, "JwtProperties");
        Objects.requireNonNull(props.getSecret(), "app.jwt.secret must be configured");
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(props.getSecret());
        } catch (IllegalArgumentException e) {
            raw = props.getSecret().getBytes(StandardCharsets.UTF_8);
        }
        if (raw.length < 64) {
            throw new IllegalStateException("app.jwt.secret must be at least 64 bytes (got " + raw.length + ")");
        }
        this.key = Keys.hmacShaKeyFor(raw);
    }

    public String issueUserAccessToken(long userId, String role, long productId) {
        return issue("user:" + userId, props.getAccessTokenTtl(), Map.of(
                "role", role,
                "userId", userId,
                "productId", productId
        ));
    }

    public String issueAgentToken(long deviceId, long productId) {
        return issue("device:" + deviceId, props.getAgentTokenTtl(), Map.of(
                "role", "AGENT",
                "deviceId", deviceId,
                "productId", productId
        ));
    }

    public String issueSessionToken(long sessionId, long deviceId, long userId, long productId) {
        return issue("session:" + sessionId, props.getSessionTokenTtl(), Map.of(
                "role", "SESSION",
                "sessionId", sessionId,
                "deviceId", deviceId,
                "userId", userId,
                "productId", productId
        ));
    }

    public String issueRefreshToken(long userId, long productId) {
        return issue("user:" + userId, props.getRefreshTokenTtl(), Map.of(
                "type", "refresh",
                "userId", userId,
                "productId", productId
        ));
    }

    private String issue(String subject, Duration ttl, Map<String, Object> claims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claims(claims)
                .signWith(key, Jwts.SIG.HS512)
                .compact();
    }

    public JwtPrincipal parse(String token) {
        Claims c = parseClaims(token);
        return toPrincipal(c);
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(props.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw new InvalidJwtException("Invalid JWT: " + e.getMessage(), e);
        }
    }

    private static JwtPrincipal toPrincipal(Claims c) {
        String role = c.get("role", String.class);
        Long userId = readLong(c, "userId");
        Long deviceId = readLong(c, "deviceId");
        Long sessionId = readLong(c, "sessionId");
        Long productId = readLong(c, "productId");
        return new JwtPrincipal(c.getSubject(), userId, deviceId, sessionId, role, productId);
    }

    private static Long readLong(Claims c, String key) {
        Object v = c.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.valueOf(v.toString());
    }

    public static class InvalidJwtException extends RuntimeException {
        public InvalidJwtException(String msg, Throwable cause) { super(msg, cause); }
    }
}
