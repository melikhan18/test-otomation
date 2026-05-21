package com.devicefarm.automation.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "scenarios", schema = "automation")
public class ScenarioEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(nullable = false, length = 255)         private String name;
    @Column(columnDefinition = "TEXT")              private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "TEXT[]", nullable = false)
    private String[] tags = new String[0];

    @Column(columnDefinition = "TEXT")              private String preconditions;
    @Column(nullable = false)                        private int version = 1;

    @Column(name = "created_by_user_id", nullable = false)             private Long createdByUserId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)                    private Instant updatedAt = Instant.now();

    protected ScenarioEntity() {}

    public ScenarioEntity(Long productId, Long projectId, String name, Long createdByUserId) {
        this.productId = productId;
        this.projectId = projectId;
        this.name = name;
        this.createdByUserId = createdByUserId;
    }

    @PreUpdate void touch() { this.updatedAt = Instant.now(); this.version += 1; }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getProjectId() { return projectId; }
    public String getName() { return name; }                public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }   public void setDescription(String v) { this.description = v; }
    public String[] getTags() { return tags; }               public void setTags(String[] v) { this.tags = v == null ? new String[0] : v; }
    public String getPreconditions() { return preconditions; } public void setPreconditions(String v) { this.preconditions = v; }
    public int getVersion() { return version; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
