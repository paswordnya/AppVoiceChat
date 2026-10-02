# Pip Voice Chat

Aplikasi asisten AI berbasis suara dan chat. Logika bisnis ditulis sekali di
modul Kotlin Multiplatform (`shared/`), lalu dipakai oleh aplikasi Android
(Jetpack Compose) dan iOS (SwiftUI). Pip berbicara Bahasa Indonesia secara
default dan menyesuaikan dengan bahasa yang dipakai pengguna.

## Struktur repo

```
.
├── shared/            Modul Kotlin Multiplatform: model, repository, use case, ViewModel,
│                      jaringan (Ktor), chat, voice, AI router, memory, analytics
├── androidApp/        Aplikasi Android (Compose), paket com.pip.app
├── iosApp/            Aplikasi iOS (SwiftUI), memakai shared lewat PipShared.xcframework
├── PipSharedPackage/  Swift Package pembungkus XCFramework dari shared/
├── prd/               Dokumen produk (PRD) dan desain ulang alur
├── ARCHITECTURE.md    Spesifikasi layar dan alur untuk prototype index.html
├── index.html         Prototype interaktif yang bisa dibuka langsung di browser
└── .github/workflows/ci.yml
```

`ARCHITECTURE.md` menjelaskan prototype `index.html` (alur layar, state, dan
event tiap ViewModel). Dokumen itu menggambarkan arsitektur target, bukan
implementasi saat ini baris demi baris.

## Fitur

- **Chat** lewat WebSocket, dengan status streaming (thinking, typing, done),
  riwayat percakapan, dan perintah slash (`/help`, `/start`, `/clear`,
  `/model`, `/voice`, `/listen`, `/status`).
- **Voice** dua arah lewat WebSocket: mic di-stream ke backend, balasan suara
  diputar balik, dengan mode tap-to-barge-in atau push-to-talk.
- **Akun**: login, daftar, profil, pengaturan, dan riwayat.
- **Mascot Pip** berbentuk blob dengan beberapa kepribadian: Pip, Bloop,
  Sunny, Coco.
- Chat dan voice berbagi satu ID sesi, jadi riwayatnya menyatu.

Detail fitur sisi iOS (modul Swift, alur Splash → Onboarding → Main) ada di
`iosApp/README.md`.

## Backend

Backend tidak ada di repo ini. Aplikasi mengharapkan service FastAPI yang
menyediakan, antara lain:

| Endpoint | Fungsi |
|---|---|
| `WS /ws/chat/{session_id}` | Chat streaming |
| `WS /ws/voice/{session_id}` | Voice dua arah |
| `GET /api/session/{session_id}/history` | Riwayat chat |
| `PUT /api/session/{session_id}/listen` | Mode mendengarkan |

Alamat default pengembangan: `http://localhost:8000` (iOS dan modul
`shared`), dan `http://10.0.2.2:8000` untuk emulator Android debug. Jalankan
backend lebih dulu sebelum mencoba chat atau voice.

## Persiapan

Kebutuhan:

- JDK 17
- Android Studio, untuk Android SDK dan emulator
- Xcode (macOS), untuk aplikasi iOS

### Android

```bash
./gradlew :androidApp:assembleDebug     # build
./gradlew :androidApp:installDebug      # pasang ke emulator atau device
```

Emulator mengakses backend di komputer lewat `10.0.2.2`. Untuk device fisik,
buat `local.properties` di root (sudah di-ignore git) dan isi IP komputer di
jaringan Wi-Fi yang sama:

```properties
BACKEND_HOST=192.168.x.x
```

### iOS

Bangun XCFramework dari modul `shared` setelah setiap perubahan di `shared/`:

```bash
./gradlew :shared:assemblePipSharedDebugXCFramework
```

Lalu buka `iosApp/Pip.xcodeproj` di Xcode dan jalankan. Untuk build Release,
pakai `assemblePipSharedReleaseXCFramework` dan arahkan `path` di
`PipSharedPackage/Package.swift` ke folder `release`.

### Prototype web

Buka `index.html` langsung di browser. Panel kiri berisi Prototype Console
untuk memaksa layar masuk ke state `loading`, `error`, `empty`, atau `success`.

## Tes dan lint

```bash
./gradlew :shared:allTests     # tes modul shared (chat, ai, network, voice)
./gradlew ktlintCheck          # lint Kotlin
```

CI (`.github/workflows/ci.yml`) menjalankan build `shared` dan `androidApp`,
`ktlintCheck`, dan build XCFramework untuk iOS pada setiap push ke `main` dan
pull request yang menyentuh `shared/`, `androidApp/`, `PipSharedPackage/`,
atau `iosApp/`.

## Teknologi

Kotlin 2.0, Ktor 2.3 (jaringan), kotlinx.serialization, kotlinx.coroutines,
Koin (dependency injection), Jetpack Compose (Android), SwiftUI (iOS),
SKIE (jembatan Kotlin ke Swift), dan ktlint.

## Dokumen produk

PRD ada di `prd/`: migrasi KMP (`PRD-KMP-Migration-v2.md`), Voice Assistant,
Voice Pipeline Upgrade, Model Router Fallback, dan animasi avatar Pip.
