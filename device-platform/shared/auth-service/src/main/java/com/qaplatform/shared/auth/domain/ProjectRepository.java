package com.qaplatform.shared.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findAllByCompanyIdAndArchivedAtIsNullOrderByName(Long companyId);
    List<Project> findAllByCompanyIdOrderByName(Long companyId);
    Optional<Project> findByCompanyIdAndSlug(Long companyId, String slug);
    boolean existsByCompanyIdAndSlug(Long companyId, String slug);
    Optional<Project> findFirstByCompanyIdAndSlug(Long companyId, String slug);
}
