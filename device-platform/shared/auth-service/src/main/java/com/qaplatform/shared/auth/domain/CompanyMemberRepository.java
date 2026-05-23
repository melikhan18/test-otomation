package com.qaplatform.shared.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface CompanyMemberRepository
        extends JpaRepository<CompanyMember, CompanyMember.Key> {

    List<CompanyMember> findAllByUserId(Long userId);
    List<CompanyMember> findAllByCompanyId(Long companyId);
    Optional<CompanyMember> findByUserIdAndCompanyId(Long userId, Long companyId);
    boolean existsByUserIdAndCompanyIdAndRole(Long userId, Long companyId, String role);

    long countByCompanyIdAndRole(Long companyId, String role);

    @Transactional
    void deleteByUserIdAndCompanyId(Long userId, Long companyId);
}
