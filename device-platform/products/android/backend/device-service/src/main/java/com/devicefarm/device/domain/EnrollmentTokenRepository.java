package com.devicefarm.device.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnrollmentTokenRepository extends JpaRepository<EnrollmentToken, Long> {
    Optional<EnrollmentToken> findByToken(String token);
}
