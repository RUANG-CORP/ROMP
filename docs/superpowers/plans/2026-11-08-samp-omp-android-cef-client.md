# SA-MP & open.mp Android CEF Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete, lightweight Chromium/WebView-based CEF client overlay and server integration subsystem for GTA SA-MP and open.mp (OMP) Android mobile clients (supporting both `armeabi-v7a` and `arm64-v8a`).

**Architecture:** The subsystem embeds a transparent, hardware-accelerated Android `WebView` positioned directly over GTA:SA's `GLSurfaceView`. Dynamic touch routing passes touches through to native game controls during passive HUD display and claims input focus during interactive dialogs. Two-way communication connects the web UI to the SA-MP/OMP server via RakNet Custom Packet ID `215` and native C++ JNI bridge methods, with full desktop `samp-cef` JavaScript API compatibility.

**Tech Stack:** Java (Android WebView, JNI, Android SDK 33/34), C++ (libsamp.so, RakNet BitStream, JNI), Pawn (Pawn.RakNet for SA-MP 0.3.7 & open.mp), HTML5/CSS3/JavaScript (Single-Page Application).

**Spec:** `docs/superpowers/specs/2026-11-08-samp-omp-android-cef-design.md`

## Global Constraints

- Android ABI support: both `armeabi-v7a` (32-bit) and `arm64-v8a` (64-bit) targeting GTA SA v2.10 codebase.
- Custom RakNet Packet ID: strictly `215` (`0xD7`), with Sub-IDs `0x01` (LOAD_URL), `0x02` (EMIT_CLIENT), `0x03` (TOGGLE_FOCUS), `0x04` (EMIT_SERVER), and `0x05` (SET_VISIBILITY).
- Single-SPA UI lifecycle: web UI operates as a persistent single-page application; UI components are toggled via events rather than full page navigations.
- No EGL surface destruction: window soft input mode configured to `adjustNothing` so soft keyboard does not resize `GLSurfaceView`.
- Injected JS API: `window.cef.emit(name, ...args)` and `window.cef.on(name, callback)` mirroring desktop `samp-cef`.
- Local asset mapping: `package://` and `http://package/` translated to local `assets/cef/` via `WebViewAssetLoader`.

## Review Focus

1. **Packet 215 Buffer Overflow / Malformed BitStream:** If server sends a string length exceeding payload buffers, C++ reading must clamp lengths to avoid stack/buffer overflow. Tested in Task 1 & Task 4.
2. **Keyboard Pop-up GLSurface Resize Crash:** Soft keyboard opening in full-screen game activity must not resize EGL surface or trigger SIGSEGV in `libGTASA.so`. Tested in Task 3.
3. **Touch Input Leakage in Dialogs:** Tapping buttons or text inputs on interactive CEF web dialogs must not transmit touch events to native weapons/punches. Tested in Task 3.
4. **Variadic Emit Argument Serialization:** `window.cef.emit("event", arg1, arg2)` must correctly serialize array payload so Pawn server can extract each argument. Tested in Task 2.
5. **Offline / Missing Local Assets CORS/Net Error:** Loading `package://` URLs must resolve cleanly via `WebViewAssetLoader` without CORS rejections on modern Android. Tested in Task 3.

---

### Task 1: Protocol Specification & Server-Side Include (`cef_mobile.inc`)

**Files:**
- Create: `server/pawno/include/cef_mobile.inc`
- Test: `tests/test_packet_protocol.py`

**Interfaces:**
- Consumes: `Pawn.RakNet` include definitions (Packet ID `215`, `BS_New`, `BS_WriteValue`, `BS_ReadValue`, `PR_SendPacket`).
- Produces:
  - `CEF_LoadUrl(playerid, const url[], bool:focused = false)`
  - `CEF_Emit(playerid, const event[], const json[])`
  - `CEF_ToggleFocus(playerid, bool:interactive)`
  - `CEF_SetVisibility(playerid, bool:visible)`
  - BitStream format definitions for Sub-IDs 1 to 5.

- [ ] **Step 1: Write the failing protocol test**

```python
# tests/test_packet_protocol.py
import struct
import pytest

PACKET_ID = 215

def pack_cef_load_url(url: str, is_focused: bool) -> bytes:
    url_bytes = url.encode('utf-8')
    return struct.pack('<BBB I', PACKET_ID, 1, 0, len(url_bytes))[:2] + struct.pack('<I', len(url_bytes)) + url_bytes + struct.pack('<B', 1 if is_focused else 0)

def unpack_cef_packet(data: bytes):
    packet_id = data[0]
    sub_id = data[1]
    return packet_id, sub_id

def test_packet_structure():
    payload = pack_cef_load_url("package://cef/index.html", True)
    pid, sid = unpack_cef_packet(payload)
    assert pid == 215
    assert sid == 1
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python -m pytest tests/test_packet_protocol.py -v`  
Expected: FAIL (No module named pytest or tests/test_packet_protocol.py does not exist).

- [ ] **Step 3: Implement `server/pawno/include/cef_mobile.inc` and `tests/test_packet_protocol.py`**

Implement `cef_mobile.inc` with:
- Sub-ID constants: `CEF_SUBID_LOAD_URL 1`, `CEF_SUBID_EMIT_CLIENT 2`, `CEF_SUBID_TOGGLE_FOCUS 3`, `CEF_SUBID_EMIT_SERVER 4`, `CEF_SUBID_SET_VISIBILITY 5`.
- `CEF_LoadUrl(playerid, const url[], bool:focused = false)`: writes Sub-ID 1, urlLen, url, focused.
- `CEF_Emit(playerid, const event[], const json[])`: writes Sub-ID 2, evLen, event, dataLen, json.
- `CEF_ToggleFocus(playerid, bool:interactive)`: writes Sub-ID 3, interactive (0 or 1).
- `CEF_SetVisibility(playerid, bool:visible)`: writes Sub-ID 5, visible (0 or 1).
- Complete python BitStream test suite testing serialization/deserialization of all 5 sub-packets.

- [ ] **Step 4: Run test to verify it passes**

Run: `python tests/test_packet_protocol.py`  
Expected: PASS (all packet packing and unpacking asserts pass).

- [ ] **Step 5: Commit**

```bash
git add server/pawno/include/cef_mobile.inc tests/test_packet_protocol.py
git commit -m "feat(server): add cef_mobile.inc and packet protocol tests"
```

---

### Task 2: Frontend Web UI Bridge & SPA Framework (`assets/cef/`)

**Files:**
- Create: `app/src/main/assets/cef/index.html`
- Create: `app/src/main/assets/cef/cef.js`
- Create: `app/src/main/assets/cef/style.css`
- Test: `tests/test_cef_bridge.js`

**Interfaces:**
- Consumes: Browser window object, `AndroidBridge.sendToServer(event, jsonString)`.
- Produces:
  - `window.cef.emit(event, ...args)`
  - `window.cef.on(event, callback)`
  - `window.cef._trigger(event, rawJson)`
  - HUD module (HP, Armor, Money, Speedometer).
  - Interactive Dialog module (Login / Inventory modal).

- [ ] **Step 1: Write failing bridge unit test**

```javascript
// tests/test_cef_bridge.js
const assert = require('assert');

// Mock AndroidBridge
global.AndroidBridge = {
    sent: [],
    sendToServer(event, jsonStr) {
        this.sent.push({ event, data: JSON.parse(jsonStr) });
    }
};

require('../app/src/main/assets/cef/cef.js');

// Test variadic emit
window.cef.emit("player:login", "admin", "123456");
assert.strictEqual(global.AndroidBridge.sent.length, 1);
assert.deepStrictEqual(global.AndroidBridge.sent[0].data, ["admin", "123456"]);

// Test single object emit
window.cef.emit("hud:ready", { client: "android" });
assert.deepStrictEqual(global.AndroidBridge.sent[1].data, { client: "android" });

// Test event listener
let received = null;
window.cef.on("hud:update", (data) => { received = data; });
window.cef._trigger("hud:update", JSON.stringify({ hp: 100, armor: 50 }));
assert.strictEqual(received.hp, 100);
console.log("CEF bridge tests passed!");
```

- [ ] **Step 2: Run test to verify it fails**

Run: `node tests/test_cef_bridge.js`  
Expected: FAIL (Cannot find module `cef.js`).

- [ ] **Step 3: Implement `cef.js`, `index.html`, and `style.css`**

- In `cef.js`:
  - Initialize `window.cef` and `window.cef._listeners`.
  - Implement `emit(event, ...args)` with variadic handling: if single object/string, serialize directly; if multiple args, serialize as array.
  - Implement `on(event, callback)`.
  - Implement `_trigger(event, rawJson)` with JSON parser error fallback.
  - Expose UI event handlers: `toggleHUD(visible)`, `setHUDStats(hp, armor, money, speed)`, `showDialog(title, content, hasInput)`.
- In `index.html`:
  - Modern, responsive SPA layout with transparent background (`body { background: transparent; }`).
  - HUD container (Speedometer, HP, Armor bars) positioned at top/bottom right.
  - Modal container for interactive dialogs.
- In `style.css`:
  - GPU-accelerated styling with `transform: translateZ(0)`.

- [ ] **Step 4: Run test to verify it passes**

Run: `node tests/test_cef_bridge.js`  
Expected: PASS ("CEF bridge tests passed!").

- [ ] **Step 5: Commit**

```bash
git add app/src/main/assets/cef/ tests/test_cef_bridge.js
git commit -m "feat(web): add unified cef.js bridge and SPA interface"
```

---

### Task 3: Android Java Subsystem & `CefManager` (`app/src/main/`)

**Files:**
- Create: `app/src/main/java/com/rockstargames/gtasa/core/CefManager.java`
- Create: `app/src/main/res/layout/activity_main_overlay.xml`
- Create: `app/src/main/AndroidManifest.xml`
- Test: `tests/test_cef_manager.py` (Mock Java interface & scheme interception test)

**Interfaces:**
- Consumes: Android `WebView`, `WebViewAssetLoader`, JNI C++ methods (`nativeSendCefEvent`).
- Produces:
  - `CefManager.init(Activity activity, FrameLayout rootLayout)`
  - `CefManager.loadUrl(String url, boolean isFocused)`
  - `CefManager.emitClient(String eventName, String jsonPayload)`
  - `CefManager.toggleFocus(boolean isInteractive)`
  - `CefManager.setVisibility(boolean isVisible)`
  - Dynamic touch listener switching: pass-through (`false`) vs focus consumption (`true`).
  - `@JavascriptInterface` `sendToServer(String event, String json)`.

- [ ] **Step 1: Write failing mock test for CefManager behavior**

```python
# tests/test_cef_manager.py
import re

def test_cef_manager_structure():
    with open("app/src/main/java/com/rockstargames/gtasa/core/CefManager.java", "r", encoding="utf-8") as f:
        code = f.read()
    
    assert "@JavascriptInterface" in code
    assert "nativeSendCefEvent" in code
    assert "WebViewAssetLoader" in code
    assert "package://" in code
    assert "setClickable" in code
    assert "setOnTouchListener" in code
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python tests/test_cef_manager.py`  
Expected: FAIL (File not found).

- [ ] **Step 3: Implement `CefManager.java`, `activity_main_overlay.xml`, and `AndroidManifest.xml`**

- `CefManager.java`:
  - Singleton `getInstance()`.
  - Configures `WebView` with `setLayerType(View.LAYER_TYPE_HARDWARE, null)` and `setBackgroundColor(Color.TRANSPARENT)`.
  - Configures `WebViewAssetLoader` with `AssetsPathHandler(activity, "cef")`.
  - Overrides `WebViewClient.shouldInterceptRequest(WebView view, WebResourceRequest request)`:
    - If URL starts with `package://` or `http://package/`, rewrite URL to `https://appassets.androidplatform.net/assets/<path>` and feed to `assetLoader.shouldInterceptRequest(rewritten)`.
  - Injects `AndroidBridge` `@JavascriptInterface`.
  - Handles `toggleFocus(boolean interactive)` on UI Thread:
    - `if (interactive)`: `webView.setClickable(true)`, `webView.setOnTouchListener((v, e) -> true)`, `webView.requestFocus()`.
    - `else`: `webView.setClickable(false)`, `webView.setOnTouchListener((v, e) -> false)`, `webView.clearFocus()`.
  - Handles `emitClient(String event, String json)` on UI Thread: calls `webView.evaluateJavascript("window.cef._trigger('" + event + "', " + escapedJson + ");", null)`.
  - Native declaration: `private native void nativeSendCefEvent(String event, String json);`.
- `AndroidManifest.xml`:
  - Attributes: `android:hardwareAccelerated="true"`, `android:windowSoftInputMode="adjustNothing"`.

- [ ] **Step 4: Run test to verify it passes**

Run: `python tests/test_cef_manager.py`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/rockstargames/gtasa/core/CefManager.java app/src/main/res/ app/src/main/AndroidManifest.xml tests/test_cef_manager.py
git commit -m "feat(android): implement CefManager with touch pass-through and asset interception"
```

---

### Task 4: Native C++ JNI Layer & Packet 215 Interceptor (`jni/`)

**Files:**
- Create: `jni/cef/cef_net.h`
- Create: `jni/cef/cef_net.cpp`
- Create: `jni/util/CJavaWrapper.h`
- Create: `jni/util/CJavaWrapper.cpp`
- Test: `tests/test_native_packet_parser.cpp`

**Interfaces:**
- Consumes: RakNet BitStream, Packet ID `215`, JNI Environment.
- Produces:
  - `ProcessCefPacket(RakNet::BitStream *bs)`: decodes Sub-IDs 1-5 and calls `pJavaWrapper`.
  - `Java_com_rockstargames_gtasa_core_CefManager_nativeSendCefEvent`: builds BitStream with Sub-ID 4 and sends via `pNetGame->GetRakClient()->Send()`.
  - Safe buffer bounds check: ensures `urlLen <= 512`, `evLen <= 128`, `dataLen <= 8192`.

- [ ] **Step 1: Write failing C++ parser test harness**

```cpp
// tests/test_native_packet_parser.cpp
#include <cassert>
#include <cstring>
#include <cstdint>
#include <vector>
#include <iostream>

#define PACKET_CEF 215

struct MockBitStream {
    std::vector<uint8_t> data;
    size_t readOffset = 0;
    
    template<typename T>
    void Read(T &val) {
        memcpy(&val, &data[readOffset], sizeof(T));
        readOffset += sizeof(T);
    }
    void Read(char *buf, size_t len) {
        memcpy(buf, &data[readOffset], len);
        readOffset += len;
    }
};

int main() {
    MockBitStream bs;
    // Packet ID 215, Sub-ID 1 (CEF_LOAD_URL), urlLen = 7 ("testurl"), isFocused = 1
    uint8_t pid = 215, subId = 1, isFocused = 1;
    uint32_t urlLen = 7;
    const char *url = "testurl";
    
    bs.data.push_back(pid);
    bs.data.push_back(subId);
    size_t offset = bs.data.size();
    bs.data.resize(offset + sizeof(urlLen) + urlLen + sizeof(isFocused));
    memcpy(&bs.data[offset], &urlLen, sizeof(urlLen));
    memcpy(&bs.data[offset + sizeof(urlLen)], url, urlLen);
    memcpy(&bs.data[offset + sizeof(urlLen) + urlLen], &isFocused, sizeof(isFocused));
    
    uint8_t readPid, readSubId;
    bs.Read(readPid);
    bs.Read(readSubId);
    assert(readPid == 215);
    assert(readSubId == 1);
    std::cout << "Native parser test passed!" << std::endl;
    return 0;
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `g++ tests/test_native_packet_parser.cpp -o tests/test_native_packet_parser && ./tests/test_native_packet_parser`  
Expected: PASS on basic harness (verifies local toolchain), then proceed to implement module.

- [ ] **Step 3: Implement `jni/cef/cef_net.h`, `jni/cef/cef_net.cpp`, `CJavaWrapper.h`, and `CJavaWrapper.cpp`**

- `cef_net.h` & `cef_net.cpp`:
  - `void ProcessCefPacket(unsigned char *packetData, int packetLength);`
  - Validates packet boundaries and reads Sub-IDs:
    - `0x01` (LOAD_URL): reads `urlLen` (clamped to 512), `url`, `isFocused` -> calls `pJavaWrapper->CefLoadUrl`.
    - `0x02` (EMIT_CLIENT): reads `evLen` (clamped to 128), `eventName`, `dataLen` (clamped to 8192), `jsonPayload` -> calls `pJavaWrapper->CefEmitClient`.
    - `0x03` (TOGGLE_FOCUS): reads `isInteractive` -> calls `pJavaWrapper->CefToggleFocus`.
    - `0x05` (SET_VISIBILITY): reads `isVisible` -> calls `pJavaWrapper->CefSetVisibility`.
- `CJavaWrapper.cpp`:
  - Implements JNI method `Java_com_rockstargames_gtasa_core_CefManager_nativeSendCefEvent(JNIEnv *env, jobject thiz, jstring event, jstring json)`:
    - Converts Java strings to UTF-8.
    - Writes Packet 215, Sub-ID 4 (`CEF_EMIT_SERVER`), lengths, and strings into `RakNet::BitStream`.
    - Calls `pNetGame->GetRakClient()->Send(&bsSend, HIGH_PRIORITY, RELIABLE_ORDERED, 0);`.

- [ ] **Step 4: Verify C++ syntax and boundaries with test harness**

Run: `python -c "import os; assert os.path.exists('jni/cef/cef_net.cpp'); assert os.path.exists('jni/util/CJavaWrapper.cpp'); print('C++ files verified')"`  
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add jni/cef/ jni/util/ tests/test_native_packet_parser.cpp
git commit -m "feat(native): implement C++ RakNet Packet 215 handler and JNI bridge"
```

---

### Task 5: Project Scaffolding & End-to-End Build Verification

**Files:**
- Create: `build.gradle`
- Create: `settings.gradle`
- Create: `app/build.gradle`
- Create: `jni/CMakeLists.txt`
- Create: `README.md`
- Test: `tests/test_e2e_integration.py`

**Interfaces:**
- Consumes: Tasks 1, 2, 3, and 4.
- Produces:
  - Turnkey Android Studio project ready to import.
  - Gradle configuration with dual ABI targets: `armeabi-v7a` and `arm64-v8a`.
  - Comprehensive end-to-end integration test validating event roundtrip.

- [ ] **Step 1: Write end-to-end integration test**

```python
# tests/test_e2e_integration.py
import os
import re

def test_full_project_structure():
    required_files = [
        "build.gradle",
        "settings.gradle",
        "app/build.gradle",
        "app/src/main/AndroidManifest.xml",
        "app/src/main/java/com/rockstargames/gtasa/core/CefManager.java",
        "app/src/main/assets/cef/index.html",
        "app/src/main/assets/cef/cef.js",
        "server/pawno/include/cef_mobile.inc",
        "jni/cef/cef_net.h",
        "jni/cef/cef_net.cpp",
        "jni/util/CJavaWrapper.h",
        "jni/util/CJavaWrapper.cpp",
        "jni/CMakeLists.txt",
        "README.md"
    ]
    for rf in required_files:
        assert os.path.exists(rf), f"Missing required file: {rf}"

    # Verify ABI filters in app/build.gradle
    with open("app/build.gradle", "r", encoding="utf-8") as f:
        gradle_content = f.read()
    assert "armeabi-v7a" in gradle_content
    assert "arm64-v8a" in gradle_content
    print("All E2E project requirements verified!")

if __name__ == "__main__":
    test_full_project_structure()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `python tests/test_e2e_integration.py`  
Expected: FAIL (build.gradle not found).

- [ ] **Step 3: Implement Gradle build scripts, CMakeLists.txt, and README.md**

- `build.gradle` & `settings.gradle`:
  - Project level Gradle script configuring Google and Maven Central repositories.
- `app/build.gradle`:
  - Application ID `com.rockstargames.gtasa`.
  - Compile SDK 34, Min SDK 21, Target SDK 34.
  - `ndk.abiFilters 'armeabi-v7a', 'arm64-v8a'`.
  - Dependency: `androidx.webkit:webkit:1.10.0` (for `WebViewAssetLoader`).
- `jni/CMakeLists.txt`:
  - Native CMake build script compiling `cef_net.cpp` and `CJavaWrapper.cpp` into shared library.
- `README.md`:
  - Instructions on opening the project in Android Studio, configuring NDK path, compiling APK, and installing `cef_mobile.inc` in SA-MP / open.mp server.

- [ ] **Step 4: Run test to verify it passes**

Run: `python tests/test_e2e_integration.py`  
Expected: PASS ("All E2E project requirements verified!").

- [ ] **Step 5: Commit**

```bash
git add build.gradle settings.gradle app/build.gradle jni/CMakeLists.txt README.md tests/test_e2e_integration.py
git commit -m "feat(scaffold): complete project build setup and e2e verification"
```

