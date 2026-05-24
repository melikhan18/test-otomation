package com.qaplatform.web.automation.domain;

import com.qaplatform.common.runengine.status.StepResultStatus;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Per-step row inside a run. Created up-front in PENDING when the
 * orchestrator snapshots the scenario plan; mutated in place as steps
 * execute. The {@code action} column is copied from the source
 * {@link WebStepEntity} so a step's display name survives even if the
 * scenario is edited (and the step row's FK is later nulled by the V1
 * {@code ON DELETE SET NULL}).
 */
@Entity
@Table(name = "step_results", schema = "web_automation")
public class WebStepResultEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id",  nullable = false) private Long runId;
    @Column(name = "step_id")                   private Long stepId;
    @Column(name = "order_index", nullable = false) private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WebStepAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StepResultStatus status = StepResultStatus.PENDING;

    @Column(name = "started_at")  private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "duration_ms") private Integer durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "screenshot_url", columnDefinition = "TEXT")
    private String screenshotUrl;

    protected WebStepResultEntity() {}

    public WebStepResultEntity(Long runId, Long stepId, int orderIndex, WebStepAction action) {
        this.runId = runId;
        this.stepId = stepId;
        this.orderIndex = orderIndex;
        this.action = action;
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public Long getStepId() { return stepId; }
    public int getOrderIndex() { return orderIndex; }
    public WebStepAction getAction() { return action; }
    public StepResultStatus getStatus() { return status; }    public void setStatus(StepResultStatus v) { this.status = v; }
    public Instant getStartedAt() { return startedAt; }       public void setStartedAt(Instant v) { this.startedAt = v; }
    public Instant getFinishedAt() { return finishedAt; }     public void setFinishedAt(Instant v) { this.finishedAt = v; }
    public Integer getDurationMs() { return durationMs; }     public void setDurationMs(Integer v) { this.durationMs = v; }
    public String getErrorMessage() { return errorMessage; }  public void setErrorMessage(String v) { this.errorMessage = v; }
    public String getScreenshotUrl() { return screenshotUrl; } public void setScreenshotUrl(String v) { this.screenshotUrl = v; }
}
