package com.qaplatform.shared.tenant.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectPlatformRepository
        extends JpaRepository<ProjectPlatformEntity, ProjectPlatformEntity.Key> {

    List<ProjectPlatformEntity> findAllByProjectIdOrderByPlatformAsc(Long projectId);

    boolean existsByProjectIdAndPlatform(Long projectId, String platform);

    long deleteByProjectIdAndPlatform(Long projectId, String platform);
}
