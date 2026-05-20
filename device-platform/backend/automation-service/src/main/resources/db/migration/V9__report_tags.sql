-- User-managed labels on reports. We tag both run-level and suite-run-level rows so the
-- unified feed can filter across both. GIN indexes make `tags && ARRAY[...]` containment
-- checks fast even on large run histories.
ALTER TABLE automation.runs
    ADD COLUMN tags TEXT[] NOT NULL DEFAULT '{}';

ALTER TABLE automation.suite_runs
    ADD COLUMN tags TEXT[] NOT NULL DEFAULT '{}';

CREATE INDEX runs_tags_gin       ON automation.runs       USING gin (tags);
CREATE INDEX suite_runs_tags_gin ON automation.suite_runs USING gin (tags);
