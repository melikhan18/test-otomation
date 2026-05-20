-- Suite-level run: a single user click on "Run suite" creates one row here and N child
-- runs (one per scenario). The orchestrator chains the scenarios sequentially on the
-- same device. Aggregate status / counts are rolled up at suite-run finish.
CREATE TABLE automation.suite_runs (
    id                    BIGSERIAL    PRIMARY KEY,
    product_id            BIGINT       NOT NULL,
    suite_id              BIGINT       NOT NULL,
    suite_name            TEXT,
    device_id             BIGINT       NOT NULL,
    environment           VARCHAR(32)  NOT NULL DEFAULT 'default',
    status                VARCHAR(16)  NOT NULL DEFAULT 'QUEUED',
    trigger_type          VARCHAR(16)  NOT NULL DEFAULT 'MANUAL',
    triggered_by_user_id  BIGINT       NOT NULL,
    started_at            TIMESTAMPTZ,
    finished_at           TIMESTAMPTZ,
    duration_ms           INTEGER,
    total_scenarios       INTEGER      NOT NULL DEFAULT 0,
    passed_scenarios      INTEGER      NOT NULL DEFAULT 0,
    failed_scenarios      INTEGER      NOT NULL DEFAULT 0,
    error_summary         TEXT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX suite_runs_product_created_idx
    ON automation.suite_runs (product_id, created_at DESC);
CREATE INDEX suite_runs_suite_created_idx
    ON automation.suite_runs (suite_id, created_at DESC);

-- Backlink on each child run. Nullable: a one-off "run scenario" stays standalone.
ALTER TABLE automation.runs
    ADD COLUMN suite_run_id BIGINT REFERENCES automation.suite_runs(id) ON DELETE SET NULL;

CREATE INDEX runs_suite_run_idx ON automation.runs (suite_run_id) WHERE suite_run_id IS NOT NULL;
