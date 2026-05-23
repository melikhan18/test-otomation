package com.qaplatform.shared.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {
    Optional<Company> findBySlug(String slug);
    Optional<Company> findByLegacyProductId(Long legacyProductId);
    boolean existsBySlug(String slug);

    /** Active (non-archived) companies — drives listings and membership lookups. */
    List<Company> findAllByArchivedAtIsNull();
    List<Company> findAllByIdInAndArchivedAtIsNull(Collection<Long> ids);
}
