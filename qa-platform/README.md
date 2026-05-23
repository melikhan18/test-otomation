# QA Platform

Self-hosted multi-platform test automation. Today: Android device farm — stream live device screens to the browser, control them remotely (tap/swipe/key/text), inspect UI elements, and run scenario / suite automations. Roadmap: iOS, backend (API), and web stacks slot in alongside Android under the same gateway, the same frontend, and the same shared kernel.

## Architecture (post-F5)

```
┌─────────────────┐  WS (binary, multiplexed) ┌────────────────────────────┐
│ Android Agent   │ ◄───────────────────────► │ android-bridge-service     │
│  (Kotlin APK)   │   video / control /       │   WebFlux / Netty WS       │
│                 │   inspect / heartbeat     │   fan-out + back-press     │
└─────────────────┘                           └─────────────┬──────────────┘
                                                            │
┌─────────────────┐  WS /ws/session/{id}/video              │
│ React Web       │ ◄──────────────────────────────────────┤
│ Console         │  REST /api/* + X-Platform header        │
│  WebCodecs      │                                          │
└─────────────────┘            │                             │
                               ▼                             │
                  ┌────────────────────────┐                 │
                  │ api-gateway (Spring CG)│                 │
                  │ header-based dispatch  │                 │
                  └─┬──────┬────────┬──────┘                 │
                    │      │        │                        │
       shared kernel│      │ android platform stack          │
         auth /     │      │  android-device /               │
         tenant     │      │  android-session /              │
                    │      │  android-automation /           │
                    ▼      ▼        ▼                        │
                  ┌──────────────────────────┐               │
                  │ Spring Boot REST         │ ──────────────┘
                  │ + PostgreSQL 16          │     Redis pub/sub
                  │ + Redis 7  + MinIO       │     (locks, online)
                  └──────────────────────────┘
```

| Service                    | Port | Layer    | Description                                                              |
|----------------------------|------|----------|--------------------------------------------------------------------------|
| api-gateway                | 8080 | shared   | Spring Cloud Gateway, JWT edge validation, header-based platform dispatch |
| auth-service               | 8081 | shared   | User login, JWT issuance/refresh, companies & projects                   |
| tenant-service             | 8089 | shared   | `project_platforms` — which platforms each project has activated         |
| android-device-service     | 8082 | android  | Device registry, enrollment tokens, agent heartbeat (Redis TTL)          |
| android-session-service    | 8083 | android  | Device reservation (Redis lock), session JWT                             |
| android-bridge-service     | 8084 | android  | **WebFlux/Netty** WS hub between agent and web (video + control + inspect) |
| android-automation-service | 8085 | android  | Scenarios, suites, runs, elements, APK repository                        |
| web-console (dev)          | 3000 | frontend | Vite + React 18 + Tailwind                                               |

## Stream Protocol

Each WebSocket binary message is exactly one frame:
```
[1 byte type][payload bytes]
```

| type | direction      | payload                                            |
|------|----------------|----------------------------------------------------|
| 0x01 | agent → hub    | H.264 keyframe (SPS+PPS+IDR, Annex-B)              |
| 0x02 | agent → hub    | H.264 delta frame                                  |
| 0x03 | hub → agent    | Control cmd JSON (tap / swipe / key / text)        |
| 0x04 | hub → agent    | Inspect request JSON `{"requestId":"…"}`           |
| 0x05 | agent → hub    | Inspect response JSON (node tree)                  |
| 0x06 | bidirectional  | Heartbeat                                          |
| 0x07 | agent → hub    | Stream metadata JSON (width/height/fps/codec)      |
| 0x08 | hub → agent    | Force keyframe (no payload)                        |
| 0x0B–0x12 | both       | APK repository ops (install / launch / reset)       |

## Quick Start — Docker (full stack)

```bash
# 1. Build & start everything (~3-5 min first time for Gradle compilation)
docker compose up -d --build

# 2. Wait for health and open
docker compose ps
open http://localhost:8080   # api gateway
```

Frontend is intended to be served separately in development:

```bash
cd frontend/web-console
pnpm install
pnpm dev   # http://localhost:3000  (proxies /api and /ws to gateway)
```

Default login: `admin / Admin@123`.

## Quick Start — Local development (no Docker for services)

The Gradle root sits at the repo root (no `backend/` subdir anymore — F1 refactor).

```bash
# 1. Infrastructure only (postgres + redis + minio)
docker compose up -d postgres redis minio minio-init

# 2. Start each service in its own terminal (from repo root)
./gradlew :auth-service:bootRun
./gradlew :tenant-service:bootRun
./gradlew :android-device-service:bootRun
./gradlew :android-session-service:bootRun
./gradlew :android-bridge-service:bootRun
./gradlew :android-automation-service:bootRun
./gradlew :api-gateway:bootRun

# 3. Frontend
cd frontend/web-console && pnpm install && pnpm dev

# 4. Android agent
cd agent/android-agent
./gradlew installDebug   # or open in Android Studio
```

## End-to-end Smoke Test

1. Backend stack healthy: `curl http://localhost:8080/actuator/health`
2. Login as admin in the web console → **Devices** page
3. Click **Generate enrollment token** (admin only) → copy the token
4. Launch the agent APK on an Android emulator (API 34) or real device
   - Backend URL: `http://10.0.2.2:8080` (emulator) or `http://<host-ip>:8080`
   - Paste the enrollment token → **Enroll & Start**
   - Tap **Grant Screen Capture** → accept the system dialog
   - Open **Settings → Accessibility** → enable “Device Farm Agent”
5. Refresh the web Devices page — the device should appear **ONLINE** within ~5 s
6. Click **Connect** → you land in the session view with the live device screen
7. Click anywhere on the canvas → it taps the device
8. Drag → swipe
9. Click **Inspect** in the right panel → tree appears with the active window
10. Select any node → copy the xpath / resource-id

Performance targets (Phase 11 goals):
- Glass-to-glass latency: **< 350 ms** (emulator on the same host)
- Bridge p99 frame-forward latency: **< 20 ms**
- Drop rate: **< 2%** at 30 fps
- 10+ parallel devices on a single bridge instance

Prometheus metrics: `http://localhost:8084/actuator/prometheus` (look for `bridge_frames_in_total`, `bridge_frames_out_total`, `bridge_frames_dropped_total`).

## Repository Layout

```
qa-platform/
├── README.md
├── docker-compose.yml                 # full stack (infra + all services)
├── Dockerfile                         # shared multi-stage build; --build-arg SERVICE + SERVICE_PATH
├── settings.gradle.kts                # Gradle multi-module root
├── build.gradle.kts
├── gradle/, gradlew, gradlew.bat
│
├── shared/                            # platform-agnostic kernel (reused by every stack)
│   ├── common/                        # JWT, errors, util — autoconfigured library
│   ├── auth-service/                  # :8081 — users, companies, projects, JWT
│   ├── tenant-service/                # :8089 — project_platforms
│   └── api-gateway/                   # :8080 — Spring Cloud Gateway
│
├── products/                          # per-platform stacks
│   └── android/
│       └── backend/
│           ├── device-service/        # :8082
│           ├── session-service/       # :8083
│           ├── device-bridge-service/ # :8084  (WebFlux)
│           └── automation-service/    # :8085  (+ APK repo, scenarios, runs)
│   (future: ios/, backend/, web/)
│
├── frontend/
│   └── web-console/                   # Vite + React 18 + TS + Tailwind
│
├── agent/
│   └── android-agent/                 # Kotlin app — MediaProjection + AccessibilityService
│
├── docs/architecture.md               # F0 spec — service catalog, routing, tenancy
│
└── deploy/
    └── sql/01-init-schemas.sql        # creates auth / tenant / android_* schemas
```

## Security Notes

- JWT uses HS512 with a shared secret. **Override `JWT_SECRET`** for any non-local deployment (`openssl rand -base64 64`).
- Enrollment tokens are single-use, expire in 15 min, and bind the device to the issuer's project.
- Sessions hold an exclusive Redis lock on the device for 30 min, refreshable.
- Agent ↔ bridge token is a long-lived (365 d) agent JWT created at enrollment.
- The web console is opinionated for Chrome/Edge: WebCodecs `VideoDecoder` is required for H.264 decode.

## License

Internal / proprietary.
