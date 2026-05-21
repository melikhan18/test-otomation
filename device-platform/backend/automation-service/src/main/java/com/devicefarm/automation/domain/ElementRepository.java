package com.devicefarm.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ElementRepository extends JpaRepository<ElementEntity, Long> {
    Optional<ElementEntity> findByProjectIdAndName(Long projectId, String name);
    boolean existsByProjectIdAndName(Long projectId, String name);

    List<ElementEntity> findAllByProjectIdOrderByNameAsc(Long projectId);

    @Query("""
           SELECT e FROM ElementEntity e
            WHERE e.projectId = :projectId
              AND (LOWER(e.name) LIKE CONCAT('%', LOWER(:q), '%')
                OR LOWER(e.primaryValue) LIKE CONCAT('%', LOWER(:q), '%')
                OR LOWER(COALESCE(e.sampleResourceId, '')) LIKE CONCAT('%', LOWER(:q), '%')
                OR LOWER(COALESCE(e.sampleText, '')) LIKE CONCAT('%', LOWER(:q), '%'))
            ORDER BY e.name ASC
           """)
    List<ElementEntity> searchByProjectId(@Param("projectId") Long projectId, @Param("q") String q);
}
