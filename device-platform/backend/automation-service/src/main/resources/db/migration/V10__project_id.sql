-- Multi-tenancy: every automation-owned row gets a project_id.
-- Backfill: each row's existing product_id is mapped to its company's default
-- project (created in auth.V3). Both columns coexist during the transition;
-- product_id is dropped after services switch to project-scoped filtering.
--
-- Cross-schema join is safe here because auth and automation live in the same
-- Postgres database. Compose's depends_on guarantees auth-service has run V3
-- before automation-service starts and tries this migration.

ALTER TABLE automation.scenarios  ADD COLUMN project_id BIGINT;
ALTER TABLE automation.suites     ADD COLUMN project_id BIGINT;
ALTER TABLE automation.elements   ADD COLUMN project_id BIGINT;
ALTER TABLE automation.test_data  ADD COLUMN project_id BIGINT;
ALTER TABLE automation.runs       ADD COLUMN project_id BIGINT;
ALTER TABLE automation.suite_runs ADD COLUMN project_id BIGINT;

-- Backfill each row: product_id → matching company's default project.
WITH proj_for_product AS (
    SELECT c.legacy_product_id AS product_id, p.id AS project_id
    FROM auth.companies c
    JOIN auth.projects  p ON p.company_id = c.id AND p.slug = 'default'
    WHERE c.legacy_product_id IS NOT NULL
)
UPDATE automation.scenarios s
   SET project_id = pf.project_id
  FROM proj_for_product pf
 WHERE pf.product_id = s.product_id;

WITH proj_for_product AS (
    SELECT c.legacy_product_id AS product_id, p.id AS project_id
    FROM auth.companies c
    JOIN auth.projects  p ON p.company_id = c.id AND p.slug = 'default'
    WHERE c.legacy_product_id IS NOT NULL
)
UPDATE automation.suites s
   SET project_id = pf.project_id
  FROM proj_for_product pf
 WHERE pf.product_id = s.product_id;

WITH proj_for_product AS (
    SELECT c.legacy_product_id AS product_id, p.id AS project_id
    FROM auth.companies c
    JOIN auth.projects  p ON p.company_id = c.id AND p.slug = 'default'
    WHERE c.legacy_product_id IS NOT NULL
)
UPDATE automation.elements e
   SET project_id = pf.project_id
  FROM proj_for_product pf
 WHERE pf.product_id = e.product_id;

WITH proj_for_product AS (
    SELECT c.legacy_product_id AS product_id, p.id AS project_id
    FROM auth.companies c
    JOIN auth.projects  p ON p.company_id = c.id AND p.slug = 'default'
    WHERE c.legacy_product_id IS NOT NULL
)
UPDATE automation.test_data t
   SET project_id = pf.project_id
  FROM proj_for_product pf
 WHERE pf.product_id = t.product_id;

WITH proj_for_product AS (
    SELECT c.legacy_product_id AS product_id, p.id AS project_id
    FROM auth.companies c
    JOIN auth.projects  p ON p.company_id = c.id AND p.slug = 'default'
    WHERE c.legacy_product_id IS NOT NULL
)
UPDATE automation.runs r
   SET project_id = pf.project_id
  FROM proj_for_product pf
 WHERE pf.product_id = r.product_id;

WITH proj_for_product AS (
    SELECT c.legacy_product_id AS product_id, p.id AS project_id
    FROM auth.companies c
    JOIN auth.projects  p ON p.company_id = c.id AND p.slug = 'default'
    WHERE c.legacy_product_id IS NOT NULL
)
UPDATE automation.suite_runs sr
   SET project_id = pf.project_id
  FROM proj_for_product pf
 WHERE pf.product_id = sr.product_id;

CREATE INDEX idx_scenarios_project  ON automation.scenarios  (project_id);
CREATE INDEX idx_suites_project     ON automation.suites     (project_id);
CREATE INDEX idx_elements_project   ON automation.elements   (project_id);
CREATE INDEX idx_test_data_project  ON automation.test_data  (project_id);
CREATE INDEX idx_runs_project       ON automation.runs       (project_id);
CREATE INDEX idx_suite_runs_project ON automation.suite_runs (project_id);
