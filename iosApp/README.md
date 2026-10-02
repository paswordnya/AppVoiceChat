# Pip — Voice & Chat AI Assistant

Pip adalah aplikasi asisten AI berbasis suara dan chat, terdiri dari app SwiftUI (iOS/macOS/visionOS) dan backend FastAPI (Python). Secara default Pip mengobrol dalam Bahasa Indonesia, tapi otomatis menyesuaikan ke bahasa yang dipakai user.

Mascot Pip berbentuk "blob" organik dengan wajah sederhana (mata & senyum), dan punya beberapa kepribadian/"buddy" yang bisa dipilih: **Pip, Bloop, Sunny, Coco** — masing-masing pakai sample suara yang sama (`voice_app.mp3`) dengan pitch/rate berbeda.

> Referensi produk: `../prd/PRD-Voice-Assistant.md.pdf` (di luar repo ini). Ada juga `../ARCHITECTURE.md` yang mendeskripsikan arsitektur KMP/MVVM yang lebih ambisius — itu **dokumen aspirasional untuk prototype `index.html`**, bukan gambaran implementasi Swift/Python saat ini.

## Struktur Repo

```
Pip/
├── Pip/                    # Source app SwiftUI
│   ├── PipApp.swift        # @main entry point
│   ├── ContentView.swift   # Router: Splash → Onboarding → Main
│   ├── Core/                # Networking, model bersama, design system
│   ├── Features/            # Layar per fitur (Splash, Onboarding, Main, Posts)
│   ├── Models/               # Model data (Post — demo/boilerplate)
│   ├── Repositories/         # Base class akses data
│   ├── ViewModels/            # Base class MVVM
│   └── Resources/             # Asset bundel (voice_app.mp3)
├── Pip.xcodeproj/           # Project Xcode
├── PipTests/, PipUITests/  # Scaffold XCTest (belum banyak diisi)
├── backend/                 # Backend FastAPI (Python)
├── voice/                    # Sample suara master untuk voice cloning
└── moc/                       # Mockup desain HTML/JS/React (referensi visual/UX)
```

## Alur Aplikasi (iOS/macOS)

`ContentView.swift` adalah state machine 3 tahap:

1. **`SplashView`** — splash gradient bermerek dengan mascot "bernapas", otomatis lanjut setelah ±2.7 detik (atau tap).
2. **`OnboardingView`** — intro "Hi, I'm Pip" dengan tombol Skip/Continue, keduanya langsung ke Main (langkah pemilihan buddy/izin mic/auth di moc belum diimplementasi).
3. **`MainView`** — layar utama; saat ini langsung menampilkan chat penuh layar (`KeyboardSessionView`), dengan `VoiceSessionView` bisa diakses dari tombol mic di chat.

Dua mode percakapan sama-sama terhubung ke satu backend FastAPI dan berbagi satu `AppSession.id` (UUID yang disimpan di `UserDefaults`), jadi riwayat chat dan voice tetap terpadu:

- **Chat** — `KeyboardSessionView` + `ChatSocketClient` via `WS /ws/chat/{id}`. UI bubble ala Telegram, gesture swipe untuk reply, separator tanggal, status streaming "thinking → typing → done", dan restore riwayat saat dibuka lagi.
- **Voice** — `VoiceSessionView` + `VoiceSocketClient` via `WS /ws/voice/{id}`. Streaming mic full-duplex, blob bereaksi sesuai fase listening/thinking/done, mode tap-to-barge-in atau push-to-talk tergantung listening mode.

Backend juga mendukung slash-command (`/help /start /clear /model /voice /listen /status`) yang ditangani tanpa memanggil LLM, dukungan multi-model LLM dengan fallback otomatis (Gemini → Groq → OpenRouter via LiteLLM), dan dua engine voice berbeda (Gemini Live full-duplex vs. pipeline custom VAD+STT+TTS dengan voice cloning).

## Modul Swift

### `Core/` — infrastruktur bersama

| File | Fungsi |
|---|---|
| `Networking/AppEnvironment.swift` | Resolve base URL backend (dev/prod) + turunan `ws://` |
| `Networking/APIClient.swift` | HTTP client singleton berbasis Alamofire |
| `Networking/APIEndpoint.swift` | Protocol untuk tiap endpoint REST (path/method/params/headers) |
| `Networking/APIResponse.swift` | Envelope generik `{success, message, data}` |
| `Networking/APIError.swift` | Enum error bertipe (invalidURL, noConnectivity, decoding, dll) |
| `Networking/AppSession.swift` | UUID sesi persisten per install (dipakai chat & voice) |
| `Networking/AuthTokenStore.swift` | Penyimpanan bearer-token (belum terhubung ke flow login) |
| `Networking/ChatHistoryEndpoint.swift` | `GET /api/session/{id}/history` |
| `Networking/ChatSocketClient.swift` | Client WebSocket untuk `/ws/chat/{id}` |
| `Networking/ListeningModeEndpoint.swift` | `PUT /api/session/{id}/listen` |
| `Networking/VoiceSocketClient.swift` | Client WebSocket untuk `/ws/voice/{id}` — capture mic via `AVAudioEngine` (16kHz PCM16), playback (24kHz float32), push-to-talk, barge-in |
| `Models/Buddy.swift` | Model kepribadian mascot (nama/gradient/pitch/rate) |
| `Models/BuddyStore.swift` | Singleton `ObservableObject` buddy yang aktif |
| `DesignSystem/PipTheme.swift` | Token warna & font brand |
| `DesignSystem/BlobShape.swift` | Custom `Shape` bentuk "blob" organik |
| `DesignSystem/PipBlobFace.swift` | Gambar mata & senyum mascot di atas `BlobShape` |

### `Features/` — modul per layar

- `Splash/SplashView.swift`, `Onboarding/OnboardingView.swift` — lihat alur di atas.
- `Main/MainView.swift`, `Main/VoiceSessionView.swift`, `Main/KeyboardSessionView.swift` — layar inti chat & voice.
- `Posts/` — **fitur demo/contoh** (boilerplate pola MVVM+Repository pakai endpoint ala JSONPlaceholder), bukan bagian produk Pip yang sesungguhnya.

### `Models/`, `Repositories/`, `ViewModels/`

- `Models/Post.swift` — model demo untuk fitur Posts.
- `Repositories/Repository.swift` — protocol `Repository` + `BaseRepository` yang di-subclass repository fitur.
- `ViewModels/BaseViewModel.swift` — base class `@MainActor ObservableObject` dengan state loading/error bersama.

## Backend (`backend/`)

Python (FastAPI + Uvicorn), dikelola dengan `uv`, pakai **LiteLLM** sebagai abstraksi LLM dan **aiosqlite** untuk persistensi. Dokumentasi setup & run lengkap ada di [`backend/README.md`](backend/README.md).

Ringkasan modul:

- `app/main.py` — app factory, registrasi router, CORS, health check.
- `app/api/` — endpoint REST: chat, models, voices, listening mode, status.
- `app/api/websocket_chat.py`, `websocket_voice.py` — endpoint WebSocket streaming.
- `app/core/command_router.py` — penanganan slash-command tanpa LLM.
- `app/core/memory.py` — ekstraksi memori jangka panjang (ringkasan fakta persisten via LLM).
- `app/core/session_store.py` — persistensi sesi/pesan/memori (aiosqlite).
- `app/llm/models.py`, `provider.py` — registry model & fallback chain (Gemini → Groq → OpenRouter).
- `app/voice/mode_a_gemini_live.py` — **Mode A**: full-duplex via Gemini Live API.
- `app/voice/mode_b_pipeline.py` — **Mode B**: pipeline custom VAD → STT → LLM → TTS dengan voice cloning.
- `app/voice/vad.py` — deteksi giliran bicara (Silero VAD).
- `app/voice/stt.py` — speech-to-text (faster-whisper).
- `app/voice/tts.py` — text-to-speech voice-cloned (Coqui XTTS v2).
- `app/voice/speaker_verification.py` — verifikasi pembicara / "voice lock" (ECAPA-TDNN/SpeechBrain).
- `app/voice/voice_registry.py` — daftar suara yang bisa di-clone, di-scan dari folder `voice/`.

## `voice/`

Berisi `voice_app.mp3` — sample suara master untuk voice cloning. Setiap file audio yang ditaruh di sini otomatis jadi opsi suara yang bisa dipilih. Dipakai oleh Mode B (TTS + speaker verification) dan oleh app iOS (`Buddy.swift`) untuk memberi variasi pitch/rate per buddy dari satu clip yang sama.

Model ML sebenarnya (Whisper, XTTS, Silero VAD, ECAPA) tersimpan/ter-download di `backend/data/` saat pertama kali dijalankan.

## `moc/`

Singkatan dari **mockup** — prototype desain HTML/JS/React interaktif yang jadi acuan visual/UX sebelum diimplementasi di SwiftUI (banyak komentar di kode Swift merujuk "mirrors the moc's..."). Bukan bagian dari app/backend yang dijalankan — murni referensi desain.

## Setup Cepat

**Backend:**
```bash
cd backend
uv venv --python 3.12 .venv && source .venv/bin/activate
uv pip install -r requirements.txt          # chat only
uv pip install -r requirements-voice.txt    # + voice (Mode A & B)
cp .env.example .env                        # isi GEMINI_API_KEY, dst.
./run.sh                                     # atau ./pipctl.sh start
```
Detail lengkap (termasuk dependency ffmpeg, key API, dan status fitur per fase): lihat [`backend/README.md`](backend/README.md).

**iOS App:**
1. Buka `Pip.xcodeproj` di Xcode.
2. Pastikan backend jalan di `http://localhost:8000` (default di `Core/Networking/AppEnvironment.swift`) — untuk device fisik, ganti ke IP LAN laptop atau pakai Tailscale.
3. Build & run target Pip (dependency SwiftPM: **Alamofire**, resolve otomatis oleh Xcode).

## Catatan

- Belum ada `README.md` khusus untuk sisi iOS sebelum dokumen ini; sekarang jadi entry point utama untuk memahami repo secara keseluruhan.
- Fitur `Posts/` di `Features/` adalah kode contoh/boilerplate pola MVVM+Repository, bukan bagian dari produk Pip.
- Beberapa langkah onboarding (pemilihan buddy, izin mic, auth) sudah ada strukturnya di kode tapi belum diaktifkan di alur utama.
