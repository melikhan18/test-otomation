# reports-aggregator-service

> **Port 8090** · Schema `reports`. Cross-platform run rollup. Her platform stack'i terminal run summary'sini buraya push'lar; dashboard buradan okur. F7 deliverable.

## Sorumluluk
Bir test run her platformda kendi schema'sında yaşar (`android_automation.runs`, ileride `ios_automation.runs`…). Bir genel bakış sorusu sorulduğunda — "son hafta ne kadar başarısız?" — federe join yapmak yerine, her platform terminal'e ulaşan run için bir özet satırı buraya push'lar. Tek tablo scan, dashboard hızlı.

İdempotent push: `(platform, source_run_id)` natural key. Re-emit güvenli.

## Veri (`reports` schema)
| Entity | İçerik |
|---|---|
| `RunSummaryEntity` | platform, sourceRunId, companyId, projectId, status (`RunStatus` enum), scenarioName, triggeredByUserId, totalSteps/passedSteps/failedSteps, durationMs, startedAt/finishedAt, errorSummary, receivedAt |

Index'ler:
- `(project_id, finished_at DESC)` — dashboard "şu projede son N run" feed'i için.
- `(platform, status, finished_at DESC)` — "ANDROID vs IOS failure rate" widget'ı için.

## API yüzeyi
Base path: `/api/reports`

| Method | Endpoint | Ne yapar |
|---|---|---|
| `POST` | `/runs` | Bir run summary push'la (idempotent). Hem user JWT hem service JWT kabul eder. |
| `GET`  | `/runs?projectId=X&limit=N` | Recent run'lar (finished_at desc, default 50) |
| `GET`  | `/summary?projectId=X&daysBack=D` | Platform × status grid. Her platform için (ANDROID/IOS/BACKEND/WEB) ve her status (PASSED/FAILED/ERROR/CANCELLED/QUEUED/RUNNING) sıfırlarla doldurulmuş tablo — dashboard widget'ı predictable render için |

Auth modeli kasıtlı olarak **gevşek**: herhangi authenticated principal hem push hem read yapabilir. Servisler internal docker network'ünden gelir (service JWT ile), dashboard user JWT'siyle gelir. Per-project / per-company kısıtlama tenant-service konsolidasyonu sonrası eklenecek.

## Bağımlılıklar
- **Diğer servisler**: hiçbiri direkt çağırmaz; **push eden** taraflar Android automation-service'in [`ReportsPublisher`](../../products/android/backend/automation-service/src/main/java/com/qaplatform/android/automation/service/run/ReportsPublisher.java)'ı (fire-and-forget, `@Async`). İleride iOS/Backend/Web stack'leri kendi publisher'larıyla aynı endpoint'e push'layacak.
- **Infra**: PostgreSQL.

## Service-to-service auth
[`JwtTokenService.issueServiceToken("android-automation")`](../common/src/main/java/com/qaplatform/common/jwt/JwtTokenService.java) ile HMAC tokenı mint'lenir (role=SERVICE, platformAdmin=true). Stateless — her push'ta yeni mint, cache yok, rotasyon yok.

## Yapılandırma
| Env | Açıklama |
|---|---|
| `DB_*` | Postgres |
| `JWT_SECRET` | Push'ı imzalayan servislerin aynısı (tek shared secret) |

## Kod kılavuzu
- Giriş: [`ReportsApplication.java`](src/main/java/com/qaplatform/shared/reports/ReportsApplication.java)
- Controller: `api/ReportsController`
- Service: `service/ReportsService` — `push()` upsert (on conflict overwrite), `list()`, `summary()` aggregate query
- DTO: `api/dto/ReportsDtos` (PushRunSummary validator: platform enum check)
- Entity + Repo: `domain/RunSummaryEntity`, `RunSummaryRepository` (custom aggregate query)
- Migration: `db/migration/V1__run_summaries.sql`
