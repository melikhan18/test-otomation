package com.devicefarm.automation.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "apps", schema = "automation")
public class AppEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)               private Long projectId;
    @Column(name = "package_name", nullable = false, length = 255) private String packageName;
    @Column(name = "display_name", nullable = false, length = 255) private String displayName;
    @Column(columnDefinition = "TEXT")                            private String description;
    @Column(name = "icon_data", columnDefinition = "TEXT")        private String iconData;

    @Column(name = "created_by_user_id", nullable = false)             private Long createdByUserId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false)                    private Instant updatedAt = Instant.now();
    @Column(name = "archived_at")                                     private Instant archivedAt;

    protected AppEntity() {}

    public AppEntity(Long projectId, String packageName, String displayName, Long createdByUserId) {
        this.projectId = projectId;
        this.packageName = packageName;
        this.displayName = displayName;
        this.createdByUserId = createdByUserId;
    }

    @PreUpdate void touch() { this.updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getPackageName() { return packageName; }
    public String getDisplayName() { return displayName; }   public void setDisplayName(String v) { this.displayName = v; }
    public String getDescription() { return description; }   public void setDescription(String v) { this.description = v; }
    public String getIconData() { return iconData; }         public void setIconData(String v) { this.iconData = v; }
    public Long getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getArchivedAt() { return archivedAt; }    public void setArchivedAt(Instant v) { this.archivedAt = v; }
}
