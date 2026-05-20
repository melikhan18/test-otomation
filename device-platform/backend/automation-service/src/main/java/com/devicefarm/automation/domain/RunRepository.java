package com.devicefarm.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface RunRepository extends JpaRepository<RunEntity, Long> {
    List<RunEntity> findTop200ByProductIdOrderByCreatedAtDesc(Long productId);
    List<RunEntity> findTop50ByProductIdAndScenarioIdOrderByCreatedAtDesc(Long productId, Long scenarioId);
    List<RunEntity> findAllBySuiteRunIdOrderByCreatedAtAsc(Long suiteRunId);
    /** Retention scan — small enough lists at typical retention windows. */
    List<RunEntity> findAllByCreatedAtBefore(Instant cutoff);
}
