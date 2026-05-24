# android-bridge-service

> **Port 8084** · Reactive (WebFlux + Reactor Netty). DB yok. Agent ↔ Web canlı binary köprüsü. 18 frame tipi, 30 fps H.264 passthrough.

## Sorumluluk
- **Agent WS bağlantısı**: agent başlatıldığında AGENT JWT ile `/ws/agent`'e bağlanır; video keyframe/delta + heartbeat akar.
- **Web WS bağlantısı**: kullanıcı session açtığında frontend `/ws/session/{id}/video?token=...` ile bağlanır (SESSION JWT). Bridge bu subscriber'ı agent'ın frame stream'ine fan-out eder.
- **Control yön**: web'den gelen tap/swipe/key/text → agent'a iletir. Synchronous "step execution" için REST surface'i de var (`/api/bridge/sessions/{id}/control`) — automation-service buradan tek tek step gönderir.
- **Inspect**: web bir node ağacı isterse → agent'a inspect request, agent'tan gelen JSON tree'yi web'e döndürür.
- **APK ops**: install / launch / reset-home request/response pair'leri (frame type'ları 0x0B–0x12).
- **Forced keyframe**: yeni bir web subscriber attach olduğunda agent'a "bir sonraki keyframe'i hemen üret" mesajı gönderilir → bekleme süresi azalır.

## Bu servis neden farklı?
- **Tek WebFlux servisi**: tüm diğer servisler MVC + JPA bloklayan stack. Burada blocking yasak — `.block()` veya JDBC çağırırsan p99 frame latency'yi mahveder.
- **Stateless**: hiç DB tablo'su yok. Frame buffer'ı + subscriber map'i bellekte; kalıcı state yok.
- **Performance bütçesi**: p99 frame-forward < 20 ms, drop rate < 2% @ 30 fps, 10+ parallel cihaz/instance.

## Veri
Yok. Bellek-içi:
- Per-device frame buffer (ringbuffer, default 60 frame)
- Per-session subscriber listesi

## Frame protokolü
Her WS binary mesaj 1 byte type prefix + payload:

| Type | Yön | Payload |
|---|---|---|
| 0x01 | agent → hub → web | H.264 keyframe (SPS+PPS+IDR, Annex-B) |
| 0x02 | agent → hub → web | H.264 delta frame |
| 0x03 | hub → agent | Control JSON (tap/swipe/key/text) |
| 0x04 | hub → agent | Inspect request `{requestId}` |
| 0x05 | agent → hub → web | Inspect response (node tree JSON) |
| 0x06 | iki yön | Heartbeat |
| 0x07 | agent → hub | Stream metadata (width/height/fps/codec) |
| 0x08 | hub → agent | Force keyframe (no payload) |
| 0x09 / 0x0A | request/response | Screenshot — `[4-byte BE metaLen][JSON][PNG]` |
| 0x0B / 0x0C | request/response | App info (`{requestId, packageName}` → installed + versionCode) |
| 0x0D / 0x0E | request/response | Install APK (`downloadUrl, sha256, expectedVersionCode`) |
| 0x0F / 0x10 | request/response | Launch app |
| 0x11 / 0x12 | request/response | Reset home + opsiyonel kill process |

## REST surface (automation-service için)
| Endpoint | Ne yapar |
|---|---|
| `POST /api/bridge/sessions/{id}/control` | Synchronous step: agent'a control gönder, deadline'a kadar ack bekle |
| `POST /api/bridge/sessions/{id}/inspect` | Inspect req → response (timeout'lu) |
| `POST /api/bridge/sessions/{id}/screenshot` | PNG bytes döner |
| `POST /api/bridge/sessions/{id}/apps/{packageName}/install` | APK install start + status |
| `POST /api/bridge/sessions/{id}/apps/{packageName}/launch` | Cold start |
| `POST /api/bridge/sessions/{id}/apps/{packageName}/reset-home` | HOME tuşu + opsiyonel kill |
| `POST /api/bridge/sessions/{id}/recording/start` · `/stop` | MediaProjection-based video kaydı (mp4 byte'ları döner) |

## Bağımlılıklar
- **Diğer servisler**: yok (responder). automation-service REST üzerinden bunu çağırır.
- **Infra**: **Redis** (frame buffer back-pressure metrik'leri + multi-instance fan-out olasılığı için pub/sub hazır).

## Yapılandırma
| Env | Açıklama |
|---|---|
| `REDIS_HOST` / `REDIS_PORT` | Redis bağlantısı |
| `JWT_SECRET` | AGENT + SESSION token'ları doğrulamak için |
| `app.bridge.web-buffer-size` | Per-subscriber frame buffer'ı (default 60) |
| `app.bridge.force-keyframe-on-subscribe` | Yeni subscriber için forced keyframe (default true) |
| `app.bridge.heartbeat-timeout-seconds` | Agent heartbeat'i bu süre gelmezse channel kapatılır (default 25) |

## Metrics
`http://localhost:8084/actuator/prometheus`:
- `bridge_frames_in_total{deviceId}` · agent'tan gelen frame
- `bridge_frames_out_total{sessionId}` · web'e fan-out
- `bridge_frames_dropped_total` · buffer dolu → drop
- `bridge_subscribers_active{deviceId}` · canlı web sayısı

## Kod kılavuzu
- Giriş: [`BridgeApplication.java`](src/main/java/com/qaplatform/android/bridge/BridgeApplication.java)
- WS handler: `ws/AgentWebSocketHandler`, `ws/SessionVideoHandler`
- REST: `api/ControlRestController`, `api/AppControlRestController`
- Fan-out + frame buffer: `service/SessionFanOut`, `service/FrameBuffer`
- Frame type'lar: `protocol/FrameTypes`
