package com.devicefarm.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository
        extends JpaRepository<ProjectMember, ProjectMember.Key> {

    List<ProjectMember> findAllByUserId(Long userId);
    List<ProjectMember> findAllByProjectId(Long projectId);
    Optional<ProjectMember> findByUserIdAndProjectId(Long userId, Long projectId);
    boolean existsByUserIdAndProjectId(Long userId, Long projectId);

    /** All project_members rows for a single user, restricted to one company.
     *  Used by buildApi/JwtMemberships to assemble per-company project lists. */
    @Query("""
        SELECT pm FROM ProjectMember pm
        JOIN Project p ON p.id = pm.projectId
        WHERE pm.userId = :userId
          AND p.companyId = :companyId
          AND p.archivedAt IS NULL
    """)
    List<ProjectMember> findAllByUserIdAndCompanyId(@Param("userId") Long userId,
                                                    @Param("companyId") Long companyId);

    /** All members of every project in the company; one round-trip when we need
     *  to render a company-wide members matrix. */
    @Query("""
        SELECT pm FROM ProjectMember pm
        JOIN Project p ON p.id = pm.projectId
        WHERE p.companyId = :companyId
          AND p.archivedAt IS NULL
    """)
    List<ProjectMember> findAllByCompanyId(@Param("companyId") Long companyId);

    @Transactional
    void deleteAllByUserIdAndProjectId(Long userId, Long projectId);

    @Transactional
    void deleteAllByUserId(Long userId);

    @Transactional
    void deleteAllByProjectId(Long projectId);

    /** Drop every project_members row for (user, company). Used when an OWNER
     *  removes a user from a company — cleanup cascades to all projects. */
    @Query("""
        DELETE FROM ProjectMember pm
        WHERE pm.userId = :userId
          AND pm.projectId IN (
              SELECT p.id FROM Project p WHERE p.companyId = :companyId
          )
    """)
    @org.springframework.data.jpa.repository.Modifying
    @Transactional
    int deleteAllByUserIdAndCompanyId(@Param("userId") Long userId,
                                       @Param("companyId") Long companyId);
}
