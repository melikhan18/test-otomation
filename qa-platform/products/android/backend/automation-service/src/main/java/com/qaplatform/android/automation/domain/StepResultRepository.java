package com.qaplatform.android.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface StepResultRepository extends JpaRepository<StepResultEntity, Long> {
    List<StepResultEntity> findAllByRunIdOrderByOrderIndexAsc(Long runId);

    @Transactional
    void deleteAllByRunId(Long runId);
}
