# android-device-service

> **Port 8082** · Schema `android_device`. Android stack'inin cihaz envanteri. Enrollment, registry, online state.

## Sorumluluk
- **Enrollment**: admin web console'dan tek-kullanımlık token üret (TTL 15dk). Agent o token'la `/api/agent/enroll`'a POST atar → karşılığında 365 günlük AGENT JWT alır + DB'de `Device` satırı oluşur.
- **Registry**: enroll olmuş tüm cihazları listele (model, manufacturer, Android sürümü, çözünürlük). Web console buradan `/devices` sayfasını render eder.
- **Heartbeat**: agent her ~5 saniyede `/api/agent/heartbeat`'e POST atar; servis Redis'te `device:online:{deviceId}` TTL ~20 saniye olarak set'ler. session-service ve reaper bu TTL'ye bakarak "cihaz canlı mı" sorusunu cevaplar.
- **Project-level access**: bir cihaz hangi projeden erişilebilir? `DeviceProjectAccess` whitelist tablosu (default: hepsi).

## Veri (`android_device` schema)
| Entity | İçerik |
|---|---|
| `Device` | manufacturer, model, androidVersion, sdkInt, abis, realWidth/realHeight, fingerprint (unique), companyId, productId |
| `DeviceProjectAccess` | (device_id, project_id) — whitelist; boşsa cihaz tüm projelerden erişilebilir |
| `EnrollmentToken` | tek-kullanımlık token, expiresAt, issuedByUserId, companyId, projectId |

## API yüzeyi
| Endpoint | Kim çağırır | Ne yapar |
|---|---|---|
| `POST /api/devices/enrollment-tokens` | Admin user | Yeni enrollment token üret |
| `GET  /api/devices` | User (gateway forward) | Aktif cihaz listesi + online state |
| `GET  /api/devices/{id}` | User | Tek cihaz detay |
| `PATCH /api/devices/{id}/access` | Admin | Project whitelist güncelle |
| `POST /api/agent/enroll` | Agent | Enrollment → AGENT JWT |
| `POST /api/agent/heartbeat` | Agent | Online ping (Redis TTL refresh) |

## Bağımlılıklar
- **Diğer servisler**: yok (responder). session-service ve OrphanedSessionReaper Redis'i okur ama HTTP çağrısı yapmaz.
- **Infra**: PostgreSQL + **Redis** (heartbeat TTL key'leri).

## Redis key'leri
```
device:online:{deviceId}   value: agent timestamp · TTL: 20s (her heartbeat'te refresh)
```

## Yapılandırma
| Env | Açıklama |
|---|---|
| `DB_*` | Postgres |
| `REDIS_HOST` / `REDIS_PORT` | Redis bağlantısı |
| `JWT_SECRET` | AGENT JWT'yi imzalamak için (auth-service'le aynı) |
| `app.device.heartbeat-ttl-seconds` | Redis TTL (default 20) |

## Kod kılavuzu
- Giriş: [`DeviceApplication.java`](src/main/java/com/qaplatform/android/device/DeviceApplication.java)
- Controller: `api/DeviceController` (kullanıcı yüzü), `api/AgentController` (agent yüzü — JWT olmadan enroll endpoint'i hariç AGENT JWT zorunlu)
- Service: `service/DeviceService`, `service/EnrollmentService`, `service/HeartbeatService`
- Entity'ler: `domain/Device`, `DeviceProjectAccess`, `EnrollmentToken`
- Migration: `db/migration/V*.sql` (Android'in eski full set'i F2'de bu schema'ya taşındı)
