package com.qaplatform.android.automation.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "step_results", schema = "android_automation")
public class StepResultEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false) private Long runId;
    @Column(name = "step_id")                  private Long stepId;
    @Column(name = "order_index", nullable = false) private int orderIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StepAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StepResultStatus status = StepResultStatus.PENDING;

    @Column(name = "started_at")  private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "duration_ms") private Integer durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "screenshot_url", columnDefinition = "TEXT") private String screenshotUrl;

    @Column(name = "resolved_locator", columnDefinition = "TEXT") private String resolvedLocator;
    @Column(name = "retries_used", nullable = false) private int retriesUsed = 0;

    protected StepResultEntity() {}

    public StepResultEntity(Long runId, Long stepId, int orderIndex, StepAction action) {
        this.runId = runId;
        this.stepId = stepId;
        this.orderIndex = orderIndex;
        this.action = action;
    }

    public Long getId() { return id; }
    public Long getRunId() { return runId; }
    public Long getStepId() { return stepId; }
    public int getOrderIndex() { return orderIndex; }
    public StepAction getAction() { return action; }
    public StepResultStatus getStatus() { return status; }    public void setStatus(StepResultStatus v) { this.status = v; }
    public Instant getStartedAt() { return startedAt; }       public void setStartedAt(Instant v) { this.startedAt = v; }
    public Instant getFinishedAt() { return finishedAt; }     public void setFinishedAt(Instant v) { this.finishedAt = v; }
    public Integer getDurationMs() { return durationMs; }     public void setDurationMs(Integer v) { this.durationMs = v; }
    public String getErrorMessage() { return errorMessage; }  public void setErrorMessage(String v) { this.errorMessage = v; }
    public String getScreenshotUrl() { return screenshotUrl; } public void setScreenshotUrl(String v) { this.screenshotUrl = v; }
    public String getResolvedLocator() { return resolvedLocator; } public void setResolvedLocator(String v) { this.resolvedLocator = v; }
    public int getRetriesUsed() { return retriesUsed; }       public void setRetriesUsed(int v) { this.retriesUsed = v; }
}
