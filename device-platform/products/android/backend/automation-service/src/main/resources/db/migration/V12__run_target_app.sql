-- ┌────────────────────────────────────────────────────────────────────┐
-- │  Run / SuiteRun — target app + pre-test prep + post-test reset    │
-- │                                                                    │
-- │  Faz 4 entegrasyonu: RunOrchestrator step döngüsünden önce         │
-- │  hedef APK'yı cihazda kurar / günceller / açar; finally bloğunda   │
-- │  cihazı ana ekrana döndürür (sonraki test temiz başlasın).         │
-- │                                                                    │
-- │  target_app_version_id: nullable — eski runs ve "app-less" runs    │
-- │  için NULL kalır (orchestrator app prep fazını atlar).             │
-- │  ON DELETE SET NULL: APK versiyonu silinse bile run satırı kalıyor │
-- │  tarihsel raporlama için; sadece referans temizlenir.              │
-- └────────────────────────────────────────────────────────────────────┘

ALTER TABLE automation.runs ADD COLUMN target_app_version_id BIGINT
    REFERENCES automation.app_versions(id) ON DELETE SET NULL;

ALTER TABLE automation.runs ADD COLUMN reset_home_after BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE automation.runs ADD COLUMN kill_process_after BOOLEAN NOT NULL DEFAULT FALSE;

-- App preparation outcome — set by RunOrchestrator before step loop.
-- Possible values: NOT_REQUESTED (target_app_version_id was null),
-- ALREADY_LATEST, INSTALLED, UPDATED, FAILED.
ALTER TABLE automation.runs ADD COLUMN app_prep_status      TEXT;
ALTER TABLE automation.runs ADD COLUMN app_prep_duration_ms INT;
ALTER TABLE automation.runs ADD COLUMN app_prep_error       TEXT;

ALTER TABLE automation.suite_runs ADD COLUMN target_app_version_id BIGINT
    REFERENCES automation.app_versions(id) ON DELETE SET NULL;

ALTER TABLE automation.suite_runs ADD COLUMN reset_home_after   BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE automation.suite_runs ADD COLUMN kill_process_after BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_runs_target_app_version       ON automation.runs(target_app_version_id);
CREATE INDEX idx_suite_runs_target_app_version ON automation.suite_runs(target_app_version_id);
