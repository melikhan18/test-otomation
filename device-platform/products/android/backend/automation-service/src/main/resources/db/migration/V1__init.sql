-- ┌────────────────────────────────────────────────────────────────────┐
-- │  Element Repository (Page Object)                                  │
-- │  Named UI elements, primary + fallback locators, optional crop.    │
-- └────────────────────────────────────────────────────────────────────┘
CREATE TABLE android_automation.elements (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT       NOT NULL,
    name              VARCHAR(160) NOT NULL,
    description       TEXT,
    primary_strategy  VARCHAR(32)  NOT NULL,                    -- RESOURCE_ID | XPATH | TEXT | CLASS | ACCESSIBILITY_ID
    primary_value     TEXT         NOT NULL,
    fallback_locators TEXT         NOT NULL DEFAULT '[]',       -- JSON array of {strategy,value}
    screenshot_data   TEXT,                                     -- data:image/png;base64,... (small crop)
    sample_bounds     TEXT,                                     -- "[l,t,r,b]" capture-time bounds (for hover preview)
    sample_class      VARCHAR(255),
    sample_text       TEXT,
    sample_resource_id VARCHAR(255),
    created_by_user_id BIGINT       NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, name)
);
CREATE INDEX idx_elements_product ON android_automation.elements(product_id);

-- ┌────────────────────────────────────────────────────────────────────┐
-- │  Test Data — named values, environment-scoped, optional sensitive  │
-- └────────────────────────────────────────────────────────────────────┘
CREATE TABLE android_automation.test_data (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT       NOT NULL,
    name              VARCHAR(160) NOT NULL,
    environment       VARCHAR(32)  NOT NULL DEFAULT 'default',  -- default | dev | staging | prod | <custom>
    value             TEXT         NOT NULL,
    description       TEXT,
    sensitive         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_by_user_id BIGINT       NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (product_id, name, environment)
);
CREATE INDEX idx_test_data_product_env ON android_automation.test_data(product_id, environment);

-- ┌────────────────────────────────────────────────────────────────────┐
-- │  Suites — group of scenarios                                       │
-- └────────────────────────────────────────────────────────────────────┘
CREATE TABLE android_automation.suites (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT       NOT NULL,
    name              VARCHAR(255) NOT NULL,
    description       TEXT,
    tags              TEXT[]       NOT NULL DEFAULT ARRAY[]::TEXT[],
    created_by_user_id BIGINT       NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_suites_product ON android_automation.suites(product_id);

-- ┌────────────────────────────────────────────────────────────────────┐
-- │  Scenarios — sequence of test steps                                │
-- └────────────────────────────────────────────────────────────────────┘
CREATE TABLE android_automation.scenarios (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT       NOT NULL,
    name              VARCHAR(255) NOT NULL,
    description       TEXT,
    tags              TEXT[]       NOT NULL DEFAULT ARRAY[]::TEXT[],
    preconditions     TEXT,
    version           INT          NOT NULL DEFAULT 1,
    created_by_user_id BIGINT       NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_scenarios_product ON android_automation.scenarios(product_id);

-- ┌────────────────────────────────────────────────────────────────────┐
-- │  Steps — atomic actions inside a scenario                          │
-- └────────────────────────────────────────────────────────────────────┘
CREATE TABLE android_automation.steps (
    id                BIGSERIAL PRIMARY KEY,
    scenario_id       BIGINT       NOT NULL REFERENCES android_automation.scenarios(id) ON DELETE CASCADE,
    order_index       INT          NOT NULL,
    action            VARCHAR(32)  NOT NULL,                    -- CLICK | LONG_PRESS | SWIPE | ENTER_TEXT | CLEAR |
                                                                -- PRESS_KEY | WAIT_FOR_VISIBLE | SLEEP |
                                                                -- ASSERT_VISIBLE | ASSERT_TEXT | SCREENSHOT | COMMENT
    target_element_id BIGINT       REFERENCES android_automation.elements(id) ON DELETE SET NULL,
    data_id           BIGINT       REFERENCES android_automation.test_data(id) ON DELETE SET NULL,
    literal_value     TEXT,
    timeout_ms        INT          NOT NULL DEFAULT 5000,
    retry_count       INT          NOT NULL DEFAULT 0,
    screenshot_after  BOOLEAN      NOT NULL DEFAULT FALSE,
    flow_meta         TEXT         NOT NULL DEFAULT '{}',       -- JSON for if/else/loop refs
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_steps_scenario_order ON android_automation.steps(scenario_id, order_index);

-- ┌────────────────────────────────────────────────────────────────────┐
-- │  Suite ↔ Scenario  (M:N, ordered within a suite)                   │
-- └────────────────────────────────────────────────────────────────────┘
CREATE TABLE android_automation.suite_scenarios (
    suite_id          BIGINT       NOT NULL REFERENCES android_automation.suites(id)    ON DELETE CASCADE,
    scenario_id       BIGINT       NOT NULL REFERENCES android_automation.scenarios(id) ON DELETE CASCADE,
    order_index       INT          NOT NULL,
    PRIMARY KEY (suite_id, scenario_id)
);
CREATE INDEX idx_suite_scenarios_order ON android_automation.suite_scenarios(suite_id, order_index);

-- ┌────────────────────────────────────────────────────────────────────┐
-- │  Runs — execution instances                                        │
-- └────────────────────────────────────────────────────────────────────┘
CREATE TABLE android_automation.runs (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT       NOT NULL,
    suite_id          BIGINT       REFERENCES android_automation.suites(id) ON DELETE SET NULL,
    scenario_id       BIGINT       REFERENCES android_automation.scenarios(id) ON DELETE SET NULL,
    scenario_version  INT,
    device_id         BIGINT,
    session_id        BIGINT,
    environment       VARCHAR(32)  NOT NULL DEFAULT 'default',
    status            VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',   -- QUEUED | RUNNING | PASSED | FAILED | ERROR | CANCELLED
    trigger_type      VARCHAR(16)  NOT NULL DEFAULT 'MANUAL',   -- MANUAL | SCHEDULED | API
    triggered_by_user_id BIGINT    NOT NULL,
    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    duration_ms       INT,
    total_steps       INT          NOT NULL DEFAULT 0,
    passed_steps      INT          NOT NULL DEFAULT 0,
    failed_steps      INT          NOT NULL DEFAULT 0,
    error_summary     TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_runs_product_status ON android_automation.runs(product_id, status);
CREATE INDEX idx_runs_scenario       ON android_automation.runs(scenario_id);
CREATE INDEX idx_runs_created        ON android_automation.runs(created_at DESC);

-- ┌────────────────────────────────────────────────────────────────────┐
-- │  Step Results — per-step outcome inside a run                      │
-- └────────────────────────────────────────────────────────────────────┘
CREATE TABLE android_automation.step_results (
    id                BIGSERIAL PRIMARY KEY,
    run_id            BIGINT       NOT NULL REFERENCES android_automation.runs(id) ON DELETE CASCADE,
    step_id           BIGINT,
    order_index       INT          NOT NULL,
    action            VARCHAR(32)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,                    -- PENDING | RUNNING | PASSED | FAILED | SKIPPED | ERROR
    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    duration_ms       INT,
    error_message     TEXT,
    screenshot_url    TEXT,
    resolved_locator  TEXT,                                     -- which strategy succeeded (self-heal trace)
    retries_used      INT          NOT NULL DEFAULT 0
);
CREATE INDEX idx_step_results_run ON android_automation.step_results(run_id, order_index);
