-- ─────────────────────────────────────────────────────────────────────────
-- Project-scoped roles.
--
-- Before: role lived at company level (OWNER / QA_MANAGER / TESTER), and
-- QA_MANAGERs had implicit access to every project. After: role lives at the
-- *project* level (QA_MANAGER / TESTER), so the same user can be QA_MANAGER
-- on one project and TESTER (or absent) on another. company_members keeps a
-- single OWNER flag for company-wide administration.
--
-- Migration strategy
-- ──────────────────
-- 1. Add project_members.role (TESTER for every existing row — that was the
--    only kind of project_members assignment before this change).
-- 2. Synthesize QA_MANAGER project_members for everyone who used to be a
--    company-level QA_MANAGER: one row per active project in their company.
-- 3. Collapse company_members.role to (OWNER, MEMBER). Every former
--    QA_MANAGER/TESTER becomes a plain MEMBER; their actual capabilities now
--    flow from project_members.
-- 4. Same collapse on company_invitations.role for any in-flight invites.
--
-- Constraint ordering note: we must DROP the existing CHECK constraints
-- before any UPDATE that introduces the new value ('MEMBER'), otherwise the
-- UPDATE fails the still-active old check.
-- ─────────────────────────────────────────────────────────────────────────

-- Step 1: role column on project_members.
ALTER TABLE auth.project_members
    ADD COLUMN role VARCHAR(16);

UPDATE auth.project_members SET role = 'TESTER' WHERE role IS NULL;

-- Step 2: every QA_MANAGER gets a QA_MANAGER row on every active project in
-- their company. Active here means "not archived" — archived projects don't
-- need synthetic rows.
INSERT INTO auth.project_members (user_id, project_id, role)
SELECT cm.user_id, p.id, 'QA_MANAGER'
  FROM auth.company_members cm
  JOIN auth.projects p ON p.company_id = cm.company_id
 WHERE cm.role = 'QA_MANAGER'
   AND p.archived_at IS NULL
ON CONFLICT (user_id, project_id) DO UPDATE SET role = 'QA_MANAGER';

-- Lock down project_members.role.
ALTER TABLE auth.project_members
    ALTER COLUMN role SET NOT NULL;
ALTER TABLE auth.project_members
    ADD CONSTRAINT project_members_role_check
        CHECK (role IN ('QA_MANAGER','TESTER'));

-- Step 3: collapse company_members.role to (OWNER, MEMBER).
-- Must drop the old CHECK first, otherwise the UPDATE to 'MEMBER' is rejected.
ALTER TABLE auth.company_members
    DROP CONSTRAINT company_members_role_check;
UPDATE auth.company_members SET role = 'MEMBER'
 WHERE role IN ('QA_MANAGER','TESTER');
ALTER TABLE auth.company_members
    ADD CONSTRAINT company_members_role_check
        CHECK (role IN ('OWNER','MEMBER'));

-- Step 4: same on company_invitations. In-flight invites with the old role
-- vocabulary just become MEMBER invitations — the per-project grants live in
-- the notification payload now (see NotificationDtos.InvitePayload).
ALTER TABLE auth.company_invitations
    DROP CONSTRAINT company_invitations_role_check;
UPDATE auth.company_invitations SET role = 'MEMBER'
 WHERE role IN ('QA_MANAGER','TESTER');
ALTER TABLE auth.company_invitations
    ADD CONSTRAINT company_invitations_role_check
        CHECK (role IN ('OWNER','MEMBER'));
