package com.qaplatform.android.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface StepRepository extends JpaRepository<StepEntity, Long> {
    List<StepEntity> findAllByScenarioIdOrderByOrderIndexAsc(Long scenarioId);

    @Transactional
    void deleteAllByScenarioId(Long scenarioId);

    long countByScenarioId(Long scenarioId);

    long countByTargetElementId(Long elementId);
    long countByDataId(Long dataId);
}
