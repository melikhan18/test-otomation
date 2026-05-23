package com.qaplatform.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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

    /**
     * Full multi-tenancy access token. The {@code companies} list is encoded as
     * an array of small maps so the JWT stays parseable by every service via
     * {@link Claims#get(String, Class)} without bespoke deserialisers.
     */
    public String issueUserAccessToken(long userId, String role, long productId,
                                       boolean platformAdmin,
                                       List<JwtPrincipal.CompanyMembership> companies) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("userId", userId);
        claims.put("productId", productId);
        claims.put("platformAdmin", platformAdmin);
        claims.put("companies", encodeCompanies(companies));
        return issue("user:" + userId, props.getAccessTokenTtl(), claims);
    }

    private static List<Map<String, Object>> encodeCompanies(List<JwtPrincipal.CompanyMembership> companies) {
        if (companies == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>(companies.size());
        for (JwtPrincipal.CompanyMembership c : companies) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.id());
            m.put("slug", c.slug());
            m.put("owner", c.owner());
            m.put("projects", encodeProjects(c.projects()));
            out.add(m);
        }
        return out;
    }

    private static List<Map<String, Object>> encodeProjects(List<JwtPrincipal.ProjectGrant> grants) {
        if (grants == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>(grants.size());
        for (JwtPrincipal.ProjectGrant g : grants) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", g.id());
            m.put("role", g.role());
            out.add(m);
        }
        return out;
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
        Boolean platformAdmin = c.get("platformAdmin", Boolean.class);
        List<JwtPrincipal.CompanyMembership> companies = readCompanies(c);
        return new JwtPrincipal(
                c.getSubject(), userId, deviceId, sessionId, role, productId,
                platformAdmin != null && platformAdmin,
                companies);
    }

    @SuppressWarnings("unchecked")
    private static List<JwtPrincipal.CompanyMembership> readCompanies(Claims c) {
        Object raw = c.get("companies");
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) return null;
        List<JwtPrincipal.CompanyMembership> out = new ArrayList<>(rawList.size());
        for (Object o : rawList) {
            if (!(o instanceof Map<?, ?> m)) continue;
            long id = ((Number) m.get("id")).longValue();
            String slug = (String) m.get("slug");
            boolean owner = Boolean.TRUE.equals(m.get("owner"));
            List<JwtPrincipal.ProjectGrant> projects = readProjects(m.get("projects"));
            out.add(new JwtPrincipal.CompanyMembership(id, slug, owner, projects));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<JwtPrincipal.ProjectGrant> readProjects(Object raw) {
        if (!(raw instanceof List<?> rawList) || rawList.isEmpty()) return List.of();
        List<JwtPrincipal.ProjectGrant> out = new ArrayList<>(rawList.size());
        for (Object o : rawList) {
            if (!(o instanceof Map<?, ?> m)) continue;
            long id = ((Number) m.get("id")).longValue();
            String role = (String) m.get("role");
            out.add(new JwtPrincipal.ProjectGrant(id, role));
        }
        return out;
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
