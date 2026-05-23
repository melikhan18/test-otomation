# QA Platform

**Self-hosted, çok-platformlu test otomasyon platformu.** Tarayıcıdan canlı Android cihaz akışı, uzaktan kontrol (tap / swipe / klavye), UI inspector ile element yakalama, senaryo + suite tabanlı otomasyon ve cross-platform raporlama — hepsi tek bir gateway, tek bir SPA ve paylaşılan bir kernel üzerinde.

> 🚧 Bugün Android stack canlı; iOS, Backend (API) ve Web (Playwright/Cypress) stack'leri için kernel + template hazır, slot'lar bekliyor.

---

## 🎯 Bu proje ne yapıyor?

- **Cihaz çiftliği** — Android emulator/gerçek cihazlar enrollment token ile bir kez kayıt olur, ardından web konsolundan kim, hangi cihazı 30 dakikalık kilitle rezerve ederse onun olur.
- **Canlı stream + uzaktan kontrol** — Cihaz ekranı H.264 (30fps, ~720p) ile tarayıcıya akıyor, WebCodecs `VideoDecoder` ile yumuşak render. Mouse tap'i = cihaz tap'i; sürükle = swipe; klavye = metin gönderimi. Glass-to-glass hedefi **< 350 ms**.
- **UI inspector** — Cihazdaki Accessibility ağacını anlık çekip ağaç görünümü + lokator önerisi (xpath / resource-id / text) sunuyor; element'i tek tıkla katalogluyor.
- **Senaryo + suite otomasyonu** — 23 step action'lık bir DSL (CLICK, SWIPE, ASSERT_VISIBLE, ENTER_TEXT, …). Senaryolar element kataloğundan lokator referansı alır → run engine adım adım yürütür, başarısız step'lerde screenshot çeker, MP4 video kaydı tutar (MinIO).
- **APK repository** — Senaryo run'ından önce hedef APK otomatik install + launch + ana ekrana dönüş; sürüm uyumsuzluğunda agent yeniden yükler.
- **Cross-platform reports** — Her platform stack'i terminal run'larını paylaşılan `reports-aggregator-service`'e push eder; dashboard tek bakışta Android/iOS/Backend/Web başarı oranlarını gösterir.
- **Çok-kiracılı** — Company → Project → Platform → Resources hiyerarşisi, JWT'de tam üyelik ağacı, per-project rol bazlı erişim.

---

## 🏗️ Mimari

```
┌──────────────┐  WS (binary, multiplex)  ┌────────────────────────────┐
│ Android Agent│ ◄──────────────────────► │ android-bridge-service     │
│ (Kotlin APK) │  video/control/inspect   │  WebFlux + Reactor Netty   │
└──────────────┘                          └─────────────┬──────────────┘
                                                        │
┌──────────────┐  WS /ws/session/{id}/video             │
│ React 18 SPA │ ◄──────────────────────────────────────┤
│ web-console  │  REST /api/*  +  X-Platform header     │
└──────────────┘            │                           │
                            ▼                           │
                ┌────────────────────────┐              │
                │ api-gateway (8080)     │              │
                │ Spring Cloud Gateway   │              │
                │ JWT edge + header-     │              │
                │ based platform routing │              │
                └─┬─────┬─────┬──────────┘              │
                  │     │     │                         │
    ┌─────────────┘     │     └─────────────┐           │
    ▼                   ▼                   ▼           ▼
┌──────────┐    ┌─────────────┐    ┌────────────────────────┐
│ shared/  │    │ shared/     │    │ products/android/      │
│ auth +   │    │ tenant +    │    │ device/session/bridge/ │
│ reports  │    │ run-engine  │    │ automation services    │
│ services │    │ (lib)       │    │ + Kotlin agent         │
└──────────┘    └─────────────┘    └────────────────────────┘
       │              │                       │
       ▼              ▼                       ▼
   PostgreSQL 16     Redis 7              MinIO (S3)
   (6 schema)        (lock, online)       (screenshots, videos, apks)
```

**İlkeler:**
1. **Tek API surface** — Frontend tek URL kullanır (`/api/runs`), `X-Platform` header'ı ile gateway doğru backend'e route eder.
2. **Platform stack autonomy** — Her platform kendi DB schema'sı, kendi servisleri, kendi step DSL'i. Bağımsız evolve eder.
3. **Shared kernel** — Auth, tenant, reports kernelin parçası; her platform bunları çağırır.
4. **Cross-platform reports** — Tüm platformlar `reports-aggregator-service`'e run-event yayınlar; dashboard tek tabloda hepsini görür.

Detay için [`qa-platform/docs/architecture.md`](qa-platform/docs/architecture.md).

---

## 🛠️ Teknoloji yığını

### Backend
- **Java 21** · **Spring Boot 3.3** (Web MVC + WebFlux + Cloud Gateway + Data JPA + Security)
- **Gradle 8.10** çok-modül (`shared/` + `products/`)
- **PostgreSQL 16** · **Flyway** her servis kendi schema'sını yönetir
- **Redis 7** — distributed lock (Redisson SETNX), agent heartbeat TTL, session reservation
- **MinIO** — S3-uyumlu object storage (screenshots, videos, APK repository)
- **HS512 JWT** (jjwt) — issuer `qa-platform`, 4 principal tipi: USER / AGENT (365g) / SESSION (2s) / SERVICE (s2s)
- **WebSocket binary protocol** — 18 frame tipi (video / control / inspect / heartbeat / APK ops)

### Android agent
- **Kotlin** (minSdk 28, targetSdk 34)
- **MediaProjection + MediaCodec** — H.264 ekran encode (8 Mbps, 30fps, 720p)
- **AccessibilityService** — tap / swipe / metin / inspect tree

### Frontend
- **React 18** · **Vite** · **TypeScript** · **TailwindCSS**
- **Zustand** (state) · **TanStack Query** (server state) · **@dnd-kit** (drag-and-drop step editor)
- **WebCodecs `VideoDecoder`** — donanım hızlandırmalı H.264 decode (Chrome/Edge)

### DevOps
- **Docker Compose** (dev + `--profile prod`)
- **Caddy 2** — otomatik Let's Encrypt TLS (prod profili)
- **Prometheus** metrics endpoint'i (bridge frame counter'ları)

---

## 📁 Repo yapısı

```
test-otomation/                       ← GitHub repo kökü (gördüğün yer)
├── README.md                          ← bu dosya
└── qa-platform/                       ← projenin tamamı
    ├── README.md                      detaylı project README
    ├── BASLANGIC.md                   Türkçe sıfırdan kurulum kılavuzu
    ├── docker-compose.yml             tüm stack (infra + 8 servis)
    ├── Dockerfile                     paylaşılan multi-stage build
    ├── settings.gradle.kts            Gradle multi-module root
    │
    ├── shared/                        platform-agnostik kernel
    │   ├── api-gateway/               :8080 — Spring Cloud Gateway
    │   ├── auth-service/              :8081 — login, JWT, kullanıcı yönetimi
    │   ├── tenant-service/            :8089 — project_platforms (hangi platform hangi projede aktif)
    │   ├── reports-aggregator-service/ :8090 — cross-platform run rollup
    │   └── common/                    JWT, error, run-engine SPI (StepExecutor, vb.)
    │
    ├── products/
    │   ├── android/                   ← Android Stack (canlı)
    │   │   ├── backend/
    │   │   │   ├── device-service/    :8082 — registry, enrollment
    │   │   │   ├── session-service/   :8083 — reservation, Redis lock
    │   │   │   ├── device-bridge-service/ :8084 — reactive WS hub
    │   │   │   └── automation-service/ :8085 — scenarios, runs, APK repo
    │   │   └── agent/
    │   │       └── android-agent/     Kotlin APK
    │   └── _platform-template/        ← yeni platform eklemenin kopya kaynağı
    │
    ├── frontend/
    │   └── web-console/               Vite + React 18 + TS + Tailwind
    │
    ├── docs/
    │   └── architecture.md            spec — service catalog, routing, tenancy
    │
    └── deploy/
        ├── sql/                       Postgres init scripts (schema yaratma)
        └── docker-compose/            altyapı-yalnızca alternatif compose
```

---

## ⚡ Hızlı başlangıç

### Gereksinimler
- Docker Desktop 4.30+
- JDK 21 (lokal dev için, Docker'da varsa atla)
- Node.js 20 + pnpm 9 (frontend dev server için)
- Android Studio (agent APK derlemek için)

### Tüm stack'i ayağa kaldır
```bash
git clone https://github.com/melikhan18/test-otomation.git
cd test-otomation/qa-platform

# Backend + infra (postgres, redis, minio, 8 servis)
docker compose up -d --build           # ilk derleme ~3-5 dk

# Frontend (dev server, ayrı terminal)
cd frontend/web-console
pnpm install
pnpm dev                               # http://localhost:3000
```

Login: `admin / Admin@123`

Kapsamlı kurulum + ilk cihaz bağlama → [`qa-platform/BASLANGIC.md`](qa-platform/BASLANGIC.md)

---

## 📚 Detaylı dokümantasyon

| Doküman | Ne içeriyor |
|---|---|
| [`qa-platform/README.md`](qa-platform/README.md) | Teknik project README — full mimari, WS frame protokol, end-to-end smoke test, performans hedefleri |
| [`qa-platform/BASLANGIC.md`](qa-platform/BASLANGIC.md) | Türkçe sıfırdan kurulum kılavuzu — Docker / IDE her iki mod, agent kurulumu, sorun giderme |
| [`qa-platform/docs/architecture.md`](qa-platform/docs/architecture.md) | Mimari spec — servis catalog, port + schema listesi, gateway routing registry, tenancy modeli, yol haritası |
| [`qa-platform/products/_platform-template/README.md`](qa-platform/products/_platform-template/README.md) | Yeni platform eklemenin (iOS / Backend / Web) 10 adımlı reçetesi |

---

## 🗺️ Yol haritası

- ✅ **Android stack** — canlı, üretim kullanımına yakın
- ✅ **Multi-platform kernel** — auth, tenant, reports, run-engine SPI hazır (F0–Final refactor, 2026-05)
- ✅ **Platform template** — yeni stack için kopya-kaynak
- 🔜 **iOS stack** — WebDriverAgent tabanlı session/bridge/agent (~3-4 hafta)
- 🔜 **Backend stack** — stateless HTTP test runner (cihaz yok, bridge yok, ~1-2 hafta)
- 🔜 **Web stack** — Playwright/Cypress headless browser runner
- 🔜 **Dashboard widget** — reports-aggregator verisinden cross-platform success-rate grafiği

---

## 🤝 Katkı

Bu single-developer pet proje; PR / issue açmadan önce bir konuşalım. Architecture spec ve faz-bazlı refactor pratiği gereği değişiklikler ufak ve fasız ilerliyor.

## 📄 Lisans

İç kullanım / proprietary. Public visibility portföy amaçlı.
