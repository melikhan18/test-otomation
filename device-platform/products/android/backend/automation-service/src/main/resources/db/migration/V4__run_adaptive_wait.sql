-- When true, the orchestrator polls the accessibility tree after every UI-mutating step
-- and proceeds as soon as two consecutive snapshots match (capped at 5s). Replaces the
-- fixed inter_step_delay_ms pause for UI-changing actions; non-UI actions skip the wait.
ALTER TABLE automation.runs
    ADD COLUMN adaptive_wait BOOLEAN NOT NULL DEFAULT FALSE;
