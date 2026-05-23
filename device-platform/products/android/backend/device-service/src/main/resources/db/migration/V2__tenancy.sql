-- Devices live at the company level — a single shared pool that all projects can
-- access by default. Specific devices can be restricted to a subset of projects
-- via device_project_access (admin opt-in).
--
-- Backfill: existing devices land in their product's company.

ALTER TABLE device.devices
    ADD COLUMN company_id BIGINT,
    ADD COLUMN restricted BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE device.devices d
   SET company_id = c.id
  FROM auth.companies c
 WHERE c.legacy_product_id = d.product_id;

CREATE INDEX idx_devices_company ON device.devices (company_id);

-- enrollment_tokens are also company-scoped (mints a token for one company's pool).
ALTER TABLE device.enrollment_tokens ADD COLUMN company_id BIGINT;
UPDATE device.enrollment_tokens t
   SET company_id = c.id
  FROM auth.companies c
 WHERE c.legacy_product_id = t.product_id;

-- Whitelist: when devices.restricted = true, only projects listed here can see
-- and reserve the device. restricted=false → all projects in the company see it.
CREATE TABLE device.device_project_access (
    device_id  BIGINT NOT NULL REFERENCES device.devices(id) ON DELETE CASCADE,
    project_id BIGINT NOT NULL,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    granted_by BIGINT,
    PRIMARY KEY (device_id, project_id)
);
CREATE INDEX idx_device_project_access_project ON device.device_project_access (project_id);
