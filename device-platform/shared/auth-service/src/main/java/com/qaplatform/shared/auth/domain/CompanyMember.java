package com.qaplatform.shared.auth.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** N-to-N user ↔ company with role. Role applies across the entire company. */
@Entity
@Table(name = "company_members", schema = "auth")
@IdClass(CompanyMember.Key.class)
public class CompanyMember {

    @Id @Column(name = "user_id")    private Long userId;
    @Id @Column(name = "company_id") private Long companyId;

    @Column(nullable = false, length = 16)
    private String role;       // OWNER | QA_MANAGER | TESTER

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt = Instant.now();

    protected CompanyMember() {}

    public CompanyMember(Long userId, Long companyId, String role) {
        this.userId = userId;
        this.companyId = companyId;
        this.role = role;
    }

    public Long getUserId()    { return userId; }
    public Long getCompanyId() { return companyId; }
    public String getRole()    { return role; }       public void setRole(String r) { this.role = r; }
    public Instant getJoinedAt() { return joinedAt; }

    public static class Key implements Serializable {
        private Long userId;
        private Long companyId;
        public Key() {}
        public Key(Long userId, Long companyId) { this.userId = userId; this.companyId = companyId; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Key k)) return false;
            return Objects.equals(userId, k.userId) && Objects.equals(companyId, k.companyId);
        }
        @Override public int hashCode() { return Objects.hash(userId, companyId); }
    }
}
