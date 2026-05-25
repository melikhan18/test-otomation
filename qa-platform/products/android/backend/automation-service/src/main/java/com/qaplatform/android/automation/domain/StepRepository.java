package com.qaplatform.android.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface StepRepository extends JpaRepository<StepEntity, Long> {
    /** All steps in a scenario, ordered flat. Still used for counts +
     *  pre-populating step result placeholders (tree shape doesn't matter
     *  there — just the set). */
    List<StepEntity> findAllByScenarioIdOrderByOrderIndexAsc(Long scenarioId);

    /**
     * Bulk-delete every step in a scenario in one SQL statement, bypassing
     * Hibernate's per-entity remove() loop. The default Spring Data derived
     * delete (`deleteAllBy*`) does SELECT + loop + remove() per row, which
     * blows up with StaleObjectStateException now that V15 added a
     * self-referencing `parent_step_id ON DELETE CASCADE`: the first IF
     * row's removal cascades children at the DB level, then the loop
     * tries to remove the children again. A single bulk DELETE lets
     * Postgres handle the cascade once, atomically.
     *
     * <p>flushAutomatically: pending session writes (e.g. an in-flight
     * step update) are flushed before the delete so they hit the wire
     * first. clearAutomatically: detached step entities are evicted
     * after the delete so subsequent operations don't see stale state.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM StepEntity s WHERE s.scenarioId = :scenarioId")
    @Transactional
    void deleteAllByScenarioId(@Param("scenarioId") Long scenarioId);

    long countByScenarioId(Long scenarioId);

    long countByTargetElementId(Long elementId);
    long countByDataId(Long dataId);

    /** Root-level steps (outside any IF). Walker entry point. */
    @Query("""
            SELECT s FROM StepEntity s
             WHERE s.scenarioId = :scenarioId
               AND s.parentStepId IS NULL
             ORDER BY s.orderIndex ASC
            """)
    List<StepEntity> findRootStepsByScenarioId(@Param("scenarioId") Long scenarioId);

    /** Children of an IF, scoped to a single branch ("then" / "else"). */
    List<StepEntity> findAllByParentStepIdAndBranchLabelOrderByOrderIndexAsc(
            Long parentStepId, String branchLabel);

    long countByParentStepIdAndBranchLabel(Long parentStepId, String branchLabel);

    @Query("""
            SELECT COUNT(s) FROM StepEntity s
             WHERE s.scenarioId = :scenarioId
               AND s.parentStepId IS NULL
            """)
    long countRootStepsByScenarioId(@Param("scenarioId") Long scenarioId);
}
