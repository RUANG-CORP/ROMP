# Design Document: SA-MP & open.mp (OMP) Android CEF Client Integration (Alyn Base)

**Date:** 2026-11-08  
**Status:** Approved / Finalized Specification  
**Target Platform:** Android (`armeabi-v7a`, `arm64-v8a`), SA-MP 0.3.7 / open.mp (OMP) Server  

---

## 1. Intent & The Why

In the GTA SA-MP multiplayer ecosystem, server developers use CEF (Chromium Embedded Framework) on PC to create modern, responsive user interfaces (HUD, inventory, login, interactive dialogs) using HTML, CSS, and modern JavaScript frameworks (React, Vue, Tailwind).

On Android mobile devices:
- Desktop `libcef.so` cannot be run directly due to binary incompatibility, massive binary size (>150MB), high RAM overhead, and battery drain.
- Standard mobile clients use rigid native Canvas/OpenGL textdraws, creating a fragmented player experience in crossplay servers.

**Goal:** Implement a lightweight, high-performance CEF subsystem for the Android SA-MP client (based on Alyn SA-MP Mobile v2.10) using Android's hardware-accelerated system WebView. The WebView overlays the game's `GLSurfaceView`, bridges to native C++ (`libsamp.so`) via JNI, and communicates with SA-MP 0.3.7 / open.mp servers over existing RakNet connections via Custom Packet ID `215`. This achieves API compatibility with desktop `samp-cef` (`window.cef.emit` / `window.cef.on`) without opening external TCP/WebSocket firewall ports.

---

## 2. Goals, Non-Goals, & Anti-Goals

### Goals
- **Full 64-bit & 32-bit Support:** Compiles for both `armeabi-v7a` and `arm64-v8a` targeting GTA SA v2.10 to ensure full compatibility with Android 14+ devices.
- **Single Persistent SPA Architecture:** The WebView acts as a persistent Single-Page Application (SPA) loaded once at connection. UI components (HUD, dialogs, speedometer, inventory) are toggled via lightweight JS events rather than destructive full-page reloads.
- **Dynamic Touch Routing & Game Input Lock:**
  - *Passive Mode (HUD):* Touch events pass directly through to `GLSurfaceView` (`setClickable(false)`), allowing full use of analog sticks, camera swipes, and buttons.
  - *Interactive Mode (Dialogs/Menus):* Touch events are captured by the WebView (`setClickable(true)`), and native in-game touch/camera actions are suppressed in C++ to prevent accidental shooting or moving during menu interaction.
- **RakNet Native Networking:** Synchronized communication over the existing game connection using Packet ID `215` and BitStream sub-packets for SA-MP 0.3.7 and open.mp (OMP).
- **Variadic Desktop CEF Compatibility:** The injected `window.cef.emit(name, ...args)` supports variadic arguments (strings, numbers, booleans, objects) automatically serialized into JSON, matching desktop `samp-cef`.
- **Custom URL Scheme Interception:** `WebViewClient.shouldInterceptRequest()` automatically translates desktop `package://` and `http://package/` URLs to local APK assets via `WebViewAssetLoader`, avoiding CORS and network delay.
- **Crash-Resistant IME Handling:** Configures `windowSoftInputMode="adjustNothing"` to prevent Android keyboard popups from resizing `GLSurfaceView` and triggering fatal EGL surface recreation crashes in `libGTASA.so`.
- **Server Include:** Clean `cef_mobile.inc` Pawn include using `Pawn.RakNet` for seamless integration in SA-MP and open.mp.

### Non-Goals
- Porting desktop `libcef.so` binary directly to Android.
- Rewriting GTA SA game rendering or audio pipelines.
- Building custom gamemode roleplay logic beyond the CEF integration and demo assets.

### Anti-Goals
- **EGL Surface Destruction:** Resizing the native game surface when the virtual keyboard opens.
- **Uncontrolled Touch Leakage:** Allowing taps on interactive CEF web dialogs to fire weapons in-game.
- **Secondary Network Ports:** Requiring open TCP/HTTP/WebSocket ports on the server, which complicates firewall rules, breaks NAT, and causes desync.
- **32-bit Only Limitation:** Restricting to `armeabi-v7a`, which causes installation failure on Android 14+ 64-bit-only hardware.

---

## 3. Constraints
- **Android SDK & NDK:** Target Android SDK 33/34, NDK r21e to r25c compatible with Alyn GTA SA v2.10 codebase build rules.
- **Threading & JNI:** All WebView DOM calls and `evaluateJavascript` must execute on the Android UI Main Thread, while RakNet packet reception occurs on the network thread.
- **Memory Footprint:** Keep WebView memory overhead strictly isolated; hide unused web layers via CSS/DOM visibility.

---

## 4. Evaluated Approaches & Selection

### Approach 1: Full Turnkey Client (Selected)
- Scaffold full Android Studio project based on Alyn SA-MP Mobile v2.10 with integrated NDK build, C++ JNI bridge, `CefManager.java`, server include `cef_mobile.inc`, and sample web assets.
- **Trade-off:** Slightly larger repository footprint, but provides an immediate end-to-end working workspace that can be opened and compiled in Android Studio.

### Approach 2: Modular CEF Bridge Kit
- Independent module with Java bridge, C++ patch diffs, and Pawn include without full client base.
- **Trade-off:** Minimal size, but requires manual integration into an external base before compilation.

### Approach 3: Protocol & Mock Testbed First
- Build only server include and web mock testbed.
- **Trade-off:** Defers native compilation and APK verification.

**Decision:** Approach 1 was chosen to provide a complete, buildable, and testable end-to-end solution.

---

## 5. Architectural Specification

### 5.1 Protocol Specification (RakNet Custom Packet ID 215)
All packets share Packet ID `215` (`0xD7`), followed by a 1-byte `subId`:

| Sub-ID | Name | Direction | Payload Structure | Description |
|---|---|---|---|---|
| `0x01` | `CEF_LOAD_URL` | Server -> Client | `uint32_t urlLen`, `char url[]`, `uint8_t isFocused` | Loads/navigates SPA root URL |
| `0x02` | `CEF_EMIT_CLIENT` | Server -> Client | `uint16_t evLen`, `char eventName[]`, `uint16_t dataLen`, `char jsonPayload[]` | Sends event to `window.cef._trigger` |
| `0x03` | `CEF_TOGGLE_FOCUS` | Server -> Client | `uint8_t isInteractive` | 0 = passive touch pass-through, 1 = interactive UI focus |
| `0x04` | `CEF_EMIT_SERVER` | Client -> Server | `uint16_t evLen`, `char eventName[]`, `uint16_t dataLen`, `char jsonPayload[]` | Sends client event to server `IPacket:215` |
| `0x05` | `CEF_SET_VISIBILITY` | Server -> Client | `uint8_t isVisible` | 0 = `View.GONE`, 1 = `View.VISIBLE` |

### 5.2 Native C++ Layer (`jni/`)
- **Hook `CNetGame::UpdateNetwork()` in `jni/net/netgame.cpp`:**
  - Case `215`: Read BitStream, dispatch based on `subId`:
    - `0x01`: `pJavaWrapper->CefLoadUrl(url, isFocused);`
    - `0x02`: `pJavaWrapper->CefEmitClient(eventName, jsonPayload);`
    - `0x03`: `pJavaWrapper->CefToggleFocus(isInteractive);`
    - `0x05`: `pJavaWrapper->CefSetVisibility(isVisible);`
- **Native Game Input Lock:**
  - When `isInteractive == true`, native game touch event dispatchers in `jni/game/` suppress touch transmission to GTA SA's camera and control pad.
- **JNI Bridge in `jni/util/CJavaWrapper.cpp`:**
  - Native to Java: Calls methods on `CefManager` instance via cached `jmethodID` scheduled on UI thread.
  - Java to Native: `Java_com_rockstargames_gtasa_core_CefManager_nativeSendCefEvent(JNIEnv *env, jobject thiz, jstring event, jstring json)` writes Packet 215 Sub-ID 4 to `pNetGame->GetRakClient()->Send()`.

### 5.3 Android Layer (`app/src/main/`)
- **Activity & Layout:** In `GTASA.java` / `MainActivity.java`, inflate `cef_webview` inside a `FrameLayout` covering the game viewport.
- **Crash Prevention in `AndroidManifest.xml`:**
  - Set `android:windowSoftInputMode="adjustNothing"` to prevent keyboard from resizing `GLSurfaceView` and crashing EGL.
  - Set `android:hardwareAccelerated="true"`.
- **`CefManager.java`:**
  - Transparent background (`Color.TRANSPARENT`).
  - Hardware acceleration enabled (`LAYER_TYPE_HARDWARE`).
  - **Asset Loader & Custom Scheme Interception:**
    - Configures `WebViewAssetLoader` with `AssetsPathHandler` for `assets/cef/`.
    - In `WebViewClient.shouldInterceptRequest()`, translates `package://` and `http://package/` to `https://appassets.androidplatform.net/assets/`.
  - **Dynamic Touch Routing:**
    - Passive mode (`isInteractive == false`): `setClickable(false)` and `setOnTouchListener((v, ev) -> false)`. Touches fall through to `GLSurfaceView`.
    - Interactive mode (`isInteractive == true`): `setClickable(true)` and `setOnTouchListener((v, ev) -> true)` for backdrop, passing clicks to web elements.
  - **Injected Javascript Bridge (`@JavascriptInterface`):**
    - `AndroidBridge.sendToServer(event, jsonPayload)`.
  - **Compatibility Shim Injected at `onPageFinished`:**
    ```javascript
    window.cef = window.cef || {};
    window.cef._listeners = {};
    window.cef.emit = function(event, ...args) {
        AndroidBridge.sendToServer(event, JSON.stringify(args.length === 1 ? args[0] : args));
    };
    window.cef.on = function(event, callback) {
        window.cef._listeners[event] = callback;
    };
    window.cef._trigger = function(event, rawJson) {
        if (window.cef._listeners[event]) {
            try { window.cef._listeners[event](JSON.parse(rawJson)); }
            catch(e) { window.cef._listeners[event](rawJson); }
        }
    };
    ```

### 5.4 Server-Side Layer (`cef_mobile.inc`)
- Provides Pawn API:
  - `CEF_LoadUrl(playerid, const url[], bool:focused = false)`
  - `CEF_Emit(playerid, const event[], const json[])`
  - `CEF_ToggleFocus(playerid, bool:interactive)`
  - `CEF_SetVisibility(playerid, bool:visible)`
  - `IPacket:215(playerid, BitStream:bs)` callback hook for handling incoming client events.

### 5.5 Web UI Client Layer (`assets/cef/`)
- Sample modern UI demonstrating:
  - Passive HUD (Health, Armour, Money, Speedometer).
  - Interactive Dialog (Login modal / Inventory).
  - Two-way event listener responding to `window.cef.on("hud:update", ...)`.

---

## 6. Resolved Builder Review Calls
1. **Codebase Baseline:** Scaffolding adheres to Alyn SA-MP Mobile v2.10 (GTA SA v2.10 supporting both `armeabi-v7a` and `arm64-v8a`).
2. **Single-SPA Lifecycle:** Sub-ID `0x05` (`CEF_SET_VISIBILITY`) added. Web UI operates as a single SPA to prevent reload flicker and memory spikes.
3. **Variadic JS Arguments:** Handled directly in the injected JavaScript shim (`JSON.stringify(args.length === 1 ? args[0] : args)`).
4. **Local Scheme Interception:** `WebViewClient.shouldInterceptRequest` maps `package://` and `http://package/` seamlessly to `WebViewAssetLoader`.
5. **Input Lock & IME Safety:** Android manifest configured with `adjustNothing` to prevent EGL crashes; C++ input hooks suppress game touch dispatch while interactive.
