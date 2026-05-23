package com.devicefarm.automation.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "runs", schema = "automation")
public class RunEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false) private Long productId;
    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "suite_id")     private Long suiteId;
    /** Backlink when this run was triggered as part of a {@link SuiteRunEntity}. */
    @Column(name = "suite_run_id") private Long suiteRunId;
    @Column(name = "scenario_id")  private Long scenarioId;

    @Column(name = "scenario_version") private Integer scenarioVersion;
    @Column(name = "device_id")        private Long deviceId;
    @Column(name = "session_id")       private Long sessionId;

    @Column(nullable = false, length = 32)
    private String environment = "default";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RunStatus status = RunStatus.QUEUED;

    @Column(name = "trigger_type", nullable = false, length = 16)
    private String triggerType = "MANUAL";

    @Column(name = "triggered_by_user_id", nullable = false)
    private Long triggeredByUserId;

    @Column(name = "started_at")  private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "duration_ms") private Integer durationMs;

    @Column(name = "total_steps",  nullable = false) private int totalSteps  = 0;
    @Column(name = "passed_steps", nullable = false) private int passedSteps = 0;
    @Column(name = "failed_steps", nullable = false) private int failedSteps = 0;

    @Column(name = "error_summary", columnDefinition = "TEXT") private String errorSummary;

    /** Public URL of the recorded MP4 (set by orchestrator after upload to MinIO). */
    @Column(name = "video_url", columnDefinition = "TEXT")
    private String videoUrl;

    /** Sleep applied between every step. 0 = no pacing. */
    @Column(name = "inter_step_delay_ms", nullable = false)
    private int interStepDelayMs = 500;

    /** When true, poll the inspect tree until it stabilizes instead of using the fixed pause. */
    @Column(name = "adaptive_wait", nullable = false)
    private boolean adaptiveWait = false;

    /** Free-form labels for filtering & grouping in the reports feed. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "TEXT[]", nullable = false)
    private String[] tags = new String[0];

    /** Optional target APK. When null, the orchestrator skips the app preparation phase. */
    @Column(name = "target_app_version_id")
    private Long targetAppVersionId;

    /** Press HOME at the end of the run so the next test starts from a clean state. */
    @Column(name = "reset_home_after", nullable = false)
    private boolean resetHomeAfter = true;

    /** Also force-stop the target app (only effective on Device Owner cihazda). */
    @Column(name = "kill_process_after", nullable = false)
    private boolean killProcessAfter = false;

    /** Outcome of the pre-step app prep phase. See V12 migration comment for valid values. */
    @Column(name = "app_prep_status")
    private String appPrepStatus;

    @Column(name = "app_prep_duration_ms")
    private Integer appPrepDurationMs;

    @Column(name = "app_prep_error", columnDefinition = "TEXT")
    private String appPrepError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RunEntity() {}

    public RunEntity(Long productId, Long projectId, Long scenarioId, Long deviceId,
                     Long triggeredByUserId, String environment) {
        this.productId = productId;
        this.projectId = projectId;
        this.scenarioId = scenarioId;
        this.deviceId = deviceId;
        this.triggeredByUserId = triggeredByUserId;
        if (environment != null) this.environment = environment;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Long getProjectId() { return projectId; }
    public Long getSuiteId() { return suiteId; }              public void setSuiteId(Long v) { this.suiteId = v; }
    public Long getSuiteRunId() { return suiteRunId; }        public void setSuiteRunId(Long v) { this.suiteRunId = v; }
    public Long getScenarioId() { return scenarioId; }
    public Integer getScenarioVersion() { return scenarioVersion; } public void setScenarioVersion(Integer v) { this.scenarioVersion = v; }
    public Long getDeviceId() { return deviceId; }
    public Long getSessionId() { return sessionId; }          public void setSessionId(Long v) { this.sessionId = v; }
    public String getEnvironment() { return environment; }
    public RunStatus getStatus() { return status; }           public void setStatus(RunStatus v) { this.status = v; }
    public String getTriggerType() { return triggerType; }    public void setTriggerType(String v) { this.triggerType = v; }
    public Long getTriggeredByUserId() { return triggeredByUserId; }
    public Instant getStartedAt() { return startedAt; }       public void setStartedAt(Instant v) { this.startedAt = v; }
    public Instant getFinishedAt() { return finishedAt; }     public void setFinishedAt(Instant v) { this.finishedAt = v; }
    public Integer getDurationMs() { return durationMs; }     public void setDurationMs(Integer v) { this.durationMs = v; }
    public int getTotalSteps() { return totalSteps; }         public void setTotalSteps(int v) { this.totalSteps = v; }
    public int getPassedSteps() { return passedSteps; }       public void setPassedSteps(int v) { this.passedSteps = v; }
    public int getFailedSteps() { return failedSteps; }       public void setFailedSteps(int v) { this.failedSteps = v; }
    public String getErrorSummary() { return errorSummary; }  public void setErrorSummary(String v) { this.errorSummary = v; }
    public String getVideoUrl() { return videoUrl; }          public void setVideoUrl(String v) { this.videoUrl = v; }
    public int getInterStepDelayMs() { return interStepDelayMs; } public void setInterStepDelayMs(int v) { this.interStepDelayMs = v; }
    public boolean isAdaptiveWait() { return adaptiveWait; }      public void setAdaptiveWait(boolean v) { this.adaptiveWait = v; }
    public String[] getTags() { return tags; }                    public void setTags(String[] v) { this.tags = v == null ? new String[0] : v; }
    public Long getTargetAppVersionId() { return targetAppVersionId; } public void setTargetAppVersionId(Long v) { this.targetAppVersionId = v; }
    public boolean isResetHomeAfter() { return resetHomeAfter; }       public void setResetHomeAfter(boolean v) { this.resetHomeAfter = v; }
    public boolean isKillProcessAfter() { return killProcessAfter; }   public void setKillProcessAfter(boolean v) { this.killProcessAfter = v; }
    public String getAppPrepStatus() { return appPrepStatus; }         public void setAppPrepStatus(String v) { this.appPrepStatus = v; }
    public Integer getAppPrepDurationMs() { return appPrepDurationMs; } public void setAppPrepDurationMs(Integer v) { this.appPrepDurationMs = v; }
    public String getAppPrepError() { return appPrepError; }           public void setAppPrepError(String v) { this.appPrepError = v; }
    public Instant getCreatedAt() { return createdAt; }
}
