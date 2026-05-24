package com.qaplatform.web.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebScenarioRepository extends JpaRepository<WebScenarioEntity, Long> {
    List<WebScenarioEntity> findAllByProjectIdOrderByUpdatedAtDesc(Long projectId);
}
