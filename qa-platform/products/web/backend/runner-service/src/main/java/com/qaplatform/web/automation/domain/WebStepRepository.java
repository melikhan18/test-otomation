package com.qaplatform.web.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebStepRepository extends JpaRepository<WebStepEntity, Long> {
    List<WebStepEntity> findAllByScenarioIdOrderByOrderIndexAsc(Long scenarioId);
    long countByScenarioId(Long scenarioId);
}
