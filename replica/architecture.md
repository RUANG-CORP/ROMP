# Architecture: ROMP / Valleta (Clean-Room Rebuild of ReSA:MP & Alyn)

Scope: Universal SA-MP / open.mp Mobile Launcher & In-Game Chromium Embedded Framework (CEF) Client + In-App Game Data Cache Downloader & Extractor. 100% Ad-Free.

## Stack

| layer | choice | why |
| --- | --- | --- |
| Mobile Platform | Android Native (Java 17 + Android SDK 34) | Maximum compatibility with GTA SA native engine, minimal overhead, ~4MB APK |
| Native Game Hook | C++20 (NDK 25.1.8937393 + CMake 3.22.1) | Dual ABI (`armeabi-v7a` & `arm64-v8a`) JNI bridge with bounds protection |
| In-Game Web Engine | Hardware-Accelerated Transparent Android WebView | Replaces heavyweight desktop CEF with platform-native Chromium engine |
| UI Framework | Android Native Views (Activity + ListView + Dialogs) | Zero theme mismatch crashes with GTA SA EGL surface, instant boot, no bloat |
| Local Storage | SharedPreferences (JSON) + Native INI (`/files/SAMP/settings.ini`) | Simple, zero-migration persistence with direct native game sync |
| Query Protocol | UDP DatagramSocket (`opcode 'i', 'r', 'c'`) | Non-blocking thread-pool SA-MP/open.mp server query protocol |
| Cache Downloader | Android `DownloadManager` + Background Chunked Stream | Built-in resume, network-drop tolerance, and notification bar support |
| Zip Extraction | Streaming `java.util.zip.ZipInputStream` (Buffer 32KB) | Memory-efficient streaming unzipping directly into `/files/` without OOM |
| Server-Side Bridge | Pawn Include (`cef_mobile.inc`) + RakNet Packet 215 | Standardized multi-platform client-server event communication |

## Schema & Data Models

Storage: Local Android Persistence (SharedPreferences JSON + Native `settings.ini`).
SQL definition provided in `replica/schema.sql` for relational or Room migration if desired.

### Core Entities:

1. **`ServerItem`**
   - `id`: UUID (String)
   - `ip`: String (Host or IPv4)
   - `port`: int (1..65535, default 7777)
   - `name`: String
   - `is_favorite`: boolean
   - `added_at`: timestamp

2. **`SettingsConfig`**
   - `player_nickname`: String (3..20 chars, regex: `^[a-zA-Z0-9_\[\]\$\@\.\=\?]+$`)
   - `fps_limit`: int (30, 60, 90, 120)
   - `fast_chat`: boolean (default true)
   - `cef_overlay`: boolean (default true)
   - `voice_chat`: boolean (default false)

3. **`DownloadTask`**
   - `download_id`: long (Android DownloadManager reference ID)
   - `source_url`: String (CDN URL for GTA SA cache zip)
   - `destination_path`: File (`Android/data/com.rockstargames.gtasa/files`)
   - `status`: enum (`IDLE`, `DOWNLOADING`, `EXTRACTING`, `VERIFYING`, `COMPLETED`, `FAILED`)
   - `progress_percent`: int (0..100)
   - `total_bytes`: long
   - `downloaded_bytes`: long
   - `error_message`: String

4. **`ServerQueryInfo`**
   - `ip`: String, `port`: int
   - `online`: boolean
   - `hostname`: String, `gamemode`: String
   - `players`: int, `max_players`: int
   - `ping_ms`: int, `passworded`: boolean
   - `rules`: Map<String, String> (lagcomp, mapname, version, weather)

---

## API & Protocols

### 1. In-App Game Data Cache Download API

| method path | does | who | input | output | flow |
| --- | --- | --- | --- | --- | --- |
| `GET {cdn_url}/gta_sa_cache.zip` | Downloads full or lite GTA SA game data archive | CacheDownloader | `Range` header (if resuming) | Binary Zip Stream | F05: Cache Download |
| `HEAD {cdn_url}/gta_sa_cache.zip` | Probes file size & resume support | CacheDownloader | none | `Content-Length`, `Accept-Ranges` | F05: Preflight |

### 2. SA-MP / open.mp UDP Query Protocol (Client to Game Server)

| Opcode | Packet Structure | Response | Usage |
| --- | --- | --- | --- |
| `'i'` (Info) | `SAMP` (4B) + IP (4B) + Port (2B) + `'i'` (1B) | Hostname, Gamemode, Players, MaxPlayers, Passworded | Live stats on server cards |
| `'r'` (Rules) | `SAMP` (4B) + IP (4B) + Port (2B) + `'r'` (1B) | Rule Count + Rule pairs (`lagcomp`, `mapname`, `weburl`, etc.) | Detailed inspect modal |
| `'c'` (Clients) | `SAMP` (4B) + IP (4B) + Port (2B) + `'c'` (1B) | Player count + Player records (Name, Score) | Server detail player list |

### 3. RakNet Custom Packet 215 (Server to Client & Vice-Versa)

| Sub-ID | Identifier | Direction | Payload | Action |
| --- | --- | --- | --- | --- |
| `1` | `LOAD_URL` | Server -> Client | `uint16_t urlLen` + `char[] url` | WebView loads URL or SPA path |
| `2` | `EMIT_CLIENT` | Server -> Client | `uint16_t nameLen` + `char[] name` + `uint32_t argsLen` + `char[] args` | Injects `window.cef.emit(name, ...args)` into WebView |
| `3` | `TOGGLE_FOCUS`| Server -> Client | `uint8_t enable` | Toggles touch input focus between game and WebView |
| `4` | `EMIT_SERVER` | Client -> Server | `uint16_t nameLen` + `char[] name` + `uint32_t argsLen` + `char[] args` | Javascript `window.cef.emitServer` sends packet to Pawn |
| `5` | `SET_VISIBILITY`| Server -> Client| `uint8_t visible` | Hides or shows the WebView overlay |

---

## The Parts That Bite (Edge Cases & Risks)

- **Android 11+ Scoped Storage (`Android/data`):**
  Android 11 (API 30+) restricts general file managers from reading/writing `Android/data`. However, an app ALWAYS has full direct read/write access to its own package directory: `context.getExternalFilesDir(null)` which maps to `/storage/emulated/0/Android/data/com.rockstargames.gtasa/files`.
  By targeting its own package folder, the Cache Downloader can write and extract all `texdb`, `data`, and `audio` assets directly without requiring root or SAF permission prompts!
- **Large Zip Unpacking & Memory OOM:**
  GTA SA audio and texture archives (`gta3.img`) can exceed 1 GB. Reading entire zip entries into RAM will immediately throw `OutOfMemoryError`. Unpacking MUST use fixed-buffer streaming (e.g. 32KB buffer chunks) from `ZipInputStream` directly into `FileOutputStream`.
- **Soft Keyboard & EGL Surface Crash:**
  When soft keyboard pops up in CEF input fields, Android defaults to resizing the window, destroying the EGL surface of GTA SA. Solution: `android:windowSoftInputMode="adjustNothing"` in `AndroidManifest.xml` (already implemented and verified).
- **Download Resumption & Partial Files:**
  If network disconnects midway, partially written files could corrupt the game cache. The downloader will download to a temporary `.tmp` archive, verify CRC/size, extract to destination, and then mark cache as verified.

---

## Build Order

1. **Milestone 1: Core Vertical Slice (COMPLETED - 87.0% Parity):**
   - Universal Launcher: S01 (Favorit), S02 (Internet), S03 (Pengaturan), S04 (Tambah Server).
   - In-Game Client: S05 (CEF HUD Overlay), RakNet Packet 215, settings.ini synchronization.
   - Verified live on LDPlayer emulator.
2. **Milestone 2: In-App Game Data Cache Downloader & Extractor (Current Focus):**
   - `CacheDownloader.java`: Asynchronous HTTP chunked downloader supporting CDN source, resume, and real-time progress callbacks.
   - `ZipExtractor.java`: Low-memory streaming unzipper extracting `texdb/`, `data/`, and `audio/` into app storage.
   - UI Integration: "Download Game Data" button & progress bar inside S03 (Pengaturan) tab and prompt dialog if cache is missing.
3. **Milestone 3: Server Detailed Inspect Sheet:**
   - UDP opcode `'r'` (Rules) & opcode `'c'` (Players list) client integration.
   - Bottom Sheet / Modal dialog showing gamemode, map, weather, online players list.
4. **Milestone 4: Realtime Server Search & Filtering:**
   - Search bar on S01 & S02 with dynamic text filter and ping threshold.

