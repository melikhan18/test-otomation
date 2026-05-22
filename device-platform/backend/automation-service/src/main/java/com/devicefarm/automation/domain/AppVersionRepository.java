package com.devicefarm.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppVersionRepository extends JpaRepository<AppVersionEntity, Long> {

    List<AppVersionEntity> findAllByAppIdOrderByVersionCodeDesc(Long appId);

    Optional<AppVersionEntity> findByAppIdAndVersionCode(Long appId, Long versionCode);

    Optional<AppVersionEntity> findFirstByAppIdOrderByVersionCodeDesc(Long appId);

    long countByAppId(Long appId);
}
