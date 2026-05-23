package com.qaplatform.common.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** HS512 shared secret. Base64-encoded, at least 64 bytes. */
    private String secret;

    /** Issuer claim. */
    private String issuer = "device-platform";

    /** Access token TTL for human users. */
    private Duration accessTokenTtl = Duration.ofMinutes(30);

    /** Refresh token TTL. */
    private Duration refreshTokenTtl = Duration.ofDays(14);

    /** Session JWT TTL (used for WS subprotocol auth). */
    private Duration sessionTokenTtl = Duration.ofHours(2);

    /** Long-lived agent token TTL. */
    private Duration agentTokenTtl = Duration.ofDays(365);

    /** Enrollment token TTL. */
    private Duration enrollmentTokenTtl = Duration.ofMinutes(15);

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
    public Duration getAccessTokenTtl() { return accessTokenTtl; }
    public void setAccessTokenTtl(Duration v) { this.accessTokenTtl = v; }
    public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
    public void setRefreshTokenTtl(Duration v) { this.refreshTokenTtl = v; }
    public Duration getSessionTokenTtl() { return sessionTokenTtl; }
    public void setSessionTokenTtl(Duration v) { this.sessionTokenTtl = v; }
    public Duration getAgentTokenTtl() { return agentTokenTtl; }
    public void setAgentTokenTtl(Duration v) { this.agentTokenTtl = v; }
    public Duration getEnrollmentTokenTtl() { return enrollmentTokenTtl; }
    public void setEnrollmentTokenTtl(Duration v) { this.enrollmentTokenTtl = v; }
}
