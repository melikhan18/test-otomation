-- Placeholder so Flyway doesn't fail an empty migration directory.
--
-- Replace with your platform's real schema in V2+. Typical first migration
-- in a new platform owns the equivalent of the Android stack's:
--   - scenarios          (named tests)
--   - steps              (ordered actions inside a scenario)
--   - runs               (one execution of a scenario)
--   - step_results       (per-step outcome row)
--   - elements           (named locators / selectors / queries)
--   - test_data          (per-environment lookup values)
--
-- See products/android/backend/automation-service/src/main/resources/db/migration
-- for the full Android shape; copy and adapt to your platform's primitives.

DO $$
BEGIN
    RAISE NOTICE 'platform-template V1 placeholder migration applied — replace with real schema';
END $$;
