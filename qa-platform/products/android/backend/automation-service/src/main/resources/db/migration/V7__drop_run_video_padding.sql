-- Reverts V6: the recording-padding knob was rolled back at the product level. We can't
-- delete the V6 migration file (Flyway would fail validation on every existing DB), so
-- we drop the column here instead. IF EXISTS guards a fresh DB where V6 hadn't run.
ALTER TABLE android_automation.runs
    DROP COLUMN IF EXISTS video_padding_sec;
