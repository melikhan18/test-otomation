# tenant-service

> **Port 8089** · Schema `tenant`. Hangi platform stack'i (Android / iOS / Backend / Web) hangi projede aktif — onu söyler. F5 deliverable.

## Sorumluluk
Multi-platform mimari 4-boyutlu tenancy üzerine kurulu: **Company → Project → Platform → Resources**. Project ve Company sahipliği auth-service'te; bu servis sadece "şu proje şu platform'u aktive etmiş mi?" sorusunu cevaplar. Frontend'in workspace switcher'ında platform dropdown'unun doğru değerleri göstermesini, automation servislerinin de "bu projede ANDROID enable mı?" check'i yapabilmesini sağlar.

> 🚧 F5 skeleton — auth-service hâlâ tenancy'nin diğer kısımlarını (company/project/members) sahipleniyor. İleride buraya konsolide olacak; bugünkü kapsam yalnız `project_platforms`.

## Veri (`tenant` schema)
| Entity | İçerik |
|---|---|
| `ProjectPlatformEntity` | (project_id, platform) UNIQUE — bir proje hangi platformları açmış. `platform`: `ANDROID` / `IOS` / `BACKEND` / `WEB`. `enabledAt`, `enabledBy` (user_id). |

V1 migration mevcut Android projelerini otomatik backfill ediyor (auth.projects'i okur, hepsine `ANDROID` ekler) — F5 ile gelen tablonun ilk kez devreye girmesi mevcut akışı bozmasın diye.

## API yüzeyi
Base path: `/api/tenancy/projects/{projectId}/platforms`

| Method | Endpoint | Ne yapar |
|---|---|---|
| `GET` | `/` | Projedeki aktif platformları listele |
| `POST` | `/` | Bir platformu aktive et (`{platform: "IOS"}`) — platformAdmin ya da company OWNER |
| `DELETE` | `/{platform}` | Bir platformu deaktive et |

Validasyon: `platform` enum sınırı (ANDROID / IOS / BACKEND / WEB), 400 atılır geçersizse.

## Bağımlılıklar
- **Diğer servisler**: auth-service'in V3 tenancy migration'ı bitmeli (compose `depends_on: condition: service_healthy`) — V1 backfill auth.projects'i okuduğu için.
- **Cross-schema FK yok**: tenant tablosu auth.projects'e FK koymuyor, sadece project_id'yi referans tutuyor. Bağımsız migration'lar.
- **Infra**: PostgreSQL.

## Yapılandırma
| Env | Açıklama |
|---|---|
| `DB_*` | Postgres |
| `JWT_SECRET` | Edge'de gateway zaten doğrular ama servis kendi başına da independently securable |

## Kod kılavuzu
- Giriş: [`TenantApplication.java`](src/main/java/com/qaplatform/shared/tenant/TenantApplication.java)
- Controller: `api/ProjectPlatformController`
- Service: `service/ProjectPlatformService` (yetki check'i: company OWNER veya platformAdmin)
- Entity + Repo: `domain/ProjectPlatformEntity`, `ProjectPlatformRepository`
- Migration: `db/migration/V1__project_platforms.sql`
