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

ALTER TABLE android_automation.runs ADD COLUMN target_app_version_id BIGINT
    REFERENCES android_automation.app_versions(id) ON DELETE SET NULL;

ALTER TABLE android_automation.runs ADD COLUMN reset_home_after BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE android_automation.runs ADD COLUMN kill_process_after BOOLEAN NOT NULL DEFAULT FALSE;

-- App preparation outcome — set by RunOrchestrator before step loop.
-- Possible values: NOT_REQUESTED (target_app_version_id was null),
-- ALREADY_LATEST, INSTALLED, UPDATED, FAILED.
ALTER TABLE android_automation.runs ADD COLUMN app_prep_status      TEXT;
ALTER TABLE android_automation.runs ADD COLUMN app_prep_duration_ms INT;
ALTER TABLE android_automation.runs ADD COLUMN app_prep_error       TEXT;

ALTER TABLE android_automation.suite_runs ADD COLUMN target_app_version_id BIGINT
    REFERENCES android_automation.app_versions(id) ON DELETE SET NULL;

ALTER TABLE android_automation.suite_runs ADD COLUMN reset_home_after   BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE android_automation.suite_runs ADD COLUMN kill_process_after BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_runs_target_app_version       ON android_automation.runs(target_app_version_id);
CREATE INDEX idx_suite_runs_target_app_version ON android_automation.suite_runs(target_app_version_id);
