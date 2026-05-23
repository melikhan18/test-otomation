# QA Platform — Başlangıç Kılavuzu

Bu kılavuz projeyi sıfırdan çalıştırıp ilk cihazını bağlayıp web'den kontrol etmen için yeterli.

> Repo dizini hâlâ `device-platform/` — multi-platform refactor (F0–F8) bittikten sonra `qa-platform/` olarak yeniden adlandırılacak.

---

## 1. Ön Gereksinimler

| Araç | Sürüm | Niçin |
|------|-------|-------|
| **Docker Desktop** | 4.30+ (Windows) | Postgres + Redis + (opsiyonel) tüm servisler |
| **JDK 21** | Temurin 21 önerilir | Backend Spring Boot servisleri |
| **Gradle 8.10** | Tek seferlik | `gradlew` script'i oluşturmak için |
| **Node.js 20+ + pnpm** | pnpm 9+ | Frontend dev server |
| **Android Studio** | Koala 2024.1+ | Agent APK'sını derleyip emulator'a kurmak |

> Hızlı kontrol:
> ```powershell
> docker --version
> java --version    # 21 olmalı
> gradle --version  # 8.10
> node --version    # 20+
> pnpm --version    # 9+
> ```

---

## 2. İlk Kurulum (tek seferlik)

```powershell
cd C:\Users\melik\Desktop\test-otomation\device-platform

# 2.1 — Backend Gradle wrapper'ı zaten repo köküne commit edilmiş.
#        İlk kez kurulumda ekstra bir şey yapmana gerek yok; .\gradlew.bat direkt çalışır.

# 2.2 — Frontend bağımlılıklarını kur
cd frontend\web-console
pnpm install
cd ..\..
```

---

## 3. Servisleri Çalıştırma — 2 Mod

### Mod A: "Hızlı dene" — Her şey Docker'da

```powershell
docker compose up -d --build
```

İlk derlemede 3-5 dakika sürer (Gradle her servisi container içinde derler). Sonraki çalıştırmalarda cache devreye girer.

Durum kontrolü:
```powershell
docker compose ps
curl http://localhost:8080/actuator/health   # gateway
```

> Frontend'i ayrı çalıştırman gerek (dev server için):
> ```powershell
> cd frontend\web-console
> pnpm dev
> ```
> Tarayıcıdan: **http://localhost:3000**

### Mod B: "Geliştirme" — Sadece altyapı Docker'da, servisler IDE'den

```powershell
# Sadece postgres + redis + minio (uygulama servisleri host'tan)
docker compose up -d postgres redis minio minio-init

# Backend servisleri (her biri ayrı terminalde, repo kökünden)
.\gradlew.bat :auth-service:bootRun
.\gradlew.bat :tenant-service:bootRun
.\gradlew.bat :android-device-service:bootRun
.\gradlew.bat :android-session-service:bootRun
.\gradlew.bat :android-bridge-service:bootRun
.\gradlew.bat :android-automation-service:bootRun
.\gradlew.bat :api-gateway:bootRun
```

Veya **IntelliJ IDEA**: repo kökünü Gradle projesi olarak aç → her servisteki `*Application.java` üzerinde sağ tık → Run.

Frontend:
```powershell
cd frontend\web-console
pnpm dev
```

---

## 4. Web Console'a Giriş

1. Tarayıcıda **http://localhost:3000** aç
2. Login:
   - Kullanıcı: `admin`
   - Şifre: `Admin@123`
3. Otomatik olarak **/devices** sayfasına yönlenirsin (henüz boş — cihaz yok)

---

## 5. Android Agent'ı Hazırlama

### 5.1 — Emulator oluştur (Android Studio)

- **Tools → Device Manager → Create Device**
- Pixel 6 (veya benzeri), **API 34** (Android 14) öner.
- Emulator'ı başlat ve açık tut.

### 5.2 — Agent APK'sını derle ve kur

```powershell
cd agent\android-agent
gradle wrapper --gradle-version=8.10   # bir defa
.\gradlew.bat installDebug
```

Veya Android Studio'da `agent/android-agent` aç → **Run** (yeşil oynat butonu).

### 5.3 — Web'den enrollment token al

1. Web console'da sağ üst köşede **"Generate enrollment token"** butonuna tıkla (admin görüyor)
2. Token görünür — **Copy** butonuyla kopyala (15 dakika geçerli, tek kullanımlık)

### 5.4 — Agent'ı yapılandır

Emulator'da **Device Farm Agent** uygulamasını aç:

| Alan | Değer |
|------|-------|
| Backend URL | `http://10.0.2.2:8080` (emulator'dan host'a) |
| Enrollment Token | (web'den kopyaladığın) |

> **Önemli:** Emulator için `10.0.2.2` host PC'nin `localhost`'una bakar. Gerçek cihaz için PC'nin LAN IP'si (`ipconfig` → IPv4) gerekir: ör. `http://192.168.1.50:8080`.

Adımlar:
1. **"Enroll & Start"** → "enrolled — starting service" mesajı görünmeli
2. **"Grant Screen Capture"** → sistem dialog'unda "Start now" tıkla
3. **"Open Accessibility Settings"** → Android ayarlarında **Device Farm Agent**'ı aç (tap/swipe için zorunlu)
4. Agent uygulamasına geri dön; bildirimde "Online · streaming ready" görmelisin

### 5.5 — Web'de cihazı gör

Web console **/devices** sayfasına dön (5 sn'de bir refresh eder):

- Cihaz kartı **ONLINE** badge ile belirir
- Manufacturer, Model, Android sürümü, çözünürlük gözükür
- **Connect** butonuna tıkla → session açılır

---

## 6. Web'den Cihaz Kontrolü

Session sayfasında:

| Aksiyon | Nasıl |
|---------|-------|
| **Tap** | Ekrana tıkla |
| **Swipe** | Sürükle (mouse down → move → up) |
| **Back** | Sol alttaki **Back** butonu |
| **Home** | **Home** butonu |
| **Recents** | **Recents** butonu |
| **Metin yaz** | Alt input'a yaz → Enter ya da **Send** (önce ilgili field cihazda focus olmalı) |
| **Inspect** | Sağ panel → **Inspect** butonu → tree açılır |
| **Element xpath/id kopyala** | Tree'den bir node seç → sağda lokator detayları, copy butonları |
| **Session bitir** | Sağ üst **End session** |

> Session 30 dakikalık rezervasyon kilidi alır. Sayfa açıkken her 5 dakikada bir otomatik tazelenir; manuel için **Refresh lock**.

---

## 7. Yaygın Sorunlar

| Belirti | Çözüm |
|---------|-------|
| Web'de cihaz hâlâ OFFLINE görünüyor | Agent'taki **Backend URL** yanlış. Emulator için `10.0.2.2`, gerçek cihaz için PC'nin LAN IP'si. PC firewall'u `8080` portunu açık tutmalı. |
| `localhost:3000` "WebCodecs not supported" diyor | Chrome / Edge / Opera kullan (Firefox WebCodecs'i tam desteklemiyor) |
| Stream görüntüsü gelmiyor ("Waiting for video") | Agent'ta **Grant Screen Capture**'ı verdiğinden emin ol. Notification'da "Online · streaming ready" yazıyor mu kontrol et. |
| Tap/swipe çalışmıyor | **Settings → Accessibility → Device Farm Agent** kapalı. Aç. |
| Inspector "accessibility-service-not-enabled" hatası | Aynı şekilde Accessibility açık olmalı. |
| `docker compose up` çok yavaş | İlk derleme normal (3-5 dk). Sonraki run'larda layer cache devreye girer. Bilgisayar yavaşsa **Mod B**'yi kullan. |
| `pnpm dev` 8080'i bulamıyor | `vite.config.ts` proxy `http://localhost:8080` bekliyor — gateway çalışıyor mu? `curl http://localhost:8080/actuator/health` |
| Agent enrollment "expired" | Token 15 dakikalık. Yenisini üret. |
| Agent enrollment "already used" | Token tek kullanımlık. Yeni üret. (Aynı cihazı yeniden kayıt için: yeni token alıp **Re-enroll**.) |

---

## 8. Loglar ve Debug

```powershell
# Servis logları (Docker mode)
docker compose logs -f device-bridge-service
docker compose logs -f auth-service

# Postgres'e bağlan
docker exec -it $(docker compose ps -q postgres) psql -U dp -d device_platform
# > \dn         (şemaları listele — auth / tenant / android_device / android_session / android_automation)
# > select * from android_device.devices;

# Redis'e bağlan
docker exec -it $(docker compose ps -q redis) redis-cli
# > keys device:*

# Agent logları (emulator açıkken)
adb logcat -s AgentSocket:* ScreenCapture:* ControlA11y:* ControlExecutor:*

# Bridge metrikleri (frame sayıları, drop)
curl http://localhost:8084/actuator/prometheus | findstr bridge_
```

---

## 9. Temizlik

```powershell
# Servisleri durdur
docker compose down

# Veritabanını sıfırla (tüm data silinir!)
docker compose down -v
```

---

## 10. Sonraki Adımlar

- **Kendi makinenden gerçek bir telefonu** bağlamak: USB ile bilgisayara bağla → APK'yı `adb install` ile kur → backend URL'sine PC'nin LAN IP'sini yaz.
- **Yeni kullanıcı eklemek**: Şimdilik sadece admin var. `auth.users` tablosuna BCrypt hash'li yeni satır eklenebilir; kullanıcı yönetimi UI'ı henüz yok.
- **JWT secret değiştirmek**: Production'a almadan önce `JWT_SECRET` env değişkenini değiştir:
  ```powershell
  $env:JWT_SECRET = (openssl rand -base64 64)
  ```

İyi çalışmalar!
