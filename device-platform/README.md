# Device Platform

Production-grade Android device farm — stream live device screens to the browser, control them remotely (tap/swipe/key/text), and inspect UI elements (resource-id, xpath, text). Self-hosted alternative to AWS Device Farm.

## Architecture

```
┌─────────────────┐  WS (binary, multiplexed) ┌───────────────────────┐
│ Android Agent   │ ◄───────────────────────► │ device-bridge-service │
│  (Kotlin APK)   │   video / control /       │  WebFlux / Netty WS   │
│                 │   inspect / heartbeat     │  fan-out + back-press │
└─────────────────┘                           └───────────┬───────────┘
                                                          │
┌─────────────────┐  WS /ws/session/{id}/video            │
│ React Web       │ ◄────────────────────────────────────┤
│ Console         │  WS /ws/session/{id}/control          │
│  WebCodecs      │  REST /api/*                          │
└─────────────────┘            │                          │
                               ▼                          │
                     ┌───────────────┐                    │
                     │ api-gateway   │                    │
                     │ (Spring CG)   │                    │
                     └───┬───┬───┬───┘                    │
                     auth│dev│ses│                        │
                         ▼   ▼   ▼                        │
                     ┌────────────────────┐               │
                     │ Spring Boot REST   │ ──────────────┘
                     │ + PostgreSQL 16    │     Redis pub/sub
                     │ + Redis 7          │     (locks, online)
                     └────────────────────┘
```

| Service                 | Port | Description                                                              |
|-------------------------|------|--------------------------------------------------------------------------|
| api-gateway             | 8080 | Spring Cloud Gateway, JWT edge validation, routes to all services        |
| auth-service            | 8081 | User login, JWT issuance/refresh                                         |
| device-service          | 8082 | Device registry, enrollment tokens, agent heartbeat (Redis TTL)          |
| session-service         | 8083 | Device reservation (Redis lock), session JWT                             |
| device-bridge-service   | 8084 | **WebFlux/Netty** WS hub between agent and web (video + control + inspect) |
| web-console (dev)       | 3000 | Vite + React 18 + Tailwind                                                |

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

```bash
# 1. Infrastructure only
docker compose -f deploy/docker-compose/infrastructure.yml up -d

# 2. Bootstrap Gradle wrapper (one time)
cd backend
gradle wrapper --gradle-version=8.10

# 3. Start each service in its own terminal
./gradlew :auth-service:bootRun
./gradlew :device-service:bootRun
./gradlew :session-service:bootRun
./gradlew :device-bridge-service:bootRun
./gradlew :api-gateway:bootRun

# 4. Frontend
cd ../frontend/web-console && pnpm install && pnpm dev

# 5. Android agent
cd ../../agent/android-agent
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
device-platform/
├── README.md
├── docker-compose.yml                  # full stack (infra + services)
├── backend/
│   ├── settings.gradle.kts             # multi-module Gradle build
│   ├── Dockerfile                      # shared build, switch via --build-arg SERVICE=…
│   ├── common/                         # JWT, security, errors (autoconfigured)
│   ├── auth-service/                   # :8081
│   ├── device-service/                 # :8082
│   ├── session-service/                # :8083
│   ├── device-bridge-service/          # :8084  (WebFlux)
│   └── api-gateway/                    # :8080
├── frontend/
│   └── web-console/                    # Vite + React 18 + TS + Tailwind
├── agent/
│   └── android-agent/                  # Kotlin app, MediaProjection + AccessibilityService
└── deploy/
    ├── docker-compose/infrastructure.yml   # postgres + redis only (IDE dev)
    └── sql/01-init-schemas.sql             # creates auth / device / session schemas
```

## Security Notes

- JWT uses HS512 with a shared secret. **Override `JWT_SECRET`** for any non-local deployment (`openssl rand -base64 64`).
- Enrollment tokens are single-use, expire in 15 min, and bind the device to the issuer's product.
- Sessions hold an exclusive Redis lock on the device for 30 min, refreshable.
- Agent ↔ bridge token is a long-lived (365 d) agent JWT created at enrollment.
- The web console is opinionated for Chrome/Edge: WebCodecs `VideoDecoder` is required for H.264 decode.

## License

Internal / proprietary.
