package com.devicefarm.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppRepository extends JpaRepository<AppEntity, Long> {

    List<AppEntity> findAllByProjectIdAndArchivedAtIsNullOrderByUpdatedAtDesc(Long projectId);

    Optional<AppEntity> findByProjectIdAndPackageNameAndArchivedAtIsNull(Long projectId, String packageName);
}
