package com.qaplatform.android.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TestDataRepository extends JpaRepository<TestDataEntity, Long> {
    Optional<TestDataEntity> findByProjectIdAndNameAndEnvironment(Long projectId, String name, String environment);
    boolean existsByProjectIdAndNameAndEnvironment(Long projectId, String name, String environment);

    List<TestDataEntity> findAllByProjectIdOrderByNameAscEnvironmentAsc(Long projectId);
    List<TestDataEntity> findAllByProjectIdAndEnvironmentOrderByNameAsc(Long projectId, String environment);

    /** Distinct environments configured under a project — drives the env selector. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT t.environment FROM TestDataEntity t WHERE t.projectId = :projectId ORDER BY t.environment"
    )
    List<String> findDistinctEnvironments(@org.springframework.data.repository.query.Param("projectId") Long projectId);
}
