# Recon map: ReSA:MP (Android SA-MP / open.mp Mobile Client & Launcher)

Scope: Universal Multi-Server Launcher (Favorites, Internet, Settings, Cache Verifier) + In-Game Client with Chromium Embedded Framework (CEF) HTML5/JS Web Overlay. 100% Ad-Free.
For: SA-MP & open.mp Android mobile and PC emulator (LDPlayer) players wanting an ad-free, high-performance client with modern web HUDs and dialogs.
Date: 2026-11-09

## Sources

| # | source | URL / Reference | notes |
| --- | --- | --- | --- |
| 1 | ReSA:MP & Alyn Releases | Public mobile SA-MP launcher distributions (APK) | Decompiled manifest, layout hierarchies, UI structure, native library names |
| 2 | Architecture Specification | `https://share.gemini.google/L4KBy5gXhAGW` | RakNet Packet 215 protocol, JNI bridge, transparent WebView, Pawn include |
| 3 | open.mp / SA-MP Protocol Docs | `https://github.com/openmultiplayer/open.mp` | UDP query protocol opcode 'i' (Information), opcode 'r' (Rules), opcode 'c' (Clients) |
| 4 | Working ROMP Implementation | `D:\Valleta\ROMP` | Verified codebase running live on LDPlayer (30 tests passing, APK 4.12 MB) |

## Core loop

Browse or add SA-MP/open.mp servers, verify game data integrity, synchronize settings, and connect into GTA:SA with a hardware-accelerated CEF web overlay without ever seeing an advertisement.

## Screens

| ID | screen | route / how to reach | purpose | key components | states seen |
| --- | --- | --- | --- | --- | --- |
| S01 | Favorit Tab | App startup / Tap [FAVORIT] tab | View and manage saved servers | Server cards, status dot, ping, players count, delete button, connect button | empty, populated, querying, offline |
| S02 | Internet Tab | Tap [INTERNET] tab | Explore curated public server list | Public server cards, query stats, connect button | populated, querying, offline |
| S03 | Pengaturan Tab | Tap [PENGATURAN] tab | Player configuration and cache verification | Nickname EditText, FPS Limit Spinner (30/60/90/120), Cache status card, Save button | valid, invalid nickname error, cache complete, cache missing |
| S04 | Tambah Server Modal | Tap [+ Tambah Server] button | Add custom SA-MP server to Favorites | Input Name (optional), Input IP, Input Port, Cancel & Simpan buttons | empty form, validation error, saved |
| S05 | In-Game CEF HUD Overlay | Tap [CONNECT] -> GTA SA launches | Realtime in-game HUD | HP bar, Armor bar, Cash counter ($), Vehicle Speedometer (KM/H) | hidden, active, updating |
| S06 | In-Game CEF Dialog / GUI | Triggered by server via Packet 215 | Interactive server Web menus, login/register, inventory, spawn selector | Modal window, form inputs, action buttons, close button | hidden, visible with input focus |

Reference screenshots captured live from LDPlayer are stored in `replica/screens/`:
- `S01_favorites.png`
- `S02_internet.png`
- `S03_settings.png`
- `S04_add_dialog.png`
- `S05_in_game_cef.png`

## Flows

```
F01 Add Custom Server and Connect
    S01 -> S04 (Input IP:Port) -> Tap Simpan -> S01 (Card appears with live ping) -> Tap CONNECT -> S05 (GTA SA launches with CEF HUD)
    happy path clicks: 5
    edge: Invalid IP, invalid port (>65535), server offline (red status dot), missing game cache warning

F02 Browse Public Internet Directory
    S01 -> S02 -> Scroll server list -> Tap CONNECT -> S05
    happy path clicks: 2
    edge: Server full, high ping, server offline

F03 Configure Player Profile & Check Cache
    S01 -> S03 -> Edit Nickname -> Select FPS Limit -> Check Cache Status -> Tap SIMPAN
    happy path clicks: 4
    edge: Invalid nickname (<3 or >20 chars, invalid symbols), missing texdb/gta3.img or data/gta.dat

F04 In-Game CEF Interactive Dialog Handshake
    S05 -> Server sends Packet 215 Sub-ID 1 (LOAD_URL) -> Sub-ID 3 (TOGGLE_FOCUS on) -> S06 displays modal -> Player interacts and submits form -> Sub-ID 4 (EMIT_SERVER) -> Sub-ID 3 (TOGGLE_FOCUS off) -> S05
    happy path clicks: 1 to 3
    edge: Soft keyboard opens (prevented from crashing by adjustNothing), URL load timeout, invalid JSON payload
```

## Components

| component | variants | states | used on |
| --- | --- | --- | --- |
| ServerCard | favorite (with delete), public (no delete) | default, querying, online, offline | S01, S02 |
| TabButton | active (`#38BDF8`), inactive (`#94A3B8`) | normal, selected | S01, S02, S03 |
| StatusDot | online (`#10B981`), offline (`#EF4444`), pending (`#94A3B8`) | static | S01, S02 |
| ActionButton | primary blue (`#0284C7`), danger dark (`#334155`), text red | normal, pressed, disabled | S01, S02, S03, S04 |
| FormEditText | single-line text, number input | focused, unfocused, error | S03, S04 |
| FpsSpinner | dropdown (30, 60, 90, 120 FPS) | collapsed, expanded | S03 |
| CacheStatusCard | complete (green), missing (amber) | static text | S03 |
| CefHud | transparent overlay (HP, Armor, Cash, Speedometer) | transparent, updating | S05 |
| CefDialog | centered modal box, form fields, action buttons | hidden, visible, focused | S06 |

## Inferred data model

```
ServerItem
    id: String (UUID)
    ip: String (IPv4 or hostname)
    port: int (1..65535)
    name: String
    isFavorite: boolean
    evidence: S01 cards, S04 dialog, SharedPreferences JSON storage
    confidence: high

SettingsConfig
    nickname: String (3..20 characters, regex [a-zA-Z0-9_\[\]\$\@\.\=\?]+)
    fpsLimit: int (30, 60, 90, 120)
    fastChat: boolean
    cefOverlay: boolean
    evidence: S03 view, SAMP/settings.ini format
    confidence: high

ServerQueryInfo
    online: boolean
    ip: String
    port: int
    hostname: String
    gamemode: String
    players: int
    maxPlayers: int
    pingMs: int
    passworded: boolean
    evidence: UDP socket response opcode 'i' (11-byte header + data)
    confidence: high

CacheStatus
    complete: boolean
    missingFiles: List<String> ("texdb/gta3.img", "data/gta.dat", "texdb/gta_int.img")
    baseDir: File
    evidence: S03 status view, GTA SA external files directory
    confidence: high

CefPacket (RakNet Custom Packet 215)
    packetId: uint8_t (215)
    subId: uint8_t (1: LOAD_URL, 2: EMIT_CLIENT, 3: TOGGLE_FOCUS, 4: EMIT_SERVER, 5: SET_VISIBILITY)
    payload: binary/string (URL string, event name, JSON arguments, boolean flags)
    evidence: cef_mobile.inc, cef_net.cpp, CefManager.java
    confidence: high
```

Relationships:
- `ServerItem (1) -> (1) ServerQueryInfo` (resolved dynamically via UDP)
- `SettingsConfig (1) + ServerItem (1) -> (1) settings.ini` (synced on Connect)
- `Server (Pawn) -> RakNet Packet 215 -> (1) CefManager -> (1) CefDialog / CefHud`

## Feature matrix

See `replica/features.csv`.
- Parity Score: **87.0 / 100**
- Must: 15 / 15 (100% complete)
- Should: 1 / 4 (Search filter, Detailed inspect sheet, and Cache zip downloader pending)
- Could: 0 / 1 (Voice chat toggle)
- Skip: 3 (Banner ads, interstitial ads, tracking SDKs - 100% ad-free clone)

## Out of scope (cannot or should not be cloned)

- **In-app advertisements:** Banners, interstitial popups, reward video ads from AdMob/Unity Ads in original ReSA/Alyn are deliberately excluded.
- **Closed third-party analytics / telemetry trackers:** Stripped for privacy and zero startup delay.
- **Server-side private gamemode scripts:** Only client-side interfaces and open protocol specifications are cloned.

## Size

- Screens: 6
- Flows: 4
- Entities: 5
- Hard parts: RakNet Packet 215 bitstream byte alignment, EGL driver soft-keyboard window resize crash handling, dynamic touch pass-through between Android GL view and WebView overlay.
- Size: **M** (Core architecture & MVP operational on LDPlayer; next steps focus on search filtering, server details dialog, and cache zip extractor).

