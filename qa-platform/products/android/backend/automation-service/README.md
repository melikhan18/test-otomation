# android-automation-service

> **Port 8085** · Schema `android_automation`. Android stack'inin yıldız servisi. Senaryolar, suite'ler, run'lar, element kataloğu, APK repository, run engine.

## Sorumluluk
Bu servis Android otomasyon dünyasının tamamını sahipleniyor:

- **Element kataloğu**: cihazda inspect'le yakalanmış lokator'ların persistent kopyası. Primary strategy + fallback array (RESOURCE_ID → ACCESSIBILITY_ID → TEXT → CLASS → XPATH).
- **Test data**: per-(project, environment, name) lookup değerleri. Senaryolar `${variable}` ile referans verir.
- **Scenarios**: ordered step list'i. Her step bir StepAction (CLICK, ASSERT_TEXT_EQUALS, vb.) + payload (element ref, beklenen değer, timeout). Drag-reorder destekli, otomatik versiyonlama (`@PreUpdate` → version++).
- **Suites**: birden fazla scenario'yu tek run akışında sırayla çalıştırma. Per-suite environment + tags.
- **Runs**: bir scenario'nun bir cihazdaki tek execution'ı. RunOrchestrator session-service'ten cihaz rezerve eder → bridge'e step'leri tek tek yollar → her step için StepResult kaydeder.
- **Suite Runs**: 2 thread'lik pool, aynı cihazda sırayla scenario'lar; per-run 30dk timeout.
- **APK repository**: target uygulamaların versiyonlanmış APK arşivi (MinIO `apks` bucket'ı). Run başında automatik install + launch.
- **Workspace**: scenario/suite'i hiyerarşik klasör organizasyonu (UI için).
- **Retention job**: 30 günden eski run'ları + MinIO blob'larını otomatik temizle.
- **Reports push**: terminal'e ulaşan her run için fire-and-forget POST → reports-aggregator.

## Veri (`android_automation` schema)
| Entity | İçerik |
|---|---|
| `ScenarioEntity` | name, description, projectId, version (@PreUpdate auto++) |
| `StepEntity` | scenarioId, orderIndex, action (`StepAction` enum, 23 değer), payloadJson |
| `SuiteEntity`, `SuiteScenarioEntity` | suite + ordered scenario refs |
| `RunEntity` | sessionId, scenarioId, status (`RunStatus` from `:common`), startedAt/finishedAt, durationMs, totalSteps/passedSteps/failedSteps, errorSummary, videoUrl, environment, tags, targetAppVersionId, appPrep* |
| `StepResultEntity` | runId, stepId, status (`StepResultStatus`), errorMessage, resolvedLocator, screenshotUrl, durationMs |
| `SuiteRunEntity` | child run'ları aggregate eder (`SuiteRunStatus`) |
| `ElementEntity` | name, primaryStrategy/Value, fallbackLocators (JSONB), screenshot sample |
| `TestDataEntity` | (project, environment, name) → value (sensitive bayrağı varsa redact) |
| `AppEntity`, `AppVersionEntity` | package name + versiyon arşivi |
| `WorkspaceFolderEntity`, `WorkspaceItemEntity` | klasör hiyerarşisi |

## API yüzeyi (gateway path'i `/api/...`, servis içinde `/api/automation/...` olarak rewrite edilir)
Resource bazlı:
- `/api/automation/scenarios` · CRUD + step reorder + version history
- `/api/automation/suites` · CRUD + scenario'ları ekle/sırala
- `/api/automation/runs` · POST trigger + GET status + step result'lar + screenshot list
- `/api/automation/suite-runs` · POST trigger + GET aggregate
- `/api/automation/elements` · CRUD + lokator yakalama (inspector'dan)
- `/api/automation/test-data` · CRUD (sensitive değerleri filter)
- `/api/automation/apps` · APK upload + versiyonlama
- `/api/automation/workspace` · klasör ağacı

## Run engine
1. `RunController.create` → `RunRepository.save(QUEUED)` → submit to background pool
2. `RunOrchestrator.execute`:
   - `SessionClient.reserve(deviceId, userJwt)` ← session-service'ten lock + SESSION token
   - Eğer `targetAppVersionId` varsa: `BridgeClient.installApk + launchApp + 10s warmup`
   - `bridge.startRecording()` (MP4 başlasın)
   - Her step için:
     - `BridgeClient.inspect(sessionId, sessionToken, timeout)` ← fresh accessibility tree
     - `LocatorResolver` ile element'i çöz (primary + fallback)
     - `StepRunner.execute` (StepAction kategorisine göre TOUCH/INPUT/WAIT/ASSERT/UTIL handler'ı)
     - `StepResult` kaydet, FAILED ise screenshot çek + upload
     - Inter-step pacing (sabit ya da adaptive — inspect tree stabilize olana kadar poll)
   - Run finalize → `RunStatus.PASSED/FAILED/ERROR/CANCELLED` → `reportsPublisher.publishAsync(runId)`
   - `bridge.stopRecording()` → MP4 byte'ları → MinIO upload → `videoUrl` set
   - `SessionClient.release(sessionId)` ← lock'u bırak

Run state machine + cancellation registry: `service/run/`'da. Step set'i `domain/StepAction.java`'da (23 değer).

## Bağımlılıklar
- **Çağırdığı servisler**:
  - **session-service** (`SessionClient`) — reserve / release
  - **bridge-service** (`BridgeClient`) — control / inspect / screenshot / recording / app ops
  - **reports-aggregator-service** (`ReportsPublisher`, `@Async`) — terminal run push
  - **device-service** (cross-schema okuma, projectLookup) — tenancy validation
- **Infra**: PostgreSQL + **MinIO** (screenshots/videos/apks).

## Background jobs
- **`RetentionService.purgeOldRuns()`** · `@Scheduled` cron `0 0 3 * * *` (her gün 03:00 UTC) — env'le configure (`RETENTION_DAYS`, `RETENTION_ENABLED`).
- **Async**: `RunOrchestrator`'ın internal `ExecutorService` (4-thread fixed pool), `SuiteRunOrchestrator`'ın 2-thread pool'u, `@Async ReportsPublisher`.

## Yapılandırma
| Env | Açıklama |
|---|---|
| `DB_*` | Postgres |
| `JWT_SECRET` | s2s token mint + AGENT/SESSION doğrulama |
| `SVC_SESSION`, `SVC_BRIDGE`, `SVC_REPORTS` | Peer servisler |
| `MINIO_*` | Object storage (`screenshots`, `videos`, `apks` bucket'ları) |
| `RETENTION_DAYS` / `RETENTION_CRON` / `RETENTION_ENABLED` | Cleanup job |

## Kod kılavuzu
- Giriş: [`AutomationApplication.java`](src/main/java/com/qaplatform/android/automation/AutomationApplication.java) — `@EnableScheduling @EnableAsync`
- Controller'lar: `api/*Controller`
- Run engine: `service/run/RunOrchestrator`, `SuiteRunOrchestrator`, `StepRunner`, `AssertionRunner`, `LocatorResolver`, `ReportsPublisher`, `RunCancellationRegistry`
- HTTP client'lar: `service/run/SessionClient`, `BridgeClient`
- Storage: `service/storage/ObjectStorage` (S3 SDK wrapper)
- Step DSL: `domain/StepAction` enum (23 değer)
- Migration: `db/migration/V*.sql` (Android'in en zengin schema seti — F2 öncesi 30+ migration buraya taşındı)
