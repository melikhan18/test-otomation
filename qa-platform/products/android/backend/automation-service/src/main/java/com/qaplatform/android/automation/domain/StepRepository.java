package com.qaplatform.android.automation.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface StepRepository extends JpaRepository<StepEntity, Long> {
    /** All steps in a scenario, ordered flat. Still used for counts +
     *  pre-populating step result placeholders (tree shape doesn't matter
     *  there — just the set). */
    List<StepEntity> findAllByScenarioIdOrderByOrderIndexAsc(Long scenarioId);

    @Transactional
    void deleteAllByScenarioId(Long scenarioId);

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
