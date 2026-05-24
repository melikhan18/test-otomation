-- Drop product_id from every automation entity — the legacy single-tenant
-- column that V10 backfilled company_id alongside but never removed.
--
-- Affected tables: runs, scenarios, suites, suite_runs, elements, test_data.
-- (step_results carries no productId; suite_scenarios is a pure join table.)
--
-- V10's backfill is permanent on the live data — every row already has its
-- project_id populated. From this migration onward services use project_id +
-- the cross-schema company_id lookup in ProjectLookup; the legacy column
-- and its indexes are pure dead weight.

-- ─── runs ────────────────────────────────────────────────────────────
DROP INDEX IF EXISTS android_automation.idx_runs_product;
ALTER TABLE android_automation.runs DROP COLUMN IF EXISTS product_id;

-- ─── scenarios ───────────────────────────────────────────────────────
DROP INDEX IF EXISTS android_automation.idx_scenarios_product;
ALTER TABLE android_automation.scenarios DROP COLUMN IF EXISTS product_id;

-- ─── suites ──────────────────────────────────────────────────────────
DROP INDEX IF EXISTS android_automation.idx_suites_product;
ALTER TABLE android_automation.suites DROP COLUMN IF EXISTS product_id;

-- ─── suite_runs ──────────────────────────────────────────────────────
DROP INDEX IF EXISTS android_automation.idx_suite_runs_product;
ALTER TABLE android_automation.suite_runs DROP COLUMN IF EXISTS product_id;

-- ─── elements ────────────────────────────────────────────────────────
DROP INDEX IF EXISTS android_automation.idx_elements_product;
ALTER TABLE android_automation.elements DROP COLUMN IF EXISTS product_id;

-- ─── test_data ───────────────────────────────────────────────────────
DROP INDEX IF EXISTS android_automation.idx_test_data_product;
ALTER TABLE android_automation.test_data DROP COLUMN IF EXISTS product_id;
