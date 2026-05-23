-- ┌────────────────────────────────────────────────────────────────────┐
-- │  Tighten device uniqueness against concurrent enrollment.          │
-- │                                                                    │
-- │  V1 created UNIQUE(product_id, serial) — good enough back when    │
-- │  product_id was the tenancy dimension. V2 introduced company_id   │
-- │  but no matching unique constraint, so two concurrent enrolls of │
-- │  the same (company_id, serial) could both pass the                │
-- │  findByCompanyIdAndSerial() check and INSERT duplicate rows.      │
-- │                                                                    │
-- │  Partial unique index — only enforced when company_id is set, so  │
-- │  legacy rows (NULL company_id) don't trip the constraint during   │
-- │  the V2 backfill grace period.                                    │
-- └────────────────────────────────────────────────────────────────────┘

CREATE UNIQUE INDEX uniq_devices_company_serial
    ON android_device.devices (company_id, serial)
    WHERE company_id IS NOT NULL;
