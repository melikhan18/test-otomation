package com.devicefarm.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, Long> {

    Optional<CompanyInvitation> findByNotificationId(Long notificationId);
    List<CompanyInvitation> findAllByCompanyIdAndAcceptedAtIsNullAndDeclinedAtIsNull(Long companyId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT i FROM CompanyInvitation i " +
        "WHERE LOWER(i.email) = LOWER(:email) " +
        "  AND i.companyId = :companyId " +
        "  AND i.acceptedAt IS NULL AND i.declinedAt IS NULL"
    )
    Optional<CompanyInvitation> findPendingByEmailAndCompany(
            @org.springframework.data.repository.query.Param("email") String email,
            @org.springframework.data.repository.query.Param("companyId") Long companyId);
}
