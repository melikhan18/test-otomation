-- Seconds added to the recording at both the head and the tail of the run.
-- Useful for capturing the launcher state right before the first tap and any post-run
-- toast/error popup that fires after the last step. Clamped to [0, 30] in code.
ALTER TABLE automation.runs
    ADD COLUMN video_padding_sec INTEGER NOT NULL DEFAULT 5;
