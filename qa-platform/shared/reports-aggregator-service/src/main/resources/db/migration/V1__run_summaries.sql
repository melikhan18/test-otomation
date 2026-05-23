-- ┌──────────────────────────────────────────────────────────────────────┐
-- │  reports.run_summaries                                                │
-- │                                                                      │
-- │  One row per terminal run, across every platform. Platforms push     │
-- │  here when a run reaches PASSED/FAILED/ERROR/CANCELLED; the          │
-- │  dashboard reads from here.                                          │
-- │                                                                      │
-- │  (platform, source_run_id) is the natural key — a run lives in its   │
-- │  platform's own schema and we just track a pointer + the rollup.     │
-- │  Idempotent push via ON CONFLICT keeps re-emits cheap.               │
-- └──────────────────────────────────────────────────────────────────────┘

CREATE TABLE reports.run_summaries (
    id                     BIGSERIAL    PRIMARY KEY,
    platform               VARCHAR(16)  NOT NULL,   -- ANDROID | IOS | BACKEND | WEB
    source_run_id          BIGINT       NOT NULL,   -- platform-side run id (e.g. android_automation.runs.id)
    company_id             BIGINT,                  -- nullable in F7 skeleton; tightens when tenancy migration completes
    project_id             BIGINT       NOT NULL,
    status                 VARCHAR(16)  NOT NULL,   -- maps to com.qaplatform.common.runengine.status.RunStatus
    scenario_name          VARCHAR(255),
    triggered_by_user_id   BIGINT,
    total_steps            INT          NOT NULL DEFAULT 0,
    passed_steps           INT          NOT NULL DEFAULT 0,
    failed_steps           INT          NOT NULL DEFAULT 0,
    duration_ms            BIGINT,
    started_at             TIMESTAMPTZ  NOT NULL,
    finished_at            TIMESTAMPTZ,
    error_summary          TEXT,
    received_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (platform, source_run_id)
);

-- Dashboard query: "what failed on this project recently".
CREATE INDEX idx_run_summaries_project_finished
    ON reports.run_summaries (project_id, finished_at DESC NULLS LAST);

-- Cross-platform breakdown: "android vs ios failure rate over a window".
CREATE INDEX idx_run_summaries_platform_status_finished
    ON reports.run_summaries (platform, status, finished_at DESC NULLS LAST);
