package com.qaplatform.web.automation.domain;

import com.qaplatform.common.runengine.status.RunStatus;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * One execution of a {@link WebScenarioEntity} against a single browser
 * profile. No reservation lock — browsers are spawned per-run, not pooled.
 */
@Entity
@Table(name = "runs", schema = "web_automation")
public class WebRunEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "scenario_id")                  private Long scenarioId;
    @Column(name = "scenario_version")             private Integer scenarioVersion;

    /** Backlink when this run was triggered as part of a {@link WebSuiteRunEntity}. */
    @Column(name = "suite_run_id") private Long suiteRunId;

    /** References a static BrowserCatalog entry by id (e.g. "desktop-chrome-1080p"). */
    @Column(name = "browser_profile_id", nullable = false, length = 64)
    private String browserProfileId;

    @Column(nullable = false, length = 32)
    private String environment = "default";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RunStatus status = RunStatus.QUEUED;

    @Column(name = "triggered_by_user_id", nullable = false)
    private Long triggeredByUserId;

    @Column(name = "started_at")  private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "duration_ms") private Integer durationMs;

    @Column(name = "total_steps",  nullable = false) private int totalSteps  = 0;
    @Column(name = "passed_steps", nullable = false) private int passedSteps = 0;
    @Column(name = "failed_steps", nullable = false) private int failedSteps = 0;

    @Column(name = "error_summary", columnDefinition = "TEXT") private String errorSummary;

    /** Public URL of the recorded video (Playwright .webm → MinIO). */
    @Column(name = "video_url", columnDefinition = "TEXT") private String videoUrl;
    /** Public URL of the Playwright trace.zip (loadable in the trace viewer). */
    @Column(name = "trace_url", columnDefinition = "TEXT") private String traceUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected WebRunEntity() {}

    public WebRunEntity(Long projectId, Long scenarioId, String browserProfileId,
                        Long triggeredByUserId, String environment) {
        this.projectId = projectId;
        this.scenarioId = scenarioId;
        this.browserProfileId = browserProfileId;
        this.triggeredByUserId = triggeredByUserId;
        if (environment != null) this.environment = environment;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getScenarioId() { return scenarioId; }
    public Integer getScenarioVersion() { return scenarioVersion; } public void setScenarioVersion(Integer v) { this.scenarioVersion = v; }
    public Long getSuiteRunId() { return suiteRunId; }              public void setSuiteRunId(Long v) { this.suiteRunId = v; }
    public String getBrowserProfileId() { return browserProfileId; }
    public String getEnvironment() { return environment; }
    public RunStatus getStatus() { return status; }                 public void setStatus(RunStatus v) { this.status = v; }
    public Long getTriggeredByUserId() { return triggeredByUserId; }
    public Instant getStartedAt() { return startedAt; }             public void setStartedAt(Instant v) { this.startedAt = v; }
    public Instant getFinishedAt() { return finishedAt; }           public void setFinishedAt(Instant v) { this.finishedAt = v; }
    public Integer getDurationMs() { return durationMs; }           public void setDurationMs(Integer v) { this.durationMs = v; }
    public int getTotalSteps() { return totalSteps; }               public void setTotalSteps(int v) { this.totalSteps = v; }
    public int getPassedSteps() { return passedSteps; }             public void setPassedSteps(int v) { this.passedSteps = v; }
    public int getFailedSteps() { return failedSteps; }             public void setFailedSteps(int v) { this.failedSteps = v; }
    public String getErrorSummary() { return errorSummary; }        public void setErrorSummary(String v) { this.errorSummary = v; }
    public String getVideoUrl() { return videoUrl; }                public void setVideoUrl(String v) { this.videoUrl = v; }
    public String getTraceUrl() { return traceUrl; }                public void setTraceUrl(String v) { this.traceUrl = v; }
    public Instant getCreatedAt() { return createdAt; }
}
