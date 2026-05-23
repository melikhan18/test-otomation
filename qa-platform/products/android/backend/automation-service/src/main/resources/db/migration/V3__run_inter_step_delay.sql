-- Pacing knob set per-run from the "Run scenario" dialog. The orchestrator sleeps this
-- many ms after every step before resolving the next one — gives the device's UI time
-- to settle (animations, network round-trips, etc.) so locators don't race the layout.
ALTER TABLE android_automation.runs
    ADD COLUMN inter_step_delay_ms INTEGER NOT NULL DEFAULT 500;
