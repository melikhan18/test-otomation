# api-gateway

> **Port 8080** · Edge proxy. Tek dış kapı. JWT'yi kenarda doğrular, header-based platform dispatch yapar.

## Sorumluluk
Frontend ve dış istemciler **sadece bu servise** konuşur. Gateway:
- Bearer JWT'yi parse eder, geçersizse 401 atar (downstream'lere geçirmeden).
- `X-Platform` header'ına göre doğru platform stack'ine route eder (Android bugün canlı; iOS / Backend / Web slot'ları hazır).
- `/ws/**` trafiğini WebFlux ile bridge-service'e tunel'ler (binary frame'leri kıpırdatmadan).
- CORS politikasını uygular (`CORS_ALLOWED_ORIGINS` env).

## Veri
Yok. Stateless config-driven proxy.

## Route registry
Tüm rotalar [`application.yml`](src/main/resources/application.yml) `spring.cloud.gateway.routes` altında. Özet:

### Shared kernel (platform-agnostik)
| Path | Hedef | Açıklama |
|---|---|---|
| `/api/auth/**` | auth-service:8081 | Login, signup, JWT refresh |
| `/api/companies/**`, `/api/notifications/**`, `/api/admin/users/**` | auth-service:8081 | Şirket + üyelik + bildirim |
| `/api/tenancy/**` | tenant-service:8089 | `project_platforms` (hangi platform hangi projede aktif) |
| `/api/reports/**` | reports-aggregator-service:8090 | Cross-platform run rollup |

### Android stack (`X-Platform: ANDROID` predicate'i)
| Path | Hedef | Açıklama |
|---|---|---|
| `/api/devices/**`, `/api/agent/**` | android-device-service:8082 | Cihaz registry + enrollment |
| `/api/sessions/**` | android-session-service:8083 | Rezervasyon + Redis lock |
| `/api/bridge/**` | android-bridge-service:8084 | Senkron control/inspect REST |
| `/api/scenarios/**`, `/api/runs/**`, `/api/suites/**`, `/api/elements/**`, `/api/test-data/**`, `/api/apps/**` | android-automation-service:8085 | Otomasyon (path `/api/automation/...` olarak rewrite edilir) |
| `/ws/**` | android-bridge-service:8084 (WS) | Binary frame passthrough |

### Legacy fallback
F3'ten önceki tam-path rotalar (`/api/automation/**`, `/api/bridge/**`, vb.) hâlâ kayıtlı — frontend X-Platform header'ı göndermese bile Android'e düşer. Final cutover'da kaldırılabilir.

## Bağımlılıklar
- **Diğer servisler**: auth, tenant, reports, android-{device,session,bridge,automation} (hepsi compose `depends_on` ile aynı network'te)
- **Infra**: yok (DB yok, Redis yok, MinIO yok)

## Yapılandırma
| Env | Default | Açıklama |
|---|---|---|
| `SVC_AUTH` | http://localhost:8081 | auth-service upstream |
| `SVC_TENANT` | http://localhost:8089 | tenant-service upstream |
| `SVC_REPORTS` | http://localhost:8090 | reports-aggregator-service upstream |
| `SVC_DEVICE`, `SVC_SESSION`, `SVC_BRIDGE`, `SVC_AUTOMATION` | http://localhost:808{2,3,4,5} | Android stack upstream'leri |
| `SVC_BRIDGE_WS` | ws://localhost:8084 | WS tunel hedefi |
| `CORS_ALLOWED_ORIGINS` | localhost:* | Browser origin allow-list |
| `JWT_SECRET` | dev default | HS512 base64 secret (≥64 byte) |

## Yeni platform eklerken
Yeni bir stack için 4 şey:
1. `application.yml`'a `id: {platform}-automation-resources` route'u (X-Platform predicate ile)
2. `docker-compose.yml`'a `SVC_{PLATFORM}_AUTOMATION` env var
3. Aynı resource path'lerini kullan (`/api/scenarios/**` vb.) — frontend hiçbir şey değişmez
4. Yeni servisi compose `depends_on`'a ekle

Detay → [`products/_platform-template/README.md`](../../products/_platform-template/README.md), 5. adım.
