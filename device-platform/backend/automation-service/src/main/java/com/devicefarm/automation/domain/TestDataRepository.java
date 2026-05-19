package com.devicefarm.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TestDataRepository extends JpaRepository<TestDataEntity, Long> {
    Optional<TestDataEntity> findByProductIdAndNameAndEnvironment(Long productId, String name, String environment);
    boolean existsByProductIdAndNameAndEnvironment(Long productId, String name, String environment);

    List<TestDataEntity> findAllByProductIdOrderByNameAscEnvironmentAsc(Long productId);
    List<TestDataEntity> findAllByProductIdAndEnvironmentOrderByNameAsc(Long productId, String environment);

    /** Distinct environments configured under a product — drives the env selector. */
    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT t.environment FROM TestDataEntity t WHERE t.productId = :productId ORDER BY t.environment"
    )
    List<String> findDistinctEnvironments(@org.springframework.data.repository.query.Param("productId") Long productId);
}
