package com.qaplatform.web.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebSuiteRunRepository extends JpaRepository<WebSuiteRunEntity, Long> {
    List<WebSuiteRunEntity> findTop100ByProjectIdOrderByCreatedAtDesc(Long projectId);
    List<WebSuiteRunEntity> findTop50ByProjectIdAndSuiteIdOrderByCreatedAtDesc(Long projectId, Long suiteId);
}
