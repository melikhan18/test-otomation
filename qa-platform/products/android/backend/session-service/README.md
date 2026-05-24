# android-session-service

> **Port 8083** · Schema `android_session`. Cihaz rezervasyonu + Redis distributed lock + session JWT issuance.

## Sorumluluk
- **Rezervasyon**: kullanıcı bir cihazda iş yapmadan önce 30 dk'lık exclusive lock alır. Aynı cihazı iki kişi aynı anda kontrol edemez.
- **Distributed lock**: Redis SETNX + Lua compare-and-swap. Release/touch sırasında token check yapılır — başka session'ın lock'unu yanlışlıkla bırakamazsın.
- **Session JWT**: lock alındığında 2 saatlik SESSION token üretilir. Bridge WS bağlantısı bu token'la açılır.
- **Orphan reaping**: agent ölürse (heartbeat kesilir) cihaz ACTIVE session'da takılı kalır — `OrphanedSessionReaper` 30 saniyede bir Redis'teki online TTL'i kontrol eder, agent offline'sa session END'lenir + lock release edilir.

## Veri (`android_session` schema)
| Entity | İçerik |
|---|---|
| `Session` | id, deviceId, userId, companyId, projectId, status (`ACTIVE`/`ENDED`/`EXPIRED`), startedAt, endedAt, lockToken (UUID — Redis lock'la eşleşir) |

## API yüzeyi
Base path: `/api/sessions`

| Method | Endpoint | Ne yapar |
|---|---|---|
| `POST` | `/` | `{deviceId}` → Redis lock alır, Session row'u + SESSION JWT döner |
| `POST` | `/{id}/touch` | Lock TTL'ini 30 dk'a tazele (kullanıcı aktif olduğu sürece) |
| `DELETE` | `/{id}` | Session END + lock release. Lua script'le compare-and-swap (sadece kendi lock'unu silebilir) |
| `GET` | `/{id}` | Session detay (kullanıcı kendi açtığını görür) |

## Bağımlılıklar
- **Diğer servisler**: HTTP çağrısı yok (responder).
- **Indirect coordination**: Redis üzerinden device-service'in heartbeat TTL'ine bağımlı — `OrphanedSessionReaper` `device:online:{deviceId}` key'ini polluyor.
- **Infra**: PostgreSQL + **Redis** (kritik).

## Redis key'leri
```
device:lock:{deviceId}     value: sessionToken (UUID)   TTL: 30m   (rezervasyon)
device:session:{deviceId}  value: sessionId            TTL: 30m   (device-service'in "IN_USE" badge'i için)
device:online:{deviceId}   (device-service yazar, bu servis okur — reaper için)
```

Tüm release/touch ops Lua script ile — yanlış lock'u bırakma race'ini engeller.

## Background jobs
- **`OrphanedSessionReaper.sweep()`** · `@Scheduled(fixedDelay = 30000)` · 30 saniyede bir tüm ACTIVE session'ları gözden geçirir; agent offline'sa (`device:online:` yok) session'ı END'ler + lock release.

## Yapılandırma
| Env | Açıklama |
|---|---|
| `DB_*` | Postgres |
| `REDIS_HOST` / `REDIS_PORT` | Redis bağlantısı |
| `JWT_SECRET` | SESSION token'ı imzalamak için |
| `app.session.lock-ttl-minutes` | Default 30 |

## Kod kılavuzu
- Giriş: [`SessionApplication.java`](src/main/java/com/qaplatform/android/session/SessionApplication.java)
- Controller: `api/SessionController`
- Service: `service/SessionService` — reserve / touch / release
- Lock: `service/lock/DeviceLockService` — Lua script wrapper'ı, Redisson kullanıyor
- Reaper: `service/OrphanedSessionReaper`
- Entity: `domain/Session`
- Migration: `db/migration/V*.sql`
