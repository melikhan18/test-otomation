-- Drop steps.flow_meta — defined for an if/else/loop flow-control feature
-- that never landed. No producer (no UI editor, no service writes it) and
-- no consumer (no service reads it). Pure dead column since V1.
--
-- Safe to drop directly: column carries only the default '{}' value on
-- every row, no information loss.

ALTER TABLE android_automation.steps DROP COLUMN flow_meta;
