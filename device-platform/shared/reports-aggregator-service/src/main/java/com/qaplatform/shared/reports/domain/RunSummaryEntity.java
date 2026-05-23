package com.qaplatform.shared.reports.domain;

import com.qaplatform.common.runengine.status.RunStatus;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * One cross-platform run summary. See V1 migration for the column-by-column
 * contract; this class just maps it.
 *
 * <p>{@code platform} stays a {@code String} rather than an enum because new
 * platform stacks plug in by name without recompiling this service.</p>
 */
@Entity
@Table(name = "run_summaries", schema = "reports",
       uniqueConstraints = @UniqueConstraint(columnNames = {"platform", "source_run_id"}))
public class RunSummaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform", nullable = false, length = 16)
    private String platform;

    @Column(name = "source_run_id", nullable = false)
    private Long sourceRunId;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RunStatus status;

    @Column(name = "scenario_name", length = 255)
    private String scenarioName;

    @Column(name = "triggered_by_user_id")
    private Long triggeredByUserId;

    @Column(name = "total_steps", nullable = false)
    private Integer totalSteps = 0;

    @Column(name = "passed_steps", nullable = false)
    private Integer passedSteps = 0;

    @Column(name = "failed_steps", nullable = false)
    private Integer failedSteps = 0;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();

    public RunSummaryEntity() {}

    public Long getId() { return id; }
    public String getPlatform() { return platform; }
    public Long getSourceRunId() { return sourceRunId; }
    public Long getCompanyId() { return companyId; }
    public Long getProjectId() { return projectId; }
    public RunStatus getStatus() { return status; }
    public String getScenarioName() { return scenarioName; }
    public Long getTriggeredByUserId() { return triggeredByUserId; }
    public Integer getTotalSteps() { return totalSteps; }
    public Integer getPassedSteps() { return passedSteps; }
    public Integer getFailedSteps() { return failedSteps; }
    public Long getDurationMs() { return durationMs; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getErrorSummary() { return errorSummary; }
    public Instant getReceivedAt() { return receivedAt; }

    public void setPlatform(String v) { this.platform = v; }
    public void setSourceRunId(Long v) { this.sourceRunId = v; }
    public void setCompanyId(Long v) { this.companyId = v; }
    public void setProjectId(Long v) { this.projectId = v; }
    public void setStatus(RunStatus v) { this.status = v; }
    public void setScenarioName(String v) { this.scenarioName = v; }
    public void setTriggeredByUserId(Long v) { this.triggeredByUserId = v; }
    public void setTotalSteps(Integer v) { this.totalSteps = v; }
    public void setPassedSteps(Integer v) { this.passedSteps = v; }
    public void setFailedSteps(Integer v) { this.failedSteps = v; }
    public void setDurationMs(Long v) { this.durationMs = v; }
    public void setStartedAt(Instant v) { this.startedAt = v; }
    public void setFinishedAt(Instant v) { this.finishedAt = v; }
    public void setErrorSummary(String v) { this.errorSummary = v; }
}
