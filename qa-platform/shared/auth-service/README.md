# auth-service

> **Port 8081** · Schema `auth`. Kimlik + tenancy kerneli. Login, JWT, şirket/proje üyelikleri, bildirimler.

## Sorumluluk
- **Kimlik**: kullanıcı login + signup, BCrypt parola hash, access + refresh JWT issuance.
- **Tenancy hiyerarşisi**: Company → Project → Members (her project'te per-user rol).
- **Bildirim**: davet/atanma gibi olaylar için DB-kalıcı bildirim + SSE feed.
- **Platform admin**: cross-tenant operasyonlar için root rol.
- **İlk açılış seeding**: `admin / Admin@123` (env'le override) — sadece kullanıcı yoksa.

Bu servis sistemdeki tek kimlik kaynağı. JWT'yi imzalar, herkes (gateway + 7 diğer servis) aynı secret'la doğrular.

## Veri (`auth` schema)
| Entity | İçerik |
|---|---|
| `User` | username, email, BCrypt parola, role |
| `Company` | tenancy üst seviye — sluglu, owner'lı |
| `Project` | company altında, isimli |
| `CompanyMember` | user + company + role (OWNER / MEMBER) |
| `ProjectMember` | user + project + role (QA_MANAGER / TESTER / VIEWER) |
| `CompanyInvitation` | tokenized davet (TTL'li) |
| `Notification` | per-user feed item |
| `Product` | legacy organizasyon birimi (F2 öncesi tenancy modeli — yeni kayıtta company_id zorunlu) |
| `RefreshToken` | refresh JWT'ler için DB-tracked rotasyon |

## API yüzeyi
| Endpoint | Ne yapar |
|---|---|
| `POST /api/auth/login` | Kullanıcı/şifre → access + refresh JWT |
| `POST /api/auth/signup` | Yeni kullanıcı + default company |
| `POST /api/auth/refresh` | Refresh JWT → yeni access token |
| `POST /api/auth/logout` | Refresh token revoke |
| `GET  /api/companies` · `POST /api/companies` | Kullanıcının şirketleri + yeni şirket |
| `GET  /api/companies/{id}/projects` · `POST .../projects` | Proje listesi/yarat |
| `GET  /api/companies/{id}/members` · `POST .../invitations` | Üyelik + davet |
| `GET  /api/notifications` · `GET .../stream` (SSE) | Bildirim feed + canlı stream |
| `GET  /api/admin/users` | Platform admin: kullanıcı yönetimi |

## Bağımlılıklar
- **Diğer servisler**: yok (purely data service; gateway dışındaki kimse direkt çağırmaz)
- **Infra**: PostgreSQL (zorunlu), Redis (gerek yok)

## Yapılandırma
| Env | Açıklama |
|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | Postgres bağlantısı (default db: `qa_platform`) |
| `JWT_SECRET` | HS512 secret (base64, ≥64 byte) — **tüm servisler aynı değeri kullanır** |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | İlk seeding için |

Application config: [`src/main/resources/application.yml`](src/main/resources/application.yml). JWT TTL'leri `JwtProperties` defaults (access 30dk, refresh 14gün).

## Kod kılavuzu
- Giriş: [`AuthApplication.java`](src/main/java/com/qaplatform/shared/auth/AuthApplication.java)
- Controller'lar: `api/AuthController`, `api/CompanyController`, `api/ProjectController`, `api/MemberController`, `api/NotificationController`, `api/AdminUserController`
- JWT issuance: `service/AuthService` → `JwtTokenService` (from `:common`)
- Tenancy guard helper'ları: `JwtPrincipal` (`:common`) üzerinde `canManageProject` / `isOwnerOf`
- DDL: `db/migration/V*.sql` (V1 schema, V3 tenancy backfill, V10 ek kolonlar)
