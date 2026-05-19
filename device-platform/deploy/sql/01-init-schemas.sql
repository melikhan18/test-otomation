-- Initialize device_platform database
-- Creates per-service schemas. Each service uses Flyway to manage its own DDL within its schema.

CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS device;
CREATE SCHEMA IF NOT EXISTS session;

-- Search path default (services override per-connection via JPA hibernate.default_schema)
ALTER DATABASE device_platform SET search_path TO public, auth, device, session;
