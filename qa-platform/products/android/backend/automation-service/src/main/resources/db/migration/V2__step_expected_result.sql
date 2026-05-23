-- ┌────────────────────────────────────────────────────────────────────┐
-- │  Xray-style "expected result"                                       │
-- │                                                                     │
-- │  Optional free-text outcome attached to every step. Mirrors how     │
-- │  Xray / TestRail / Zephyr document expected behaviour:              │
-- │     Action  →  Click the login button                               │
-- │     Expected → User lands on the dashboard                          │
-- │                                                                     │
-- │  Not consumed by the execution engine — purely documentation that   │
-- │  the report / step card surfaces next to the action.                │
-- └────────────────────────────────────────────────────────────────────┘
ALTER TABLE android_automation.steps
    ADD COLUMN expected_result TEXT;
