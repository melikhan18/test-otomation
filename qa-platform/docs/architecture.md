# QA Platform — Mimari Özet

Bu doküman platformun **multi-platform** mimarisini tanımlar: tek frontend + paylaşılan çekirdek (shared kernel) + her platform için bağımsız backend stack. Şu an Android stack canlı; iOS ve Backend (API) stack'ları için altyapı bu mimari ile hazırlanır.

## 1. Genel mimari

```
┌────────────────────────────────────────────────────────────────────────────┐
│                       Web Console (Tek SPA, React)                         │
│   Workspace switcher: Company → Project → Platform → Scenarios/Suites     │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      │ HTTPS
                                      ▼
                          ┌──────────────────────┐
                          │     API Gateway      │  ← header-based routing
                          │  X-Platform → stack  │
                          └──────────┬───────────┘
                                     │
        ┌────────────────────────────┼────────────────────────────┐
        │                            │                            │
        ▼                            ▼                            ▼
┌───────────────┐         ┌─────────────────────┐       ┌─────────────────────┐
│ SHARED CORE   │         │  ANDROID PLATFORM   │       │   iOS PLATFORM      │
│               │         │       STACK         │       │   (gelecek)         │
├───────────────┤         ├─────────────────────┤       ├─────────────────────┤
│ auth          │         │ android-automation  │       │ ios-automation      │
│ tenant        │         │ android-device      │       │ ios-device          │
│ reports-agg   │         │ android-session     │       │ ios-session         │
│ notifications │         │ android-bridge      │       │ ios-bridge          │
│ common (lib)  │         │   ↓ WS               │       │   ↓ WS               │
└───────────────┘         │ android-agent (APK) │       │ ios-agent (WDA)     │
                          └─────────────────────┘       └─────────────────────┘

                                                       ┌─────────────────────┐
                                                       │ BACKEND PLATFORM    │
                                                       │  (gelecek)          │
                                                       ├─────────────────────┤
                                                       │ backend-runner-svc  │
                                                       │  (stateless)        │
                                                       └─────────────────────┘
```

**Prensipler:**
1. **Tek API surface** — Frontend tek URL kullanır (`/api/runs`), platform `X-Platform` header'ı ile geçer. Gateway header'a göre doğru backend'e route eder.
2. **Tenancy 4 boyutlu** — Company → Project → Platform → Resources. Platform yatay bir tenancy dimension'ıdır (company ve project gibi).
3. **Platform stack autonomy** — Her platform kendi DB schema, kendi servisleri, kendi step DSL'i. Bağımsız evolve eder.
4. **Shared kernel** — Auth, tenant, reports, notifications shared. Her platform bu servisleri çağırır.
5. **Cross-platform reports** — Her platform run-event'lerini `reports-aggregator-service`'e yayınlar. Frontend tek dashboard'da hepsini görür.

## 2. Klasör yapısı

```
qa-platform/                              ← repo kök
├── shared/                                ← paylaşılan servisler
│   ├── api-gateway/
│   ├── auth-service/
│   ├── tenant-service/                    ← F5
│   ├── reports-aggregator-service/        ← F7
│   ├── notifications-service/             ← sonradan ayrılır
│   └── common/                            ← Maven artifact'leri (jwt, error, run-engine-lib)
│       ├── core/
│       └── run-engine/                    ← F6
│
├── products/
│   ├── android/                           ← Android Stack
│   │   ├── backend/
│   │   │   ├── android-automation-service/
│   │   │   ├── android-device-service/
│   │   │   ├── android-session-service/
│   │   │   └── android-bridge-service/
│   │   └── agent/
│   │       └── android-agent/
│   │
│   ├── ios/                               ← sonradan
│   ├── backend/                           ← sonradan (REST/GraphQL/gRPC tests)
│   ├── web/                               ← sonradan (Cypress/Playwright)
│   └── _template/                         ← F8 — yeni platform iskeleti
│
├── frontend/
│   └── web-console/                       ← tek SPA, multi-platform aware
│
├── deploy/
│   ├── docker-compose.yml
│   ├── sql/
│   └── k8s/                               ← sonradan
│
├── scripts/
│   └── generate-prod-env.sh
│
└── docs/
    └── architecture.md                    ← bu dosya
```

## 3. Servis catalog

### Shared kernel

| Servis | Port | DB schema | Sorumluluk |
|--------|------|-----------|------------|
| `api-gateway` | 8080 | — | Edge gateway, JWT validate, header-based routing |
| `auth-service` | 8081 | `auth` | Login, JWT issuance/refresh, user mgmt |
| `tenant-service` | 8089 | `tenant` | Company/Project/Platform tenancy + project_platforms (F5) |
| `reports-aggregator-service` | 8090 | `reports` | Cross-platform run rollup, dashboard veri kaynağı (F7) |
| `notifications-service` | 8091 | `notifications` | Slack/email/SSE (sonradan auth'tan ayrılır) |

### Android stack

| Servis | Port | DB schema | Sorumluluk |
|--------|------|-----------|------------|
| `android-automation-service` | 8085 | `android_automation` | Scenarios, suites, runs, elements, apps, test data, step DSL |
| `android-device-service` | 8082 | `android_device` | Cihaz registry, enrollment, heartbeat |
| `android-session-service` | 8083 | `android_session` | Cihaz rezervasyonu, Redis lock, session JWT |
| `android-bridge-service` | 8084 | — | WS hub agent↔web (video, control, inspect, install) |
| `android-agent` | — | — | Kotlin APK, cihazda çalışır |

### iOS stack (gelecek)

`ios-automation-service` (8086), `ios-device-service` (8092), `ios-session-service` (8093), `ios-bridge-service` (8094), `ios-agent` (Mac üstünde WDA).

### Backend stack (gelecek)

`backend-runner-service` (8087) — stateless HTTP test runner. Cihaz yok, bridge yok, agent yok.

### Web stack (gelecek)

`web-runner-service` (8088) — Playwright/Cypress headless browser.

## 4. API Gateway route registry

Header-based smart routing. Frontend platform-agnostic URL'ler kullanır, gateway `X-Platform` header'ına göre dispatch eder.

```yaml
# Shared (header gerekmez)
/api/auth/**         → auth-service
/api/tenancy/**      → tenant-service
/api/reports/**      → reports-aggregator-service

# Platform-aware (X-Platform zorunlu)
/api/runs/**         + X-Platform=ANDROID  → android-automation-service
/api/runs/**         + X-Platform=IOS      → ios-automation-service
/api/runs/**         + X-Platform=BACKEND  → backend-runner-service
/api/runs/**         + X-Platform=WEB      → web-runner-service

/api/scenarios/**    + X-Platform=...      → ilgili platform automation
/api/suites/**       + X-Platform=...      → ilgili platform automation
/api/apps/**         + X-Platform=ANDROID  → android-automation (APK)
/api/apps/**         + X-Platform=IOS      → ios-automation (IPA)
/api/endpoints/**    + X-Platform=BACKEND  → backend-runner (REST endpoints)
/api/test-data/**    + X-Platform=...      → ilgili platform automation
/api/elements/**     + X-Platform=ANDROID  → android-automation
/api/elements/**     + X-Platform=IOS      → ios-automation
/api/suite-runs/**   + X-Platform=...      → ilgili platform automation

/api/devices/**      + X-Platform=ANDROID  → android-device-service
/api/devices/**      + X-Platform=IOS      → ios-device-service
/api/sessions/**     + X-Platform=ANDROID  → android-session-service
/api/sessions/**     + X-Platform=IOS      → ios-session-service
```

## 5. Tenancy modeli

```
User                                                  
  ↓ üye olduğu                                        
Company (tenant root)                                 
  ├── Project A (e.g. BIP)                            
  │     ├── ANDROID  ← platform-enabled               
  │     ├── IOS                                       
  │     ├── BACKEND                                   
  │     └── WEB                                       
  ├── Project B                                       
  │     ├── ANDROID                                   
  │     └── BACKEND                                   
  └── Project C                                       
        └── BACKEND only                              
```

- **Project'in hangi platformlara açıldığı** `tenant.project_platforms (project_id, platform)` tablosunda tutulur.
- Frontend workspace switcher kullanıcıya **company → project → enabled platforms** sıralı seçim sunar.
- JWT taşır: `userId`, `companies` (membership tree), her project için role.
- `X-Platform` header her API isteğinde gönderilir (Workspace switcher'daki aktif platform).
- TenancyGuard backend'de: `requirePlatform(caller, projectId, platform)` — platform o projeye enabled mı + caller'ın o projede yetkisi var mı.

## 6. Naming conventions

### Java paketleri

| Tip | Kural | Örnek |
|-----|-------|-------|
| Shared kernel | `com.qaplatform.shared.{component}` | `com.qaplatform.shared.auth`, `com.qaplatform.shared.tenant` |
| Common lib | `com.qaplatform.common.{module}` | `com.qaplatform.common.error`, `com.qaplatform.common.runengine` |
| Android stack | `com.qaplatform.android.{component}` | `com.qaplatform.android.automation`, `com.qaplatform.android.device` |
| iOS stack | `com.qaplatform.ios.{component}` | (gelecek) |
| Backend stack | `com.qaplatform.backend.{component}` | (gelecek) |
| Web stack | `com.qaplatform.web.{component}` | (gelecek) |

### Servis adları

`{platform}-{component}-service` (Docker compose service adı + Spring `spring.application.name`):

| Şablon | Örnek |
|--------|-------|
| `android-{x}-service` | `android-automation-service`, `android-device-service` |
| `ios-{x}-service` | `ios-automation-service` (gelecek) |
| Shared istisna | `api-gateway`, `auth-service`, `tenant-service`, `reports-aggregator-service` (platform prefix yok — shared) |

### DB schema'lar

| Schema | Owner |
|--------|-------|
| `auth` | auth-service (shared) |
| `tenant` | tenant-service (shared, F5) |
| `reports` | reports-aggregator-service (shared, F7) |
| `android_device` | android-device-service |
| `android_session` | android-session-service |
| `android_automation` | android-automation-service |
| `ios_device`, `ios_session`, `ios_automation` | iOS stack (gelecek) |
| `backend_automation` | backend-runner-service (gelecek) |

### Environment variables

`{PLATFORM}_{COMPONENT}_{KEY}` — örn. `ANDROID_AUTOMATION_DB_HOST`. Shared: prefix yok (`JWT_SECRET`, `REDIS_HOST`, vb.).

## 7. Yol haritası

| Faz | İçerik | Süre | Mevcut Android davranışı |
|-----|--------|------|--------------------------|
| **F0** | Bu doc | yarım gün | Etkilemez |
| **F1** | Klasör reorganizasyonu (`shared/`, `products/android/`) — yalnız taşıma | 1-2 gün | Korur (sadece path değişimi) |
| **F2** | Naming refactor: Java paket, servis adı, DB schema rename | 5-6 gün | DB rename + paket rename — agresif. Data kaybı kabul. |
| **F3** | API Gateway header-based routing | 1 gün | Frontend hâlâ eski URL'lerle çalışır (transition route'ları) |
| **F4** | Frontend X-Platform header + workspace switcher 3. dropdown | 2-3 gün | Mevcut Android run akışı korunur, ek olarak platform dropdown |
| **F5** | `tenant-service` skeleton + `project_platforms` tablosu | 3-4 gün | Auth-service'in tenant logic'i tenant-service'e taşınır |
| **F6** | Run engine shared library (`common/run-engine`) | 3-4 gün | Android orchestrator bu interface'i implement eder |
| **F7** | `reports-aggregator-service` skeleton + event flow | 3-4 gün | Android run-event'leri aggregator'a düşer |
| **F8** | `_platform-template/` iskelet | 1-2 gün | Etkilemez |
| **Final** | Repo dizin rename + cutover | yarım gün | Klasör adı değişir |
| **Toplam** | | **~6-7 hafta** | |

## 8. Cutover stratejisi

Production data kaybı kabul edilmiş; rollback gerek yok. Her faz sonunda:

1. `docker compose down -v` (DB ve volume'ları sıfırla — gerekiyorsa)
2. `docker compose up -d --build` (yeni image'ler)
3. Flyway migration'ları otomatik çalışır
4. Smoke test (login, scenario yarat, run aç)
5. PR + main merge
6. `docker compose --profile prod up -d --build` (canlı ortam)

Her fazın başında bir `git checkout -b feat/Fx-{topic}` branch'i açılır, faz sonunda main'e merge.

## 9. Yeni platform ekleme rehberi (F8 sonrası)

iOS veya backend gibi yeni platform eklemek için (~1-2 hafta):

1. `products/_template/` klasörünü `products/{platform}/` olarak kopyala
2. Stub servis adlarını yenile (`{platform}-automation-service`, vs.)
3. Java paket adını yenile (`com.qaplatform.{platform}.*`)
4. DB schema'larını yenile (`{platform}_automation`, vs.)
5. `docker-compose.yml`'a yeni servisleri ekle
6. API Gateway route registry'ye yeni `X-Platform={PLATFORM}` route'larını ekle
7. Frontend'in step palette'ine platform-spesifik action'ları ekle
8. `tenant.project_platforms` tablosuna platform değerini ekle, projelerde enable edilebilir hale gelir
9. Run engine shared library'sini implement et — `RunOrchestrator`, `StepExecutor` interface'leri

Detay F8'de template README'sinde dokümante edilir.

## 10. Referanslar

- Sektör örnekleri: Atlassian (Jira/Confluence/Bitbucket), BrowserStack (App Live/Automate/API Testing), Sauce Labs, JFrog Platform
- Pattern: API Gateway Smart Routing + Modular Monolith → Microservices
- REST design felsefesi: Resource-centric + attribute-orthogonal (dimensions in headers, not URLs)
