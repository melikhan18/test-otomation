-- Drop devices.product_id + enrollment_tokens.product_id — legacy single-tenant
-- columns from pre-F2 era. F2's V2 backfilled company_id onto every existing
-- row; queries have used company_id since V3 (uniq_devices_company_serial).
--
-- enrollment_tokens.company_id was nullable during the V2 grace period (so old
-- pre-backfill tokens could still enroll); that window has closed (any
-- unused token from that era has long since expired), so we promote it to NOT
-- NULL here and drop the productId fallback path in EnrollmentService.

-- 1. Defensive cleanup: any leftover unused token without a company_id is
--    unusable now that the productId fallback is gone — purge it rather than
--    leaving a dangling row that would fail at enroll time.
DELETE FROM android_device.enrollment_tokens
 WHERE company_id IS NULL
   AND used_at    IS NULL;

-- 2. Backfill any used-but-null rows (audit-only history, shouldn't exist) so
--    NOT NULL holds.
UPDATE android_device.enrollment_tokens
   SET company_id = 0
 WHERE company_id IS NULL;

ALTER TABLE android_device.enrollment_tokens
    ALTER COLUMN company_id SET NOT NULL,
    DROP COLUMN product_id;

ALTER TABLE android_device.devices
    DROP COLUMN product_id;
