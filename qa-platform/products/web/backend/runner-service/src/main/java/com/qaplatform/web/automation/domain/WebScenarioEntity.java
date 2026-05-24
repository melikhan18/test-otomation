package com.qaplatform.web.automation.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A named, versioned web test scenario — sequence of {@link WebStepEntity}.
 *
 * <p>Auto-bumps {@code version} on every update so reports can pin failures
 * to a specific iteration (same {@code @PreUpdate} trick Android uses).</p>
 */
@Entity
@Table(name = "scenarios", schema = "web_automation")
public class WebScenarioEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(nullable = false, length = 255)        private String name;
    @Column(columnDefinition = "TEXT")             private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "TEXT[]", nullable = false)
    private String[] tags = new String[0];

    @Column(nullable = false) private int version = 1;

    @Column(name = "created_by_user_id", nullable = false)             private Long createdByUserId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)                    private Instant updatedAt = Instant.now();

    protected WebScenarioEntity() {}

    public WebScenarioEntity(Long projectId, String name, Long createdByUserId) {
        this.projectId = projectId;
        this.name = name;
        this.createdByUserId = createdByUserId;
    }

    @PreUpdate void touch() { this.updatedAt = Instant.now(); this.version += 1; }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getName() { return name; }                 public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }   public void setDescription(String v) { this.description = v; }
    public String[] getTags() { return tags; }               public void setTags(String[] v) { this.tags = v == null ? new String[0] : v; }
    public int getVersion() { return version; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
