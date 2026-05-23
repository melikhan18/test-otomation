-- ┌────────────────────────────────────────────────────────────────────┐
-- │  Apps & App Versions                                               │
-- │                                                                    │
-- │  Test target uygulamaları. Bir "app" paket bazlıdır (per-project,  │
-- │  benzersiz package_name). Her app birden çok "version"a sahiptir   │
-- │  (kullanıcı APK yükledikçe). Run/SuiteRun başlatılırken hedef      │
-- │  app_version_id seçilir; runner cihazda doğru sürümü kurar.        │
-- │                                                                    │
-- │  Sadece project_id ile scoped — V10 sonrası mimari project-       │
-- │  centric olduğu için legacy product_id kolonu yok.                 │
-- └────────────────────────────────────────────────────────────────────┘
CREATE TABLE automation.apps (
    id                BIGSERIAL    PRIMARY KEY,
    project_id        BIGINT       NOT NULL,
    package_name      VARCHAR(255) NOT NULL,                     -- ör. com.example.app
    display_name      VARCHAR(255) NOT NULL,                     -- kullanıcı dostu ad
    description       TEXT,
    icon_data         TEXT,                                       -- data:image/png;base64,... (APK'dan parse, opsiyonel)
    created_by_user_id BIGINT      NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    archived_at       TIMESTAMPTZ,                                -- soft delete; retention job temizler
    UNIQUE (project_id, package_name)
);
CREATE INDEX idx_apps_project ON automation.apps(project_id) WHERE archived_at IS NULL;

CREATE TABLE automation.app_versions (
    id                  BIGSERIAL    PRIMARY KEY,
    app_id              BIGINT       NOT NULL REFERENCES automation.apps(id) ON DELETE CASCADE,
    version_code        BIGINT       NOT NULL,                    -- Android manifest versionCode (int ama 64-bit güvenli)
    version_name        VARCHAR(255),                              -- Android manifest versionName (ör. "1.4.2")
    file_size_bytes     BIGINT       NOT NULL,
    sha256              VARCHAR(64)  NOT NULL,                    -- hex lowercase (always 64 chars; varchar keeps Hibernate validation happy)
    storage_key         TEXT         NOT NULL,                    -- MinIO içindeki path, ör. "<projectId>/<appId>/<versionCode>.apk"
    notes               TEXT,                                      -- kullanıcının release notu (opsiyonel)
    uploaded_by_user_id BIGINT       NOT NULL,
    uploaded_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (app_id, version_code)
);
CREATE INDEX idx_app_versions_app ON automation.app_versions(app_id, version_code DESC);
