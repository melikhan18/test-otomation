package com.qaplatform.android.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SuiteScenarioRepository
        extends JpaRepository<SuiteScenarioEntity, SuiteScenarioEntity.Key> {

    List<SuiteScenarioEntity> findAllBySuiteIdOrderByOrderIndexAsc(Long suiteId);

    /** Reverse lookup — every suite a scenario currently belongs to. */
    List<SuiteScenarioEntity> findAllByScenarioId(Long scenarioId);

    long countBySuiteId(Long suiteId);
    long countByScenarioId(Long scenarioId);

    @Transactional
    void deleteAllBySuiteId(Long suiteId);

    @Transactional
    void deleteAllByScenarioId(Long scenarioId);
}
