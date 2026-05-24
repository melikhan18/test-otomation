package com.qaplatform.web.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebStepResultRepository extends JpaRepository<WebStepResultEntity, Long> {
    List<WebStepResultEntity> findAllByRunIdOrderByOrderIndexAsc(Long runId);
}
