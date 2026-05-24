-- ┌──────────────────────────────────────────────────────────────────────┐
-- │  V2 — element catalog + test data + suites                            │
-- │                                                                       │
-- │  Brings the web stack to parity with Android's automation flow:       │
-- │  reusable named locators, env-scoped fixtures, ordered scenario       │
-- │  bundles ("suites"). Without these, every step has to carry its       │
-- │  selector + literal value inline — fine for smoke tests, painful      │
-- │  at scale.                                                            │
-- │                                                                       │
-- │  v1 steps stored selector + value as literal strings. v2 adds         │
-- │  optional target_element_id + data_id pointers; the executor          │
-- │  prefers the catalog ref when set, falls back to the literals         │
-- │  otherwise. No data migration needed — existing v1 rows keep using    │
-- │  the literal columns.                                                 │
-- └──────────────────────────────────────────────────────────────────────┘

-- ─── elements ─────────────────────────────────────────────────────────
-- A named locator the user has captured (today: typed manually from
-- browser DevTools; later: picked from a live preview pane).
-- `primary_strategy` mirrors Android's: CSS / XPATH / ROLE / TEXT / TEST_ID
-- — Playwright accepts all of these via `page.locator()` natively, so the
-- executor just passes `primary_value` through. Fallbacks are tried in
-- order if the primary doesn't match in time (self-healing locator).
CREATE TABLE web_automation.elements (
    id                  BIGSERIAL    PRIMARY KEY,
    project_id          BIGINT       NOT NULL,
    name                VARCHAR(160) NOT NULL,
    description         TEXT,
    primary_strategy    VARCHAR(32)  NOT NULL,
    primary_value       TEXT         NOT NULL,
    -- JSON array of {strategy, value} pairs. Service layer (de)serialises.
    fallback_locators   TEXT         NOT NULL DEFAULT '[]',
    created_by_user_id  BIGINT       NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uniq_web_elements_project_name
    ON web_automation.elements (project_id, name);

-- ─── test_data ────────────────────────────────────────────────────────
-- Per (project, environment, name) lookup values. Same shape as Android.
CREATE TABLE web_automation.test_data (
    id                  BIGSERIAL    PRIMARY KEY,
    project_id          BIGINT       NOT NULL,
    name                VARCHAR(160) NOT NULL,
    environment         VARCHAR(32)  NOT NULL DEFAULT 'default',
    value               TEXT         NOT NULL,
    description         TEXT,
    sensitive           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by_user_id  BIGINT       NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uniq_web_test_data_project_name_env
    ON web_automation.test_data (project_id, name, environment);

-- ─── steps ALTER: add catalog refs alongside the v1 literals ──────────
-- Both are optional. Executor preference (later codified in WebStepExecutor):
--   target_element_id  > selector (literal)
--   data_id            > value    (literal)
-- Old v1 rows keep using their literal columns; new rows from the
-- catalog-aware UI populate the FK columns instead.
ALTER TABLE web_automation.steps
    ADD COLUMN target_element_id BIGINT REFERENCES web_automation.elements(id) ON DELETE SET NULL,
    ADD COLUMN data_id           BIGINT REFERENCES web_automation.test_data(id) ON DELETE SET NULL;
CREATE INDEX idx_web_steps_target_element ON web_automation.steps (target_element_id) WHERE target_element_id IS NOT NULL;
CREATE INDEX idx_web_steps_data           ON web_automation.steps (data_id)           WHERE data_id IS NOT NULL;

-- ─── suites ──────────────────────────────────────────────────────────
-- Named bundle of scenarios run sequentially on the same browser type.
-- One ScenarioRun per child; one SuiteRun aggregating their statuses.
CREATE TABLE web_automation.suites (
    id                  BIGSERIAL    PRIMARY KEY,
    project_id          BIGINT       NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    tags                TEXT[]       NOT NULL DEFAULT '{}',
    created_by_user_id  BIGINT       NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_web_suites_project ON web_automation.suites (project_id, updated_at DESC);

-- ─── suite_scenarios ─────────────────────────────────────────────────
-- Ordered M:N join. CASCADE keeps suite cleanups simple; SET NULL on
-- scenario delete would orphan rows — we'd rather break suites visibly
-- so the UI can prompt the user to remove the missing entry.
CREATE TABLE web_automation.suite_scenarios (
    id           BIGSERIAL    PRIMARY KEY,
    suite_id     BIGINT       NOT NULL REFERENCES web_automation.suites(id)    ON DELETE CASCADE,
    scenario_id  BIGINT       NOT NULL REFERENCES web_automation.scenarios(id) ON DELETE CASCADE,
    order_index  INT          NOT NULL
);
CREATE INDEX idx_web_suite_scenarios_suite_order
    ON web_automation.suite_scenarios (suite_id, order_index);

-- ─── suite_runs ──────────────────────────────────────────────────────
-- Suite-level aggregate. Same RunStatus + SuiteRunStatus enum families
-- the cross-platform reports aggregator already consumes for Android.
CREATE TABLE web_automation.suite_runs (
    id                     BIGSERIAL    PRIMARY KEY,
    project_id             BIGINT       NOT NULL,
    suite_id               BIGINT       REFERENCES web_automation.suites(id) ON DELETE SET NULL,
    suite_name             TEXT,
    browser_profile_id     VARCHAR(64)  NOT NULL,
    environment            VARCHAR(32)  NOT NULL DEFAULT 'default',
    status                 VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',
    triggered_by_user_id   BIGINT       NOT NULL,
    started_at             TIMESTAMPTZ,
    finished_at            TIMESTAMPTZ,
    duration_ms            INT,
    total_scenarios        INT          NOT NULL DEFAULT 0,
    passed_scenarios       INT          NOT NULL DEFAULT 0,
    failed_scenarios       INT          NOT NULL DEFAULT 0,
    error_summary          TEXT,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_web_suite_runs_project_created
    ON web_automation.suite_runs (project_id, created_at DESC);

-- Add suite_run_id backlink on runs so child runs can be queried under
-- their parent suite without a join.
ALTER TABLE web_automation.runs
    ADD COLUMN suite_run_id BIGINT REFERENCES web_automation.suite_runs(id) ON DELETE SET NULL;
CREATE INDEX idx_web_runs_suite_run
    ON web_automation.runs (suite_run_id) WHERE suite_run_id IS NOT NULL;
