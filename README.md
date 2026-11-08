# ROMP - SA-MP & open.mp Android CEF Mobile Client

High-performance, lightweight Chromium/WebView CEF (Chromium Embedded Framework) overlay client and server integration subsystem for **GTA SA-MP 0.3.7** and **open.mp (OMP)** Android mobile clients.

---

## Architecture Overview

```
+-------------------------------------------------------------+
|                     SA-MP / open.mp Server                  |
|                  (cef_mobile.inc + Pawn.RakNet)             |
+-------------------------------------------------------------+
                              ▲
                              │ RakNet Packet ID 215 (Sub-IDs 1..5)
                              ▼
+-------------------------------------------------------------+
|                Android Native C++ (libsamp.so)              |
|        - cef_net.cpp: Packet 215 interceptor & validator   |
|        - CJavaWrapper.cpp: JNI dispatcher to UI Thread      |
+-------------------------------------------------------------+
                              ▲
                              │ JNI Callbacks / nativeSendCefEvent
                              ▼
+-------------------------------------------------------------+
|               Android App Layer (CefManager.java)           |
|        - Hardware-accelerated transparent WebView           |
|        - Dynamic Touch Pass-Through (HUD vs Dialog)         |
|        - windowSoftInputMode="adjustNothing" (No EGL crash) |
|        - WebViewAssetLoader (package:// scheme intercept)   |
+-------------------------------------------------------------+
                              ▲
                              │ JavaScript Bridge
                              ▼
+-------------------------------------------------------------+
|                 Web Frontend SPA (assets/cef/)              |
|        - cef.js: window.cef.emit & window.cef.on API        |
|        - index.html: Transparent HUD & interactive modals   |
+-------------------------------------------------------------+
```

---

## Key Features

1. **Dual ABI Support (`armeabi-v7a` & `arm64-v8a`):**
   * Ready for modern Android 14+ 64-bit-only devices as well as legacy 32-bit devices.
2. **Dynamic Touch Routing:**
   * **Passive Mode (HUD):** Touches fall through directly to `GLSurfaceView` (`setClickable(false)`), allowing continuous control of analog sticks, buttons, and camera swipes.
   * **Interactive Mode (Dialogs/Menus):** Touch focus claimed by WebView (`setClickable(true)`), with native game touch suppressed in C++ to prevent accidental shooting or moving.
3. **No EGL Surface Resize Crashes:**
   * `android:windowSoftInputMode="adjustNothing"` ensures virtual keyboard popups never resize or recreate GTA:SA's native EGL render surface.
4. **Desktop PC `samp-cef` Script Compatibility:**
   * Supports `window.cef.emit(name, ...args)` with variadic arguments automatically serialized to JSON.
   * Intercepts `package://` and `http://package/` URLs and maps them to local APK `assets/cef/` via `WebViewAssetLoader`.
5. **No Extra Ports:**
   * Communicates entirely over existing RakNet BitStream connection (Packet ID `215`).

---

## Repository Structure

```
ROMP/
├── app/
│   ├── build.gradle                          # Android module config (dual ABI, SDK 34)
│   └── src/main/
│       ├── AndroidManifest.xml               # EGL-safe activity manifest
│       ├── assets/cef/                       # Web frontend assets (SPA)
│       │   ├── cef.js                        # Unified JavaScript CEF bridge
│       │   ├── index.html                    # In-game HUD & Dialog overlay UI
│       │   └── style.css                     # GPU-accelerated styling
│       ├── java/com/rockstargames/gtasa/core/
│       │   └── CefManager.java               # Android WebView overlay manager
│       └── res/layout/
│           └── activity_main_overlay.xml     # Game overlay frame layout
├── jni/
│   ├── CMakeLists.txt                        # Native build script
│   ├── cef/
│   │   ├── cef_net.h                         # Packet 215 protocol constants
│   │   └── cef_net.cpp                       # BitStream parser & bounds validator
│   └── util/
│       ├── CJavaWrapper.h                    # JNI wrapper header
│       └── CJavaWrapper.cpp                  # JNI bridge & native send handler
├── server/pawno/include/
│   └── cef_mobile.inc                        # Pawn.RakNet server include for SA-MP & OMP
├── tests/                                    # Automated unit & integration tests
│   ├── test_packet_protocol.py
│   ├── test_cef_bridge.js
│   ├── test_cef_manager.py
│   ├── test_native_packet_parser.py
│   └── test_e2e_integration.py
├── build.gradle                              # Root Gradle build script
└── settings.gradle                           # Root Gradle settings
```

---

## How to Build & Run

### 1. Build Client APK (Android Studio)
1. Open this repository in **Android Studio** (Hedgehog or newer).
2. Configure your Android NDK path (r21e - r25c recommended).
3. Connect an Android device or emulator (`armeabi-v7a` or `arm64-v8a`).
4. Select `Build > Assemble Project` or run `gradle assembleDebug`.

### 2. Install Server Include
1. Copy `server/pawno/include/cef_mobile.inc` to your SA-MP or open.mp server's `pawno/include/` directory.
2. In your gamemode or filterscript:
```pawn
#include <a_samp>
#include <Pawn.RakNet>
#include <cef_mobile>

public OnPlayerSpawn(playerid) {
    // Load local asset SPA
    CEF_LoadUrl(playerid, "package://cef/index.html", false);
    return 1;
}

public OnCefEvent(playerid, const event[], const json[]) {
    if (!strcmp(event, "dialog:response")) {
        // Handle dialog submission
    }
    return 1;
}
```

---

## Running Automated Tests

```bash
# Run Packet 215 BitStream protocol tests
python -m unittest tests/test_packet_protocol.py

# Run Frontend CEF JavaScript bridge tests
node tests/test_cef_bridge.js

# Run Android CefManager contract tests
python -m unittest tests/test_cef_manager.py

# Run Native C++ JNI bridge tests
python -m unittest tests/test_native_packet_parser.py

# Run End-to-End integration suite
python -m unittest tests/test_e2e_integration.py
```

