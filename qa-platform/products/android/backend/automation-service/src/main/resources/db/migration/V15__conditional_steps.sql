-- ┌──────────────────────────────────────────────────────────────────────┐
-- │  V15 — conditional steps (IF / ELSE) for Android stack               │
-- │                                                                       │
-- │  Mirrors the WEB stack's V3 — adds three optional columns so a       │
-- │  scenario can model an IF block as a tree node with two child        │
-- │  branches ("then" / "else").                                         │
-- │                                                                       │
-- │    parent_step_id   FK to steps.id, NULL = root level                │
-- │    branch_label     "then" | "else" | NULL (for non-control kids)    │
-- │    condition        JSONB; only populated when action = 'IF'         │
-- │                                                                       │
-- │  Note: V1 had a `flow_meta TEXT` column for the same purpose, dropped │
-- │  in V13 because the design wasn't fleshed out. V15 brings the         │
-- │  feature back with a proper typed-tree shape (matches Web's V3).      │
-- │                                                                       │
-- │  Every legacy step has parent_step_id NULL and branch_label NULL,    │
-- │  which the executor treats as the root branch. No data migration.    │
-- └──────────────────────────────────────────────────────────────────────┘

ALTER TABLE android_automation.steps
    ADD COLUMN parent_step_id BIGINT
        REFERENCES android_automation.steps(id) ON DELETE CASCADE,
    ADD COLUMN branch_label   VARCHAR(8),
    ADD COLUMN condition      JSONB;

ALTER TABLE android_automation.steps
    ADD CONSTRAINT chk_android_steps_branch_label
        CHECK (branch_label IS NULL OR branch_label IN ('then', 'else'));

ALTER TABLE android_automation.steps
    ADD CONSTRAINT chk_android_steps_parent_branch_coupling
        CHECK (
            (parent_step_id IS NULL AND branch_label IS NULL)
         OR (parent_step_id IS NOT NULL AND branch_label IS NOT NULL)
        );

CREATE INDEX idx_android_steps_parent_branch_order
    ON android_automation.steps (parent_step_id, branch_label, order_index)
    WHERE parent_step_id IS NOT NULL;
