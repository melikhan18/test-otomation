package com.qaplatform.shared.tenant.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * One row per (project, platform) pair the project owner has activated.
 *
 * <p>Composite PK because platform activations are state, not entities — we
 * don't need to refer to a row by surrogate id from elsewhere, and the (project,
 * platform) pair is naturally unique. {@link Key} is the JPA-required embedded
 * id class.</p>
 */
@Entity
@Table(name = "project_platforms", schema = "tenant")
@IdClass(ProjectPlatformEntity.Key.class)
public class ProjectPlatformEntity {

    @Id
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Id
    @Column(name = "platform", nullable = false, length = 16)
    private String platform;

    @Column(name = "enabled_at", nullable = false, updatable = false)
    private Instant enabledAt = Instant.now();

    @Column(name = "enabled_by")
    private Long enabledBy;

    protected ProjectPlatformEntity() {}

    public ProjectPlatformEntity(Long projectId, String platform, Long enabledBy) {
        this.projectId = projectId;
        this.platform = platform;
        this.enabledBy = enabledBy;
    }

    public Long getProjectId() { return projectId; }
    public String getPlatform() { return platform; }
    public Instant getEnabledAt() { return enabledAt; }
    public Long getEnabledBy() { return enabledBy; }

    /** Composite primary key. JPA requires a separate class for {@code @IdClass}. */
    public static class Key implements Serializable {
        private Long projectId;
        private String platform;

        public Key() {}
        public Key(Long projectId, String platform) {
            this.projectId = projectId;
            this.platform = platform;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(projectId, k.projectId) && Objects.equals(platform, k.platform);
        }
        @Override
        public int hashCode() { return Objects.hash(projectId, platform); }
    }
}
