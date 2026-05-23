package com.qaplatform.android.session.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "sessions", schema = "android_session")
public class Session {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 16)
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    protected Session() {}
    public Session(Long deviceId, Long userId, Long productId) {
        this.deviceId = deviceId;
        this.userId = userId;
        this.productId = productId;
    }

    public Long getId() { return id; }
    public Long getDeviceId() { return deviceId; }
    public Long getUserId() { return userId; }
    public Long getProductId() { return productId; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getEndedAt() { return endedAt; }

    public void end() { this.status = "ENDED"; this.endedAt = Instant.now(); }
    public boolean isActive() { return "ACTIVE".equals(status); }
}
