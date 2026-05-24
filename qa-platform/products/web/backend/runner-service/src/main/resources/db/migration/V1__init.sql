-- ┌──────────────────────────────────────────────────────────────────────┐
-- │  web_automation — initial schema for the Playwright-based web stack   │
-- │                                                                       │
-- │  Mirrors Android's pre-Suite shape (scenarios → steps → runs →        │
-- │  step_results) but drops everything that doesn't apply to web:        │
-- │                                                                       │
-- │   - No `devices` table — "device" in web is a static browser profile  │
-- │     (chromium-1080p, webkit-mobile-iphone14, …), config not data.     │
-- │   - No `apps` / app_versions — there's no APK install phase for web.  │
-- │   - No `sessions` — runs are ephemeral; each spawns its own Playwright │
-- │     Browser + BrowserContext, kills them at the end, no reservation   │
-- │     lock.                                                              │
-- │   - No `elements` / `test_data` in v1 — steps carry literal selectors │
-- │     + values inline. Catalog tables come in a later faz.              │
-- │                                                                       │
-- │  Status enums (`status`, `step_results.status`) reuse the common      │
-- │  RunStatus / StepResultStatus from F6 so the reports-aggregator can   │
-- │  count cross-platform without translation.                            │
-- └──────────────────────────────────────────────────────────────────────┘

-- ─── scenarios ────────────────────────────────────────────────────────
CREATE TABLE web_automation.scenarios (
    id                   BIGSERIAL    PRIMARY KEY,
    project_id           BIGINT       NOT NULL,
    name                 VARCHAR(255) NOT NULL,
    description          TEXT,
    tags                 TEXT[]       NOT NULL DEFAULT '{}',
    version              INT          NOT NULL DEFAULT 1,
    created_by_user_id   BIGINT       NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_web_scenarios_project ON web_automation.scenarios (project_id, updated_at DESC);

-- ─── steps (children of scenarios) ────────────────────────────────────
-- v1 step set is opinionated:
--   Navigation:   GOTO, RELOAD, GO_BACK, GO_FORWARD
--   Interaction:  CLICK, DBL_CLICK, FILL, PRESS_KEY, CHECK, UNCHECK,
--                 SELECT, HOVER
--   Wait:         WAIT_FOR_SELECTOR, WAIT_FOR_LOAD_STATE, SLEEP
--   Assert:       ASSERT_VISIBLE, ASSERT_HIDDEN, ASSERT_TEXT_EQUALS,
--                 ASSERT_TEXT_CONTAINS, ASSERT_URL_EQUALS, ASSERT_URL_CONTAINS,
--                 ASSERT_TITLE_EQUALS, ASSERT_TITLE_CONTAINS, ASSERT_ATTRIBUTE
--   Util:         SCREENSHOT, COMMENT, EVAL_JS
--
-- `selector` is a Playwright locator string (CSS, XPath, role-based syntax,
-- or text= shorthand). `value` is the action argument (URL for GOTO, text
-- for FILL, expected value for ASSERT_*).
CREATE TABLE web_automation.steps (
    id                BIGSERIAL    PRIMARY KEY,
    scenario_id       BIGINT       NOT NULL REFERENCES web_automation.scenarios(id) ON DELETE CASCADE,
    order_index       INT          NOT NULL,
    action            VARCHAR(32)  NOT NULL,
    selector          TEXT,
    value             TEXT,
    timeout_ms        INT          NOT NULL DEFAULT 5000,
    screenshot_after  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_web_steps_scenario_order ON web_automation.steps (scenario_id, order_index);

-- ─── runs ─────────────────────────────────────────────────────────────
-- One row per scenario execution. `browser_profile_id` references the
-- static JSON catalog at app.browser/profiles (e.g. "desktop-chrome-1080p");
-- no FK because the catalog is config, not data.
CREATE TABLE web_automation.runs (
    id                     BIGSERIAL    PRIMARY KEY,
    project_id             BIGINT       NOT NULL,
    scenario_id            BIGINT       REFERENCES web_automation.scenarios(id) ON DELETE SET NULL,
    scenario_version       INT,
    browser_profile_id     VARCHAR(64)  NOT NULL,
    environment            VARCHAR(32)  NOT NULL DEFAULT 'default',
    status                 VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',
    triggered_by_user_id   BIGINT       NOT NULL,
    started_at             TIMESTAMPTZ,
    finished_at            TIMESTAMPTZ,
    duration_ms            INT,
    total_steps            INT          NOT NULL DEFAULT 0,
    passed_steps           INT          NOT NULL DEFAULT 0,
    failed_steps           INT          NOT NULL DEFAULT 0,
    error_summary          TEXT,
    video_url              TEXT,
    trace_url              TEXT,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_web_runs_project_created ON web_automation.runs (project_id, created_at DESC);
CREATE INDEX idx_web_runs_scenario        ON web_automation.runs (scenario_id, created_at DESC);

-- ─── step_results (children of runs) ──────────────────────────────────
-- One row per executed step. Created up-front in PENDING state when the
-- run starts, transitions to RUNNING then a terminal status as steps
-- execute (or SKIPPED if an earlier step aborts the run).
CREATE TABLE web_automation.step_results (
    id              BIGSERIAL    PRIMARY KEY,
    run_id          BIGINT       NOT NULL REFERENCES web_automation.runs(id) ON DELETE CASCADE,
    step_id         BIGINT       REFERENCES web_automation.steps(id) ON DELETE SET NULL,
    order_index     INT          NOT NULL,
    action          VARCHAR(32)  NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    duration_ms     INT,
    error_message   TEXT,
    screenshot_url  TEXT
);
CREATE INDEX idx_web_step_results_run_order ON web_automation.step_results (run_id, order_index);
