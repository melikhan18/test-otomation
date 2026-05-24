# web-runner-service

> **Port 8088** · Schema `web_automation`. Web platformunun **tek** backend servisi — Playwright-tabanlı server-side test koşumu. Browser bizim sunucumuzda; agent + bridge + session yok.

## Sorumluluk

Bir scenario tetiklendiğinde Playwright bu container içinde:
1. Browser engine'i başlatır (Chromium / Firefox / WebKit — pick'lenen profil'e göre)
2. `BrowserContext` oluşturur (viewport, DPR, user-agent, locale, timezone profil'den gelir; cookies/storage izole)
3. Video kaydı + tracing başlatır
4. F6 SPI üzerinden step'leri yürütür (`page.click`, `page.fill`, `assertThat(locator).hasText`, …)
5. Başarısız step'lerde otomatik screenshot çeker
6. Tear-down: trace.zip + run.webm MinIO'ya yüklenir, context+browser kapatılır
7. Run terminal'e ulaşır → `reports-aggregator-service`'e push

Android'in 4 servisine (device + session + bridge + automation) karşılık **tek servis** çünkü browser fiziksel cihaz değil — yaratılır, kullanılır, atılır.

## Veri (`web_automation` schema)

| Entity | İçerik |
|---|---|
| `WebScenarioEntity` | name, description, projectId, version (@PreUpdate auto++), tags |
| `WebStepEntity` | scenarioId, orderIndex, action (`WebStepAction` enum, 27 değer), selector (Playwright locator string), value (URL/text/expected), timeoutMs, screenshotAfter |
| `WebRunEntity` | scenarioId, **browserProfileId** (catalog ref, FK değil), environment, status (`RunStatus`), started/finished/duration, totalSteps/passed/failed, errorSummary, **videoUrl**, **traceUrl** |
| `WebStepResultEntity` | runId, stepId, status (`StepResultStatus`), started/finished/duration, errorMessage, screenshotUrl |

V1 migration tek dosya: [`db/migration/V1__init.sql`](src/main/resources/db/migration/V1__init.sql).

**Yok olanlar (kasıtlı):**
- `devices` — "device" web'de fiziksel değil, statik konfig
- `sessions` — run ephemeral, lock yok
- `suites` — v1 standalone scenario
- `apps` / `app_versions` — APK install fazı yok
- `elements` / `test_data` — selector ve değerler step satırında inline

## API yüzeyi

Servis içinde `/api/web/{resource}`; gateway `/api/{resource}` + `X-Platform: WEB` → rewrite.

| Method | Endpoint | Ne yapar |
|---|---|---|
| `GET` | `/api/web/browsers` | Statik browser profile kataloğunu döndür (chromium-1080p, webkit-iphone-14, …) |
| `POST` | `/api/web/scenarios` | Yeni scenario |
| `GET` | `/api/web/scenarios` | Project'teki scenario list |
| `GET` | `/api/web/scenarios/{id}` | Scenario + step list |
| `PUT/DELETE` | `/api/web/scenarios/{id}` | Update / delete |
| `POST` | `/api/web/scenarios/{id}/steps` | Step ekle (`{action, selector?, value?, position?}`) |
| `PUT/DELETE` | `/api/web/scenarios/{id}/steps/{stepId}` | Update / delete |
| `POST` | `/api/web/scenarios/{id}/steps/reorder` | Drag-reorder (`{stepIds: [...]}`) |
| `POST` | `/api/web/runs` | Run tetikle (`{scenarioId, browserProfileId, environment?}`) |
| `GET` | `/api/web/runs?scenarioId=N` | Run list |
| `GET` | `/api/web/runs/{id}` | Run + step results + video/trace URL |

## Browser profilleri

Statik JSON catalog — [`browser-profiles.json`](src/main/resources/browser-profiles.json). v1'de 7 profil:

| ID | Engine | Viewport | Mobile |
|---|---|---|---|
| `desktop-chrome-1080p` | chromium | 1920×1080 | no |
| `desktop-chrome-1440` | chromium | 1440×900 | no |
| `desktop-firefox-1080p` | firefox | 1920×1080 | no |
| `desktop-safari-1440` | webkit | 1440×900 | no |
| `mobile-iphone-14` | webkit | 414×896 (DPR 3) | yes |
| `mobile-pixel-8` | chromium | 412×915 (DPR 2.625) | yes |
| `tablet-ipad` | webkit | 1024×1366 (DPR 2) | yes |

Yeni profil eklemek = JSON satırı + servis restart. DB row değil, config.

## Step DSL (`WebStepAction` enum)

27 action, 5 kategori:

| Kategori | Action'lar |
|---|---|
| Navigation | `GOTO`, `RELOAD`, `GO_BACK`, `GO_FORWARD` |
| Interaction | `CLICK`, `DBL_CLICK`, `FILL`, `PRESS_KEY`, `CHECK`, `UNCHECK`, `SELECT`, `HOVER` |
| Wait | `WAIT_FOR_SELECTOR`, `WAIT_FOR_LOAD_STATE`, `SLEEP` |
| Assert | `ASSERT_{VISIBLE,HIDDEN,TEXT_EQUALS,TEXT_CONTAINS,URL_EQUALS,URL_CONTAINS,TITLE_EQUALS,TITLE_CONTAINS,ATTRIBUTE}` |
| Util | `SCREENSHOT`, `COMMENT`, `EVAL_JS` |

`selector` Playwright locator syntax (CSS / XPath / `text=` / `role=...[name=...]`). `value` action'a göre: URL (GOTO), text (FILL), expected (ASSERT_*), key adı (PRESS_KEY), ms (SLEEP), `attr=value` (ASSERT_ATTRIBUTE).

## F6 SPI entegrasyonu

`com.qaplatform.common.runengine.spi` interface'lerini Android'le birebir aynı pattern'de implement ediyor — F6 kontratının portable olduğunun ikinci kanıtı.

| Sınıf | Karşılığı |
|---|---|
| `WebStepExecutor` | `@Component`, `forRun(Page, defaultTimeoutMs) → StepExecutor` lambda. WebStepAction → Playwright primitives dispatch. |
| `StepEntityRunStep` | `WebStepEntity` → F6 `RunStep` adapter |
| `ObjectStorageArtifactSink` | F6 `ArtifactSink` — MinIO uploader (+ trace bucket) |
| `Slf4jRunLogStream` | Per-run log prefix "WebRunLog" |
| `WebRunOrchestrator` | Yaşam döngüsünü yürütür — Playwright launch → context → page → step loop → tear-down |

## Bağımlılıklar

- **Çağırdığı servisler**:
  - `reports-aggregator-service` (`WebReportsPublisher`, `@Async`) — terminal run push
  - `auth.projects` + `auth.companies` (cross-schema read via `ProjectLookup`) — tenancy resolve
- **Infra**: PostgreSQL (zorunlu) + **MinIO** (videos + traces + screenshots) + Playwright Java client + Node.js driver subprocess

## Yapılandırma

| Env | Açıklama |
|---|---|
| `DB_*` | Postgres |
| `JWT_SECRET` | Edge gateway aynı secret'la doğrular |
| `SVC_REPORTS` | reports-aggregator URL'i |
| `MINIO_*` | Object storage (videos / screenshots / traces bucket) |
| `BROWSER_HEADLESS` | Default `true` — container'da display yok |
| `BROWSER_STEP_TIMEOUT_MS` | Step başına ceiling (default 30000) |
| `RUNS_TMP_DIR` | Geçici trace + video scratch (default `/tmp/qa-platform-web-runs`) |

Full config: [`application.yml`](src/main/resources/application.yml).

## Dockerfile

Servisin kendi Dockerfile'ı var ([`Dockerfile`](Dockerfile)) — root Dockerfile'ı kullanmıyor. Sebep:

- Base image `mcr.microsoft.com/playwright:v1.49.0-noble` — Microsoft'un resmi Playwright image'ı, Chromium + Firefox + WebKit binary'leri + tüm OS deps (fonts, libnss3, libatk, libxkbcommon, libgbm, libasound2, …) hazır gelir
- Üzerine JDK 21 ekleniyor (base Node-only)
- `PLAYWRIGHT_BROWSERS_PATH=/ms-playwright` — Java client önceden yüklenmiş binary'leri kullanır, runtime'da yeniden indirmez

Image boyutu ~1.8GB (Android servislerinin ~4-5x'i). Kabul edilebilir.

**Pinning:** Java dep'teki `com.microsoft.playwright:playwright:X.Y.Z` ile Dockerfile base'deki `mcr.microsoft.com/playwright:vX.Y.Z-noble` aynı versiyona pin'lenmeli. Drift olursa first-launch'ta "driver mismatch" hataları çıkar.

## Performans + sizing

| Kaynak | Idle | Run sırasında |
|---|---|---|
| Spring Boot | 250MB | 350MB |
| Chromium headless | 280MB | 600-900MB |
| Firefox headless | 350MB | 650-1000MB |
| WebKit headless | 200MB | 400-700MB |
| Trace + video (90s run) | 0 | 20-60MB |

Compose default'u 4GB mem + 2 CPU + 1GB shm. ~6-8 paralel run rahat (4 vCPU / 8GB host). `shm_size: 1gb` **kritik** — Chromium default 64MB ile sessizce crash olur.

## Background jobs

Yok. Android'in retention cron'u henüz web tarafına eklenmedi — v1.x işi.

## Bilinen limit'ler (v1)

- **Localhost test edilemez** — server-side Playwright kullanıcının `localhost:3000`'ine ulaşamaz. Tunnel CLI v1.5'te eklenecek.
- **Element catalog yok** — selector'lar step satırında literal. Kataloglama gelecek faz.
- **Test data fixture yok** — `${variable}` resolve yok.
- **Suite yok** — scenario'lar standalone.
- **Cancel button yok** — run tetiklendiyse bitene kadar yürür.
- **Realtime preview yok** — kullanıcı trace + video'yu run bitince izler. Headed VNC veya CDP-snapshot canlı preview v2 işi.

## Kod kılavuzu

- Giriş: [`WebRunnerApplication.java`](src/main/java/com/qaplatform/web/automation/WebRunnerApplication.java) — `@EnableAsync` reports publisher için
- Controller'lar: `api/{WebScenarioController, WebRunController, BrowserController}`
- Service'ler: `service/{WebScenarioService, WebRunService}` + `service/run/WebRunOrchestrator`
- Run engine (F6 SPI): `service/run/runengine/*` — WebStepExecutor + adapters
- Storage: `service/storage/ObjectStorage` (S3 SDK wrapper, screenshot/video/trace bucket'ları)
- Browser catalog: `browser/BrowserCatalog` (PostConstruct'ta JSON yükler) + `browser/BrowserProfile` record
- Tenancy: `tenancy/{ProjectContext, ProjectLookup, TenancyGuard}` — Android'in aynısı
- Migration: `db/migration/V1__init.sql` (tek migration v1'de, 4 tablo)
- Reports push: `service/run/WebReportsPublisher` — Android pattern'ının kopyası, `PLATFORM = "WEB"`
