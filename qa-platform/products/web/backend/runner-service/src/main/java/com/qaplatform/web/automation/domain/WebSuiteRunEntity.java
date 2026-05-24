package com.qaplatform.web.automation.domain;

import com.qaplatform.common.runengine.status.SuiteRunStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "suite_runs", schema = "web_automation")
public class WebSuiteRunEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false) private Long projectId;
    @Column(name = "suite_id")                     private Long suiteId;
    @Column(name = "suite_name", columnDefinition = "TEXT") private String suiteName;

    @Column(name = "browser_profile_id", nullable = false, length = 64)
    private String browserProfileId;

    @Column(nullable = false, length = 32) private String environment = "default";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SuiteRunStatus status = SuiteRunStatus.QUEUED;

    @Column(name = "triggered_by_user_id", nullable = false) private Long triggeredByUserId;

    @Column(name = "started_at")  private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "duration_ms") private Integer durationMs;

    @Column(name = "total_scenarios",  nullable = false) private int totalScenarios  = 0;
    @Column(name = "passed_scenarios", nullable = false) private int passedScenarios = 0;
    @Column(name = "failed_scenarios", nullable = false) private int failedScenarios = 0;

    @Column(name = "error_summary", columnDefinition = "TEXT") private String errorSummary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected WebSuiteRunEntity() {}

    public WebSuiteRunEntity(Long projectId, Long suiteId, String suiteName, String browserProfileId,
                             Long triggeredByUserId, String environment) {
        this.projectId = projectId;
        this.suiteId = suiteId;
        this.suiteName = suiteName;
        this.browserProfileId = browserProfileId;
        this.triggeredByUserId = triggeredByUserId;
        if (environment != null) this.environment = environment;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getSuiteId() { return suiteId; }
    public String getSuiteName() { return suiteName; }            public void setSuiteName(String v) { this.suiteName = v; }
    public String getBrowserProfileId() { return browserProfileId; }
    public String getEnvironment() { return environment; }
    public SuiteRunStatus getStatus() { return status; }          public void setStatus(SuiteRunStatus v) { this.status = v; }
    public Long getTriggeredByUserId() { return triggeredByUserId; }
    public Instant getStartedAt() { return startedAt; }           public void setStartedAt(Instant v) { this.startedAt = v; }
    public Instant getFinishedAt() { return finishedAt; }         public void setFinishedAt(Instant v) { this.finishedAt = v; }
    public Integer getDurationMs() { return durationMs; }         public void setDurationMs(Integer v) { this.durationMs = v; }
    public int getTotalScenarios() { return totalScenarios; }     public void setTotalScenarios(int v) { this.totalScenarios = v; }
    public int getPassedScenarios() { return passedScenarios; }   public void setPassedScenarios(int v) { this.passedScenarios = v; }
    public int getFailedScenarios() { return failedScenarios; }   public void setFailedScenarios(int v) { this.failedScenarios = v; }
    public String getErrorSummary() { return errorSummary; }      public void setErrorSummary(String v) { this.errorSummary = v; }
    public Instant getCreatedAt() { return createdAt; }
}
