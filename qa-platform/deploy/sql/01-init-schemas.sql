-- Initialize qa_platform database.
--
-- Each Spring service manages its own DDL via Flyway, but Postgres requires
-- the target schema to exist before the first migration runs. We create
-- everything up front so service startup order doesn't matter.
--
-- Layout (post-F2 + F5 rename):
--   auth                shared kernel — users, companies, projects, memberships
--   tenant              shared kernel — project_platforms (which platforms are active per project)
--   android_device      android stack — devices, enrollment tokens, agent state
--   android_session     android stack — session reservations, locks
--   android_automation  android stack — scenarios, suites, runs, elements, apps
--
-- Future platform stacks (ios_*, backend_*, web_*) add their own schemas in
-- new migrations; nothing in this init script changes.

CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS tenant;
CREATE SCHEMA IF NOT EXISTS reports;
CREATE SCHEMA IF NOT EXISTS android_device;
CREATE SCHEMA IF NOT EXISTS android_session;
CREATE SCHEMA IF NOT EXISTS android_automation;

-- Default search path covers the shared kernel only. Each service overrides
-- this per JDBC connection via spring.jpa.properties.hibernate.default_schema
-- so it sees its own schema first.
ALTER DATABASE qa_platform SET search_path TO public, auth, tenant;
