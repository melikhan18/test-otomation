package com.devicefarm.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "projects", schema = "auth")
public class Project {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 64)
    private String slug;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Project() {}

    public Project(Long companyId, String slug, String name) {
        this.companyId = companyId;
        this.slug = slug;
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getSlug() { return slug; }                      public void setSlug(String v) { this.slug = v; }
    public String getName() { return name; }                      public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }        public void setDescription(String v) { this.description = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getArchivedAt() { return archivedAt; }         public void setArchivedAt(Instant v) { this.archivedAt = v; }
}
