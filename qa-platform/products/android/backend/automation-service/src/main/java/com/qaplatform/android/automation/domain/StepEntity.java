package com.qaplatform.android.automation.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "steps", schema = "android_automation")
public class StepEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_id", nullable = false)       private Long scenarioId;
    @Column(name = "order_index", nullable = false)       private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StepAction action;

    @Column(name = "target_element_id")    private Long targetElementId;
    @Column(name = "data_id")              private Long dataId;
    @Column(name = "literal_value", columnDefinition = "TEXT") private String literalValue;

    /** Xray-style expected outcome — documentation only, not executed at runtime. */
    @Column(name = "expected_result", columnDefinition = "TEXT") private String expectedResult;

    @Column(name = "timeout_ms", nullable = false)         private int timeoutMs = 5000;
    @Column(name = "retry_count", nullable = false)        private int retryCount = 0;
    @Column(name = "screenshot_after", nullable = false)   private boolean screenshotAfter = false;

    /** Tree-model fields. A root-level step has both NULL — matches every
     *  legacy step that existed before V15. A child step (inside an IF
     *  branch) carries both: the parent step's id and which branch
     *  ("then" / "else") it belongs to. */
    @Column(name = "parent_step_id")
    private Long parentStepId;

    @Column(name = "branch_label", length = 8)
    private String branchLabel;

    /** Predicate JSON for IF rows. Shape = {@code StepCondition} record. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition", columnDefinition = "JSONB")
    private String conditionJson;

    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();

    protected StepEntity() {}

    public StepEntity(Long scenarioId, int orderIndex, StepAction action) {
        this.scenarioId = scenarioId;
        this.orderIndex = orderIndex;
        this.action = action;
    }

    public Long getId() { return id; }
    public Long getScenarioId() { return scenarioId; }
    public int getOrderIndex() { return orderIndex; }       public void setOrderIndex(int v) { this.orderIndex = v; }
    public StepAction getAction() { return action; }        public void setAction(StepAction v) { this.action = v; }
    public Long getTargetElementId() { return targetElementId; } public void setTargetElementId(Long v) { this.targetElementId = v; }
    public Long getDataId() { return dataId; }              public void setDataId(Long v) { this.dataId = v; }
    public String getLiteralValue() { return literalValue; } public void setLiteralValue(String v) { this.literalValue = v; }
    public String getExpectedResult() { return expectedResult; } public void setExpectedResult(String v) { this.expectedResult = v; }
    public int getTimeoutMs() { return timeoutMs; }          public void setTimeoutMs(int v) { this.timeoutMs = v; }
    public int getRetryCount() { return retryCount; }        public void setRetryCount(int v) { this.retryCount = v; }
    public boolean isScreenshotAfter() { return screenshotAfter; } public void setScreenshotAfter(boolean v) { this.screenshotAfter = v; }
    public Long getParentStepId() { return parentStepId; }   public void setParentStepId(Long v) { this.parentStepId = v; }
    public String getBranchLabel() { return branchLabel; }   public void setBranchLabel(String v) { this.branchLabel = v; }
    public String getConditionJson() { return conditionJson; } public void setConditionJson(String v) { this.conditionJson = v; }
    public Instant getCreatedAt() { return createdAt; }
}
