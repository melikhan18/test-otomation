package com.qaplatform.web.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface WebSuiteScenarioRepository extends JpaRepository<WebSuiteScenarioEntity, Long> {
    List<WebSuiteScenarioEntity> findAllBySuiteIdOrderByOrderIndexAsc(Long suiteId);
    long countBySuiteId(Long suiteId);

    @Transactional
    void deleteAllBySuiteId(Long suiteId);
}
