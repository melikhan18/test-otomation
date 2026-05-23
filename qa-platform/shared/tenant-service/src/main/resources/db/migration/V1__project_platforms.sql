-- ┌────────────────────────────────────────────────────────────────────┐
-- │  project_platforms                                                  │
-- │                                                                    │
-- │  Which testing platforms each project has activated. The platform  │
-- │  dropdown in the workspace switcher uses this to decide which      │
-- │  options to enable per project.                                    │
-- │                                                                    │
-- │  project_id refers to auth.projects — cross-schema. We don't       │
-- │  declare a FK because tenant-service is downstream of auth-service │
-- │  (Flyway runs in parallel; FK would create a cross-service startup │
-- │  ordering constraint). Integrity is enforced at the application    │
-- │  layer: project_id must resolve via auth-service before insert.    │
-- └────────────────────────────────────────────────────────────────────┘

CREATE TABLE tenant.project_platforms (
    project_id  BIGINT      NOT NULL,
    platform    VARCHAR(16) NOT NULL,                        -- ANDROID | IOS | BACKEND | WEB
    enabled_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    enabled_by  BIGINT,                                      -- user id who activated
    PRIMARY KEY (project_id, platform)
);
CREATE INDEX idx_project_platforms_project ON tenant.project_platforms(project_id);

-- Backfill: every existing project gets ANDROID enabled, since that's the only
-- live platform today and every project that was created before F5 implicitly
-- used Android. New projects will start empty and require explicit activation.
INSERT INTO tenant.project_platforms (project_id, platform, enabled_at)
SELECT p.id, 'ANDROID', NOW()
FROM auth.projects p
ON CONFLICT (project_id, platform) DO NOTHING;
