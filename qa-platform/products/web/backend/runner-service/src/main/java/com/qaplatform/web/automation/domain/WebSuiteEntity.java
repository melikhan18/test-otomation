package com.qaplatform.web.automation.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "suites", schema = "web_automation")
public class WebSuiteEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(nullable = false, length = 255)        private String name;
    @Column(columnDefinition = "TEXT")             private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "TEXT[]", nullable = false)
    private String[] tags = new String[0];

    @Column(name = "created_by_user_id", nullable = false) private Long createdByUserId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)                    private Instant updatedAt = Instant.now();

    protected WebSuiteEntity() {}

    public WebSuiteEntity(Long projectId, String name, Long createdByUserId) {
        this.projectId = projectId;
        this.name = name;
        this.createdByUserId = createdByUserId;
    }

    @PreUpdate void touch() { this.updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getName() { return name; }                public void setName(String v) { this.name = v; }
    public String getDescription() { return description; }   public void setDescription(String v) { this.description = v; }
    public String[] getTags() { return tags; }               public void setTags(String[] v) { this.tags = v == null ? new String[0] : v; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
