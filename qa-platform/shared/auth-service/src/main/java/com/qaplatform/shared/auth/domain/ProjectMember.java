package com.qaplatform.shared.auth.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Project-scoped membership. Roles live here now (QA_MANAGER / TESTER); a user
 * may be QA_MANAGER on one project and TESTER on another. Company OWNERs do not
 * need a row here — they get implicit access to every project in their company.
 */
@Entity
@Table(name = "project_members", schema = "auth")
@IdClass(ProjectMember.Key.class)
public class ProjectMember {

    @Id @Column(name = "user_id")    private Long userId;
    @Id @Column(name = "project_id") private Long projectId;

    @Column(nullable = false, length = 16)
    private String role;   // QA_MANAGER | TESTER

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt = Instant.now();

    @Column(name = "added_by")
    private Long addedBy;

    protected ProjectMember() {}

    public ProjectMember(Long userId, Long projectId, String role, Long addedBy) {
        this.userId = userId;
        this.projectId = projectId;
        this.role = role;
        this.addedBy = addedBy;
    }

    public Long getUserId() { return userId; }
    public Long getProjectId() { return projectId; }
    public String getRole() { return role; }            public void setRole(String r) { this.role = r; }
    public Instant getAddedAt() { return addedAt; }
    public Long getAddedBy() { return addedBy; }

    public static class Key implements Serializable {
        private Long userId;
        private Long projectId;
        public Key() {}
        public Key(Long userId, Long projectId) { this.userId = userId; this.projectId = projectId; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return Objects.equals(userId, k.userId) && Objects.equals(projectId, k.projectId);
        }
        @Override public int hashCode() { return Objects.hash(userId, projectId); }
    }
}
