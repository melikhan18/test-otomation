package com.devicefarm.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users", schema = "auth")
public class User {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false, length = 32)
    private String role = "USER";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {}
    public User(String username, String passwordHash, Long productId, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.productId = productId;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String h) { this.passwordHash = h; }
    public Long getProductId() { return productId; }
    public String getRole() { return role; }
    public void setRole(String r) { this.role = r; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean e) { this.enabled = e; }
    public Instant getCreatedAt() { return createdAt; }
}
