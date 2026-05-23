package com.qaplatform.shared.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Generic in-app notification. The {@link #type} string + JSON {@link #payload}
 * keeps the table future-proof — new event kinds (RUN_COMPLETED, MEMBER_ADDED,
 * …) don't need a schema change. The application layer ({@code NotificationService})
 * owns serializing typed payloads in/out via Jackson.
 *
 * Status lifecycle
 * ────────────────
 *  <pre>
 *    UNREAD   ── user opens bell / clicks ──▶  READ
 *    UNREAD   ── user dismisses info row ──▶  DISMISSED
 *    UNREAD   ── invite Accept             ─▶  ACCEPTED   (also sets resolved_at)
 *    UNREAD   ── invite Decline            ─▶  DECLINED   (also sets resolved_at)
 *  </pre>
 */
@Entity
@Table(name = "notifications", schema = "auth")
public class Notification {

    public static final String STATUS_UNREAD    = "UNREAD";
    public static final String STATUS_READ      = "READ";
    public static final String STATUS_DISMISSED = "DISMISSED";
    public static final String STATUS_ACCEPTED  = "ACCEPTED";
    public static final String STATUS_DECLINED  = "DECLINED";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 48)
    private String type;

    @Column(nullable = false, length = 16)
    private String status = STATUS_UNREAD;

    /**
     * JSON document — Postgres column is {@code jsonb}, mapped here as a String
     * and serialised/deserialised by NotificationService via Jackson. Doing the
     * conversion in the service layer keeps the entity dependency-free and lets
     * us evolve payload shapes without DB-level constraints.
     */
    @Column(columnDefinition = "jsonb", nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String payload = "{}";

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    protected Notification() {}

    public Notification(Long userId, String type, String payloadJson, Long actorUserId) {
        this.userId = userId;
        this.type = type;
        if (payloadJson != null && !payloadJson.isBlank()) this.payload = payloadJson;
        this.actorUserId = actorUserId;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getType() { return type; }
    public String getStatus() { return status; }              public void setStatus(String v) { this.status = v; }
    public String getPayload() { return payload; }            public void setPayload(String v) { this.payload = v; }
    public Long getActorUserId() { return actorUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }     public void setResolvedAt(Instant v) { this.resolvedAt = v; }
    public Instant getExpiresAt() { return expiresAt; }       public void setExpiresAt(Instant v) { this.expiresAt = v; }

    public void markRead() { if (STATUS_UNREAD.equals(status)) status = STATUS_READ; }
    public void resolve(String terminalStatus) {
        this.status = terminalStatus;
        this.resolvedAt = Instant.now();
    }
}
