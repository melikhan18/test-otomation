package com.qaplatform.android.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuiteRepository extends JpaRepository<SuiteEntity, Long> {
    List<SuiteEntity> findAllByProjectIdOrderByUpdatedAtDesc(Long projectId);
}
