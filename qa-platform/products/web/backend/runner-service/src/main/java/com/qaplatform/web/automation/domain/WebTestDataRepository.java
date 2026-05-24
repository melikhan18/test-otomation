package com.qaplatform.web.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebTestDataRepository extends JpaRepository<WebTestDataEntity, Long> {
    List<WebTestDataEntity> findAllByProjectIdOrderByUpdatedAtDesc(Long projectId);
    boolean existsByProjectIdAndNameAndEnvironment(Long projectId, String name, String environment);
    Optional<WebTestDataEntity> findByProjectIdAndNameAndEnvironment(Long projectId, String name, String environment);
}
