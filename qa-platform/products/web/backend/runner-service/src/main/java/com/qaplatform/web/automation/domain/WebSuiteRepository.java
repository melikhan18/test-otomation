package com.qaplatform.web.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebSuiteRepository extends JpaRepository<WebSuiteEntity, Long> {
    List<WebSuiteEntity> findAllByProjectIdOrderByUpdatedAtDesc(Long projectId);
}
