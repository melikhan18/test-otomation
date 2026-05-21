package com.devicefarm.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "companies", schema = "auth")
public class Company {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(nullable = false, length = 128)
    private String name;

    /** Legacy single-tenant product this company was migrated from. Drop later. */
    @Column(name = "legacy_product_id", unique = true)
    private Long legacyProductId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Company() {}

    public Company(String slug, String name) {
        this.slug = slug;
        this.name = name;
    }

    public Long getId() { return id; }
    public String getSlug() { return slug; }                  public void setSlug(String v) { this.slug = v; }
    public String getName() { return name; }                  public void setName(String v) { this.name = v; }
    public Long getLegacyProductId() { return legacyProductId; }
    public void setLegacyProductId(Long v) { this.legacyProductId = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getArchivedAt() { return archivedAt; }     public void setArchivedAt(Instant v) { this.archivedAt = v; }
}
