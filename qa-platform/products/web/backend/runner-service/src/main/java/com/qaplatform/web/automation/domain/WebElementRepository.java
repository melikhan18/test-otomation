package com.qaplatform.web.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebElementRepository extends JpaRepository<WebElementEntity, Long> {
    List<WebElementEntity> findAllByProjectIdOrderByUpdatedAtDesc(Long projectId);
    boolean existsByProjectIdAndName(Long projectId, String name);
}
