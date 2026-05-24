-- Drop sessions.product_id — legacy single-tenant column from pre-F2 era.
-- Tenancy is enforced via the device's company_id (looked up at session
-- creation) and the caller's project membership in the JWT, not via a
-- session-side product id.
--
-- No backfill into a peer column needed — sessions never had a
-- corresponding company_id and don't need one. Existing rows are
-- short-lived (30 min lock), so any inflight session simply ends without
-- the column.

DROP INDEX IF EXISTS android_session.idx_sessions_product;
ALTER TABLE android_session.sessions DROP COLUMN product_id;
