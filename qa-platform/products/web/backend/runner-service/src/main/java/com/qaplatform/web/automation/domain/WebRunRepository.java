package com.qaplatform.web.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebRunRepository extends JpaRepository<WebRunEntity, Long> {
    List<WebRunEntity> findTop200ByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<WebRunEntity> findTop50ByProjectIdAndScenarioIdOrderByCreatedAtDesc(Long projectId, Long scenarioId);
    /** Child runs of a suite-run, in execution order. */
    List<WebRunEntity> findAllBySuiteRunIdOrderByCreatedAtAsc(Long suiteRunId);
}
