-- ─────────────────────────────────────────────────────────────────────────
-- Notification platform + email-based invites.
--
-- A {@code notifications} row is the canonical "user has seen / has yet to see
-- this event" record. Specific event types (COMPANY_INVITATION, RUN_COMPLETED,
-- …) live in the {@code payload} JSON column and are validated by the
-- application layer — the table itself stays generic so adding a new event
-- type doesn't need a schema change.
-- ─────────────────────────────────────────────────────────────────────────

-- Users gain an email so admins can invite them by address (the only stable
-- handle a teammate is likely to know). Nullable for the first deploy because
-- existing rows have no email; the application UI nudges users to set one.
ALTER TABLE auth.users
    ADD COLUMN email VARCHAR(255);
CREATE UNIQUE INDEX uniq_users_email ON auth.users (lower(email)) WHERE email IS NOT NULL;

CREATE TABLE auth.notifications (
    id             BIGSERIAL    PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    type           VARCHAR(48)  NOT NULL,
    -- UNREAD → READ (user opened dropdown / clicked).
    -- ACCEPTED / DECLINED only meaningful for actionable types like invitations.
    -- DISMISSED = user explicitly hid an info notification.
    status         VARCHAR(16)  NOT NULL DEFAULT 'UNREAD',
    payload        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    actor_user_id  BIGINT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    resolved_at    TIMESTAMPTZ,
    expires_at     TIMESTAMPTZ
);
CREATE INDEX notifications_user_created_idx
    ON auth.notifications (user_id, created_at DESC);
CREATE INDEX notifications_user_unread_idx
    ON auth.notifications (user_id) WHERE status = 'UNREAD';

-- The legacy V3 company_invitations table used email + signed token. We're
-- replacing that with the notification flow (user lookup → notification),
-- so the token column becomes redundant. Make it nullable so we can keep the
-- table for audit history without forcing a token on every insert.
ALTER TABLE auth.company_invitations
    ALTER COLUMN token DROP NOT NULL;
-- Link an invitation back to the notification it generated, so accept/decline
-- can flip both rows in one transaction.
ALTER TABLE auth.company_invitations
    ADD COLUMN notification_id BIGINT REFERENCES auth.notifications(id) ON DELETE SET NULL;
ALTER TABLE auth.company_invitations
    ADD COLUMN declined_at TIMESTAMPTZ;
