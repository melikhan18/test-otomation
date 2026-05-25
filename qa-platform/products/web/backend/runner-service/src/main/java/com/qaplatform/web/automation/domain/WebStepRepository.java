package com.qaplatform.web.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WebStepRepository extends JpaRepository<WebStepEntity, Long> {

    /**
     * Every step in a scenario, ordered for naive flat consumption. Still
     * used by the legacy code paths (run summary pre-population, step count
     * for the scenario header) where the tree shape doesn't matter — just
     * the count and ids of every step regardless of nesting.
     */
    List<WebStepEntity> findAllByScenarioIdOrderByOrderIndexAsc(Long scenarioId);

    long countByScenarioId(Long scenarioId);

    /**
     * Root-level steps (the outermost scenario body — anything outside any
     * IF block). The tree walker starts here and recurses into IF children
     * via {@link #findAllByParentStepIdAndBranchLabelOrderByOrderIndexAsc}.
     */
    @Query("""
            SELECT s FROM WebStepEntity s
             WHERE s.scenarioId = :scenarioId
               AND s.parentStepId IS NULL
             ORDER BY s.orderIndex ASC
            """)
    List<WebStepEntity> findRootStepsByScenarioId(@Param("scenarioId") Long scenarioId);

    /**
     * Children of an IF step inside a specific branch ("then" or "else"),
     * ordered. The executor calls this twice per IF (once for the chosen
     * branch to run, optionally once for the other to mark SKIPPED).
     */
    List<WebStepEntity> findAllByParentStepIdAndBranchLabelOrderByOrderIndexAsc(
            Long parentStepId, String branchLabel);

    /** Count steps inside one branch — used when shifting siblings on insert. */
    long countByParentStepIdAndBranchLabel(Long parentStepId, String branchLabel);

    /**
     * Count of steps at the root of a scenario (parent_step_id IS NULL).
     * Used when shifting siblings on insert at root level.
     */
    @Query("""
            SELECT COUNT(s) FROM WebStepEntity s
             WHERE s.scenarioId = :scenarioId
               AND s.parentStepId IS NULL
            """)
    long countRootStepsByScenarioId(@Param("scenarioId") Long scenarioId);
}
