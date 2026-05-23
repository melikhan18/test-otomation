package com.devicefarm.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Pending or completed invitation row. Created when an admin invites someone
 * by email — the corresponding {@link Notification} is what the invitee
 * actually sees in their bell dropdown.
 *
 * The original V3 table had a {@code token} column for email-link flows; that's
 * been retired in favor of the notification UX but the column is kept (nullable)
 * for audit + future re-introduction.
 */
@Entity
@Table(name = "company_invitations", schema = "auth")
public class CompanyInvitation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 16)
    private String role;

    /** Optional opaque token — reserved for future email-link acceptance. */
    @Column(length = 255)
    private String token;

    @Column(name = "invited_by", nullable = false)
    private Long invitedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "declined_at")
    private Instant declinedAt;

    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected CompanyInvitation() {}

    public CompanyInvitation(Long companyId, String email, String role, Long invitedBy, Instant expiresAt) {
        this.companyId = companyId;
        this.email = email == null ? null : email.toLowerCase().trim();
        this.role = role;
        this.invitedBy = invitedBy;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getEmail() { return email; }
    public String getRole() { return role; }                 public void setRole(String v) { this.role = v; }
    public String getToken() { return token; }               public void setToken(String v) { this.token = v; }
    public Long getInvitedBy() { return invitedBy; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getDeclinedAt() { return declinedAt; }
    public Long getNotificationId() { return notificationId; }
    public void setNotificationId(Long v) { this.notificationId = v; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isPending() { return acceptedAt == null && declinedAt == null; }
    public void markAccepted() { this.acceptedAt = Instant.now(); }
    public void markDeclined() { this.declinedAt = Instant.now(); }
}
