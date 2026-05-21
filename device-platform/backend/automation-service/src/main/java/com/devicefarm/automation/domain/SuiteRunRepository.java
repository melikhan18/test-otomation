package com.devicefarm.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface SuiteRunRepository extends JpaRepository<SuiteRunEntity, Long> {
    List<SuiteRunEntity> findTop100ByProductIdOrderByCreatedAtDesc(Long productId);
    List<SuiteRunEntity> findTop100ByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<SuiteRunEntity> findTop50ByProjectIdAndSuiteIdOrderByCreatedAtDesc(Long projectId, Long suiteId);

    @Transactional
    long deleteAllByCreatedAtBefore(Instant cutoff);
}
