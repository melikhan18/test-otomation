package com.qaplatform.android.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface RunRepository extends JpaRepository<RunEntity, Long> {
    List<RunEntity> findTop200ByProductIdOrderByCreatedAtDesc(Long productId);
    List<RunEntity> findTop200ByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<RunEntity> findTop50ByProjectIdAndScenarioIdOrderByCreatedAtDesc(Long projectId, Long scenarioId);
    List<RunEntity> findAllBySuiteRunIdOrderByCreatedAtAsc(Long suiteRunId);
    /** Retention scan — small enough lists at typical retention windows. */
    List<RunEntity> findAllByCreatedAtBefore(Instant cutoff);
}
