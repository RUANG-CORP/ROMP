# Design Document: Universal Ad-Free SA-MP & open.mp Mobile Launcher & Client

**Date:** 2026-11-08  
**Architecture:** Universal Multi-Server Launcher (Like Alyn/ReSA Mobile, 100% Ad-Free)  
**Target Platform:** Android (`armeabi-v7a`, `arm64-v8a`), SA-MP 0.3.7 / open.mp (OMP)  

---

## 1. Intent & Purpose

The user selected the **Universal Launcher** architecture (mirroring Alyn SA-MP and ReSA SA-MP, but completely clean of third-party advertisements).

**Core Experience:**
- Players can browse public servers or add their own custom servers (IP & Port).
- Players can save favorite servers, view real-time player counts and ping.
- 1-click connect to any server, automatically generating `settings.ini` and launching the game with the integrated CEF overlay.
- Zero advertisements, zero trackers, ultra-fast launch.

---

## 2. Universal Launcher Structure

```
+-----------------------------------------------------------------------------------------+
|                        UNIVERSAL SA-MP MOBILE LAUNCHER                                  |
+-----------------------------------------------------------------------------------------+
| Navigation: [ FAVORIT ]              [ INTERNET ]             [ PENGATURAN ]            |
+-----------------------------------------------------------------------------------------+
| Tab 1: FAVORIT                                                                          |
|   - Floating Action Button (+) -> Dialog "Tambah Server" (IP, Port, Nama Server)        |
|   - RecyclerView: List Server Favorit                                                   |
|       * Status Dot (Hijau = Online, Merah = Offline)                                    |
|       * Nama Server & IP:Port                                                           |
|       * Live Ping (ms) & Players (e.g. 45/100)                                          |
|       * Tombol "CONNECT" -> Launch game ke server ini                                   |
+-----------------------------------------------------------------------------------------+
| Tab 2: INTERNET (Server Publik / Rekomendasi)                                           |
|   - Daftar curated server roleplay populer                                              |
|   - Filter berdasarkan bahasa / mode                                                    |
|   - Tombol "+ Favorit" dan "CONNECT"                                                    |
+-----------------------------------------------------------------------------------------+
| Tab 3: PENGATURAN (Settings)                                                            |
|   - Input Nickname Pemain (Validasi otomatis karakter [a-zA-Z0-9_])                     |
|   - FPS Lock: 30 FPS / 60 FPS / 90 FPS / 120 FPS                                        |
|   - Fast Chat & Voice Chat Toggles                                                      |
|   - Status Cache GTA SA (Deteksi texdb/, audio/, data/)                                 |
+-----------------------------------------------------------------------------------------+
```

---

## 3. Data Storage & Query Architecture

1. **`ServerRepository.java`:**
   * Manages server list stored in Android `SharedPreferences` as JSON array.
   * Default includes: Localhost (`127.0.0.1:7777`) and popular community servers.
   * Methods: `getFavorites()`, `addServer(ip, port, name)`, `removeServer(id)`.
2. **`SampQueryClient.java`:**
   * Non-blocking UDP socket querying.
   * Concurrently queries servers in list and updates UI via callback.
3. **`SettingsManager.java`:**
   * Nickname, FPS, and fast-chat configuration.
   * Synchronizes to `/storage/emulated/0/Android/data/com.rockstargames.gtasa/files/SAMP/settings.ini`.
4. **`CacheVerifier.java`:**
   * Validates `texdb/gta3.img`, `data/gta.dat`, `audio/SFX/` before launching.
