# common

> Paylaşılan Java kütüphanesi. **Bir servis değil**, deploy edilmez. Her servisin `build.gradle.kts`'ında `implementation(project(":common"))` ile bağlanır. Bütün stack'in ortak tip'leri burada.

## İçindekiler

### `com.qaplatform.common.jwt` — JWT issuance + validation
- **`JwtTokenService`**: HMAC HS512 ile token üretir + parse eder. 5 token tipi:
  - `issueUserAccessToken` — 30 dk, kullanıcı için (role + company tree dahil)
  - `issueRefreshToken` — 14 gün
  - `issueAgentToken` — 365 gün (cihazda kalıcı)
  - `issueSessionToken` — 2 saat (WS subprotocol auth için)
  - `issueServiceToken` — 30 dk (s2s, role=SERVICE, platformAdmin=true) — F7'de eklendi
- **`JwtPrincipal`** record: parse'lanmış JWT. Helper'lar: `canManageProject(projectId)`, `isOwnerOf(companyId)`, `roleInProject(projectId)`, `platformAdmin`.
- **`JwtAuthFilter`**: Spring Security filter — Bearer token'ı parse'layıp `SecurityContext`'e `JwtPrincipal` yerleştirir. Her servis kendi `SecurityConfig`'inde `addFilterBefore` ile zincire ekler.
- **`JwtProperties`**: Spring `@ConfigurationProperties("app.jwt")` — secret + issuer + TTL'leri config'den alır.

### `com.qaplatform.common.error` — Hata modeli
- **`ApiException`** — Spring'in `ProblemDetail` (RFC 7807) ile uyumlu. `ApiException.badRequest(msg)`, `ApiException.notFound(msg)`, `ApiException.unauthorized(msg)`, `ApiException.forbidden(msg)` factory'leri.
- **`ApiExceptionHandler`** `@ControllerAdvice` — `ApiException`'ı `application/problem+json` response'a çevirir. Her servis kendi `@Configuration`'ında bunu pick'ler (autoconfig).

### `com.qaplatform.common.tenancy` — Header sabitleri
- **`TenancyHeaders`** — `X-Company-Id`, `X-Project-Id`, `X-Platform` literal'leri. Frontend axios interceptor'ı bu header'ları her isteğe ekler; backend controller'lar `@RequestHeader(TenancyHeaders.COMPANY_ID)` ile okur.

### `com.qaplatform.common.runengine` — Run engine SPI (F6 deliverable)
Platform-agnostik test çalıştırma kontratı. Her platform stack'i `StepExecutor`'ı kendi step set'i için implement eder; orchestrator (bugün Android içinde, ileride buraya taşınacak) bu SPI üzerinden çalışır.

#### `status/` — terminal enum'lar
| Enum | Değerler |
|---|---|
| `RunStatus` | QUEUED → RUNNING → PASSED / FAILED / ERROR / CANCELLED |
| `SuiteRunStatus` | Aynı şekil, suite-aggregate |
| `StepResultStatus` | PENDING → RUNNING → PASSED / FAILED / SKIPPED / ERROR |

JPA `@Enumerated(EnumType.STRING)` ile persist. Her platform aynı enum'u kullanır → reports-aggregator translation tablosu gerektirmez.

#### `spi/` — extension noktaları
| Interface / record | Ne için |
|---|---|
| `StepExecutor` | **Tek dispatch noktası**. Platform impl: `execute(RunStep, StepContext) → StepOutcome`. |
| `StepContext` | Per-run scope: runId, companyId, projectId, platform, environment, mutable vars map, log + artifact sink, cancellation token |
| `StepOutcome` | Terminal step sonucu: status, errorMessage, resolvedLocator, screenshotPng, retriesUsed. `passed()` / `failed(reason)` / `error(reason)` / `skipped()` factory'leri. |
| `RunStep` | Opaque step contract: id, orderIndex, action (platform-specific string), payload (JSON, executor parse eder), timeoutMs |
| `ArtifactSink` | `uploadScreenshot(runId, stepResultId, png)`, `uploadVideo(runId, mp4)` — MinIO/S3 binding |
| `RunLogStream` | `info/warn/error` — per-run log fan-out (SLF4J + WS push kombinasyonu olabilir) |
| `CancellationToken` | `isCancelled()` — orchestrator + executor cooperative cancellation için |

Test/dry-run kolaylığı: `CancellationToken.NEVER`, `RunLogStream.DISCARD` sabitleri.

## Yapı
```
src/main/java/com/qaplatform/common/
├── jwt/
├── error/
├── tenancy/
└── runengine/
    ├── status/
    ├── spi/
    └── package-info.java
```

## Test
Henüz yok. JWT issuance + parse roundtrip için unit test eklenebilir.
