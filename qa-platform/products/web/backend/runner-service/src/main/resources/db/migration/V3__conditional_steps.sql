-- ┌──────────────────────────────────────────────────────────────────────┐
-- │  V3 — conditional steps (IF / ELSE)                                  │
-- │                                                                       │
-- │  Steps gain three optional columns so a scenario can model an IF      │
-- │  block as a tree node with two child branches ("then" / "else"):     │
-- │                                                                       │
-- │    parent_step_id   FK to steps.id, NULL = root level                │
-- │    branch_label     "then" | "else" | NULL (for non-control kids)    │
-- │    condition        JSONB; only populated when action = 'IF'         │
-- │                                                                       │
-- │  Existing flat scenarios keep working unchanged — every legacy step  │
-- │  has parent_step_id NULL and branch_label NULL, which the executor   │
-- │  treats as the root branch. No data migration required.             │
-- └──────────────────────────────────────────────────────────────────────┘

ALTER TABLE web_automation.steps
    ADD COLUMN parent_step_id BIGINT
        REFERENCES web_automation.steps(id) ON DELETE CASCADE,
    ADD COLUMN branch_label   VARCHAR(8),
    ADD COLUMN condition      JSONB;

-- Constraint: branch_label can only be "then" or "else" — anything else is a typo.
ALTER TABLE web_automation.steps
    ADD CONSTRAINT chk_web_steps_branch_label
        CHECK (branch_label IS NULL OR branch_label IN ('then', 'else'));

-- Constraint: a child step must declare which branch it lives under, and a
-- root step must NOT carry a branch label. This couples parent_step_id and
-- branch_label so they can't drift.
ALTER TABLE web_automation.steps
    ADD CONSTRAINT chk_web_steps_parent_branch_coupling
        CHECK (
            (parent_step_id IS NULL AND branch_label IS NULL)
         OR (parent_step_id IS NOT NULL AND branch_label IS NOT NULL)
        );

-- Tree-walk index: ordered children of a parent within a single branch.
-- Root-level scenarios continue to use the existing
-- idx_web_steps_scenario_order index because parent_step_id is NULL there.
CREATE INDEX idx_web_steps_parent_branch_order
    ON web_automation.steps (parent_step_id, branch_label, order_index)
    WHERE parent_step_id IS NOT NULL;
