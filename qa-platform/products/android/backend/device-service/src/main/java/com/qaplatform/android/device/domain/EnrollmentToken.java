package com.qaplatform.android.device.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "enrollment_tokens", schema = "android_device")
public class EnrollmentToken {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "issued_by_user_id", nullable = false)
    private Long issuedByUserId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "used_by_device_id")
    private Long usedByDeviceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected EnrollmentToken() {}
    public EnrollmentToken(String token, Long companyId, Long issuedByUserId, Instant expiresAt) {
        this.token = token;
        this.companyId = companyId;
        this.issuedByUserId = issuedByUserId;
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public String getToken() { return token; }
    public Long getCompanyId() { return companyId; }
    public Long getIssuedByUserId() { return issuedByUserId; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Long getUsedByDeviceId() { return usedByDeviceId; }
    public void markUsed(Long deviceId) { this.usedAt = Instant.now(); this.usedByDeviceId = deviceId; }
}
