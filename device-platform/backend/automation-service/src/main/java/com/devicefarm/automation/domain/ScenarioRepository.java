package com.devicefarm.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScenarioRepository extends JpaRepository<ScenarioEntity, Long> {
    List<ScenarioEntity> findAllByProductIdOrderByUpdatedAtDesc(Long productId);
    List<ScenarioEntity> findAllByProjectIdOrderByUpdatedAtDesc(Long projectId);
}
