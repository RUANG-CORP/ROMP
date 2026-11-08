# Universal Ad-Free SA-MP & open.mp Mobile Launcher & Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete, clean, 100% ad-free Universal SA-MP & open.mp Mobile Launcher and Game Client featuring Favorites / Internet server tabs, Add Server dialog, real-time UDP querying, settings management (`settings.ini`), cache verification, and seamless launch into the CEF-overlay game client.

**Architecture:** A multi-tab Material Design 3 Launcher (`LauncherActivity`) hosting:
1. **Tab Favorit:** RecyclerView list of saved custom servers with live ping & player counts, plus an Add Server dialog.
2. **Tab Internet:** Curated directory of public roleplay servers with 1-click connect.
3. **Tab Pengaturan:** Player nickname, FPS Limiter (30/60/90/120 FPS), and GTA:SA cache validation.
Connecting to any server automatically updates native `/files/SAMP/settings.ini` and launches `GTASA` with the CEF overlay.

**Tech Stack:** Java 17 (Android SDK 34), Material Components & ViewPager2 / TabLayout, UDP Sockets (`DatagramSocket`), C++17 (NDK 25.1), Android WebView.

**Spec:** `docs/superpowers/specs/2026-11-08-adfree-samp-launcher-client-design.md`

## Global Constraints

- 100% Ad-Free: Zero advertising SDKs (no AdMob, Unity Ads, StartApp, IronSource, or tracking bloatware).
- Multi-Server Support: Ability to add, query, and connect to arbitrary IP:Port servers.
- SA-MP UDP Query Protocol: Header `SAMP` (`0x53, 0x41, 0x4D, 0x50`) followed by 4-byte IP, 2-byte port, and opcode (`'i'` info, `'p'` ping).
- Native Settings Path: Synchronizes configuration directly to `/storage/emulated/0/Android/data/com.rockstargames.gtasa/files/SAMP/settings.ini`.
- Dual ABI Compatibility: Retains `arm64-v8a` and `armeabi-v7a` compilation without breaking native JNI linkages.

## Review Focus

1. **UDP Query Timeout / Network Hanging:** If target server is offline or firewall blocks UDP, query sockets must enforce a strict 2-second timeout and fail gracefully without blocking the UI main thread.
2. **Duplicate or Malformed Server Entry:** Adding a server with invalid IP or out-of-range port (not 1-65535) must be caught with friendly input validation.
3. **Missing Game Data Force Close:** If player taps "CONNECT" before GTA:SA `texdb/` or `audio/` data exists, launcher must block launch and display clean guidance rather than allowing native SIGSEGV crash.
4. **Invalid Nickname Format:** Nicknames containing spaces or forbidden characters (`[a-zA-Z0-9_]`, max 20 chars) must be validated before writing to `settings.ini`.
5. **Dual Activity Lifecycle Transition:** Transitioning from `LauncherActivity` to `GTASA` must clean up launcher resources while ensuring CEF overlay initializes cleanly.

---

### Task 1: Real-Time SA-MP UDP Query Client (`SampQueryClient`)

**Files:**
- Create: `app/src/main/java/com/rockstargames/gtasa/launcher/net/ServerInfo.java`
- Create: `app/src/main/java/com/rockstargames/gtasa/launcher/net/SampQueryClient.java`
- Test: `tests/test_samp_query.py`

**Interfaces:**
- Consumes: Target IP string, Port integer, timeout integer.
- Produces:
  - `SampQueryClient.queryServer(String ip, int port, QueryCallback callback)`
  - `ServerInfo` model with `online`, `pingMs`, `hostname`, `gamemode`, `players`, `maxPlayers`, `passworded`.

- [ ] **Step 1: Write failing protocol verification test**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Implement `ServerInfo.java` and `SampQueryClient.java`**
- [ ] **Step 4: Run test to verify it passes**
- [ ] **Step 5: Commit**

---

### Task 2: Server Repository & Settings Manager (`ServerRepository`, `SettingsManager`)

**Files:**
- Create: `app/src/main/java/com/rockstargames/gtasa/launcher/data/ServerItem.java`
- Create: `app/src/main/java/com/rockstargames/gtasa/launcher/data/ServerRepository.java`
- Create: `app/src/main/java/com/rockstargames/gtasa/launcher/data/SettingsManager.java`
- Test: `tests/test_settings_and_servers.py`

**Interfaces:**
- Consumes: Android `Context`, `SharedPreferences`.
- Produces:
  - `ServerRepository.getFavorites()`, `addFavorite(ServerItem item)`, `deleteFavorite(String id)`, `getPublicServers()`.
  - `SettingsManager.getNickname()`, `setNickname(String name)`, `getFpsLimit()`, `setFpsLimit(int fps)`, `syncToNativeSettings(ServerItem server, File externalFilesDir)`.

- [ ] **Step 1: Write failing repository & settings unit test**
- [ ] **Step 2: Run test to verify it fails**
- [ ] **Step 3: Implement `ServerItem.java`, `ServerRepository.java`, and `SettingsManager.java`**
- [ ] **Step 4: Run test to verify it passes**
- [ ] **Step 5: Commit**

---

### Task 3: Game Storage & Cache Verifier (`CacheVerifier`)

**Files:**
- Create: `app/src/main/java/com/rockstargames/gtasa/launcher/data/CacheVerifier.java`
- Test: `tests/test_cache_verifier.py`

**Interfaces:**
- Consumes: Android `Context`.
- Produces:
  - `CacheVerifier.checkGameData(Context context)` -> `CacheStatus`
  - Validates `texdb/gta3.img`, `data/gta.dat`, `audio/SFX/` to prevent force closes.

- [x] **Step 1: Write failing cache validator test**
- [x] **Step 2: Run test to verify it fails**
- [x] **Step 3: Implement `CacheVerifier.java`**
- [x] **Step 4: Run test to verify it passes**
- [x] **Step 5: Commit**

---

### Task 4: Universal Launcher UI with Tabs (`LauncherActivity`, Adapters & Layouts)

**Files:**
- Create: `app/src/main/java/com/rockstargames/gtasa/launcher/ui/ServerAdapter.java`
- Create: `app/src/main/java/com/rockstargames/gtasa/launcher/ui/LauncherActivity.java`
- Create: `app/src/main/res/layout/activity_launcher.xml`
- Create: `app/src/main/res/layout/item_server_card.xml`
- Create: `app/src/main/res/layout/dialog_add_server.xml`
- Test: `tests/test_universal_launcher_ui.py`

**Interfaces:**
- Consumes: `ServerRepository`, `SampQueryClient`, `SettingsManager`, `CacheVerifier`.
- Produces:
  - Tabbed UI: [FAVORIT] | [INTERNET] | [PENGATURAN].
  - Button (+) to add custom servers.
  - Live query updates on server cards (status badge, online count, ping).
  - Server card with "CONNECT" button that launches `GTASA`.

- [x] **Step 1: Write failing UI contract test**
- [x] **Step 2: Run test to verify it fails**
- [x] **Step 3: Implement layouts, adapter, and `LauncherActivity.java`**
- [x] **Step 4: Run test to verify it passes**
- [x] **Step 5: Commit**

---

### Task 5: End-to-End Pipeline Integration, APK Assembly & LDPlayer Launch

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (configure `LauncherActivity` as primary launcher)
- Test: `tests/test_universal_e2e.py`

**Interfaces:**
- Consumes: Tasks 1 through 4.
- Produces:
  - Verified, compiled APK containing the complete Universal Launcher.
  - Direct installation and live verification on LDPlayer.

- [x] **Step 1: Write failing e2e test**
- [x] **Step 2: Run test to verify it fails**
- [x] **Step 3: Update `AndroidManifest.xml`, compile with `gradlew assembleDebug`, install to LDPlayer**
- [x] **Step 4: Run test to verify it passes**
- [x] **Step 5: Commit and verify on LDPlayer screen**
