-- ─────────────────────────────────────────────────────────────────────────
-- Multi-tenancy: Company (top) → Project (mid) → Resources.
--
-- One user may belong to N companies (company_members), and within a company
-- a user has ONE role (OWNER / QA_MANAGER / TESTER). ADMIN is a platform-wide
-- staff flag stored on the user row itself.
--
-- For TESTER role, explicit project_members rows control which projects they
-- can access. OWNER and QA_MANAGER get implicit access to every project in
-- their company.
--
-- Migration strategy
-- ──────────────────
-- The legacy `auth.products` table modeled single-tenancy via `users.product_id`.
-- We backfill: each product becomes one Company with a "default" Project, every
-- existing user becomes OWNER of that company + member of its default project.
-- `users.product_id` stays for one or two release cycles so downstream services
-- can keep filtering until they switch to project_id. Drop later.
-- ─────────────────────────────────────────────────────────────────────────

-- Platform-wide super-user flag (rare; vendor support staff).
ALTER TABLE auth.users ADD COLUMN platform_admin BOOLEAN NOT NULL DEFAULT FALSE;
-- The legacy ADMIN role on users.role is special-cased into platform_admin.
UPDATE auth.users SET platform_admin = TRUE WHERE role = 'ADMIN';

CREATE TABLE auth.companies (
    id               BIGSERIAL    PRIMARY KEY,
    -- Slug used in URLs (/c/{slug}/...). Lowercase, kebab-case, immutable-ish.
    slug             VARCHAR(64)  NOT NULL UNIQUE,
    name             VARCHAR(128) NOT NULL,
    -- Track the legacy product so we can join during the transitional period
    -- and clean up once downstream services are project-aware.
    legacy_product_id BIGINT      UNIQUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    archived_at      TIMESTAMPTZ
);

CREATE TABLE auth.company_members (
    user_id     BIGINT       NOT NULL REFERENCES auth.users(id)     ON DELETE CASCADE,
    company_id  BIGINT       NOT NULL REFERENCES auth.companies(id) ON DELETE CASCADE,
    role        VARCHAR(16)  NOT NULL CHECK (role IN ('OWNER','QA_MANAGER','TESTER')),
    joined_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, company_id)
);
CREATE INDEX idx_company_members_company ON auth.company_members (company_id);

CREATE TABLE auth.projects (
    id           BIGSERIAL    PRIMARY KEY,
    company_id   BIGINT       NOT NULL REFERENCES auth.companies(id) ON DELETE CASCADE,
    slug         VARCHAR(64)  NOT NULL,
    name         VARCHAR(128) NOT NULL,
    description  TEXT,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    archived_at  TIMESTAMPTZ,
    UNIQUE (company_id, slug)
);
CREATE INDEX idx_projects_company ON auth.projects (company_id);

-- TESTER-level explicit access. OWNER + QA_MANAGER get implicit access to all
-- projects in their company, so they don't need rows here.
CREATE TABLE auth.project_members (
    user_id    BIGINT      NOT NULL REFERENCES auth.users(id)     ON DELETE CASCADE,
    project_id BIGINT      NOT NULL REFERENCES auth.projects(id)  ON DELETE CASCADE,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    added_by   BIGINT,
    PRIMARY KEY (user_id, project_id)
);
CREATE INDEX idx_project_members_project ON auth.project_members (project_id);

-- Pending email-based invitations. Accepted invitations become company_members rows.
CREATE TABLE auth.company_invitations (
    id          BIGSERIAL    PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES auth.companies(id) ON DELETE CASCADE,
    email       VARCHAR(255) NOT NULL,
    role        VARCHAR(16)  NOT NULL CHECK (role IN ('OWNER','QA_MANAGER','TESTER')),
    token       VARCHAR(255) NOT NULL UNIQUE,
    invited_by  BIGINT       NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_company_invitations_email ON auth.company_invitations (lower(email));

-- ─────────────────────────────────────────────────────────────────────────
-- Backfill: every existing product → one company + one default project.
-- Every existing user becomes OWNER of their product's company + project member.
-- ─────────────────────────────────────────────────────────────────────────

INSERT INTO auth.companies (slug, name, legacy_product_id)
SELECT lower(code), name, id FROM auth.products;

INSERT INTO auth.projects (company_id, slug, name, description)
SELECT c.id, 'default', 'Default', 'Auto-created from legacy product setup'
FROM auth.companies c
WHERE c.legacy_product_id IS NOT NULL;

INSERT INTO auth.company_members (user_id, company_id, role)
SELECT u.id, c.id, 'OWNER'
FROM auth.users u
JOIN auth.companies c ON c.legacy_product_id = u.product_id;
