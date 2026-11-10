import os
import subprocess
import zipfile
import shutil
import re

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APK_PATH = os.path.join(ROOT_DIR, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk')
BUILD_TOOLS = r'C:\Android\Sdk\build-tools\34.0.0'
KEYSTORE = r'C:\Users\Administrator\.android\debug.keystore'
ENGINE_DEX_DIR = os.path.join(ROOT_DIR, 'replica', 'engine_dex')
ENGINE_DEX = os.path.join(ENGINE_DEX_DIR, 'classes.dex')
FLUTTER_DEX_DIR = os.path.join(ROOT_DIR, 'replica', 'flutter_dex')
FLUTTER_DEX = os.path.join(FLUTTER_DEX_DIR, 'classes.dex')

def ensure_engine_dex():
    if not os.path.exists(ENGINE_DEX) or os.path.getsize(ENGINE_DEX) < 10000:
        raise FileNotFoundError(f"Engine DEX not found at {ENGINE_DEX}")

def get_next_dex_name(existing_dex_names):
    # Matches 'classes.dex', 'classes2.dex', 'classes3.dex', etc.
    indices = [1]
    for name in existing_dex_names:
        if name == 'classes.dex':
            indices.append(1)
        else:
            m = re.match(r'classes(\d+)\.dex', name)
            if m:
                indices.append(int(m.group(1)))
    next_idx = max(indices) + 1
    return f'classes{next_idx}.dex'

def package_3d_engine():
    print(f"[3D-Engine] Processing {APK_PATH}...")
    ensure_engine_dex()
    
    temp_apk = os.path.join(ROOT_DIR, 'replica', 'temp_packed.apk')
    aligned_apk = os.path.join(ROOT_DIR, 'replica', 'temp_aligned.apk')
    
    # Check existing entries in APK
    added_entries = set()
    existing_dex = []
    engine_dex_entry = None
    flutter_dex_entry = None
    
    with zipfile.ZipFile(APK_PATH, 'r') as zin:
        for item in zin.infolist():
            if item.filename.endswith('.dex'):
                existing_dex.append(item.filename)
                # Check if Game or FlutterEngine class is already in this dex
                data = zin.read(item.filename)
                if b'Lcom/alyn/resa/Game;' in data:
                    engine_dex_entry = item.filename
                elif b'Lio/flutter/embedding/engine/FlutterEngine;' in data:
                    flutter_dex_entry = item.filename
    
    print(f"[3D-Engine] Existing DEX files: {existing_dex}")
    print(f"[3D-Engine] Engine DEX entry in APK: {engine_dex_entry}")
    print(f"[3D-Engine] Flutter DEX entry in APK: {flutter_dex_entry}")
    
    jni_dir = os.path.join(ROOT_DIR, 'app', 'src', 'main', 'jniLibs')

    with zipfile.ZipFile(APK_PATH, 'r') as zin, zipfile.ZipFile(temp_apk, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
        # 1. Copy all non-signature entries from original APK
        for item in zin.infolist():
            if item.filename.startswith('META-INF/'):
                continue
            if item.filename in added_entries:
                continue
            if item.filename in (engine_dex_entry, flutter_dex_entry):
                continue
            # For native .so libs, force fresh read from disk if available, and ZIP_STORED (uncompressed)
            if item.filename.startswith('lib/'):
                parts = item.filename.split('/') # e.g. ['lib', 'x86_64', 'libalyn_resa.so']
                disk_path = os.path.join(jni_dir, parts[1], parts[2]) if len(parts) >= 3 else None
                if disk_path and os.path.exists(disk_path):
                    with open(disk_path, 'rb') as f_disk:
                        content = f_disk.read()
                else:
                    content = zin.read(item.filename)
                zout.writestr(item.filename, content, compress_type=zipfile.ZIP_STORED)
            else:
                zout.writestr(item, zin.read(item.filename))
            added_entries.add(item.filename)
        
        # 2. Add / update engine DEX with latest compiled version
        target_dex = engine_dex_entry if engine_dex_entry else get_next_dex_name(existing_dex)
        with open(ENGINE_DEX, 'rb') as ef:
            engine_dex_bytes = ef.read()
        zout.writestr(target_dex, engine_dex_bytes)
        added_entries.add(target_dex)
        if target_dex not in existing_dex:
            existing_dex.append(target_dex)
        print(f"[3D-Engine] Bundled 3D RenderWare engine into {target_dex} ({len(engine_dex_bytes)//1024} KB)")

        # 2b. Add / update Flutter UI overlay DEX
        if os.path.exists(FLUTTER_DEX):
            flutter_target_dex = flutter_dex_entry if flutter_dex_entry else get_next_dex_name(existing_dex)
            with open(FLUTTER_DEX, 'rb') as ff:
                flutter_dex_bytes = ff.read()
            zout.writestr(flutter_target_dex, flutter_dex_bytes)
            added_entries.add(flutter_target_dex)
            print(f"[3D-Engine] Bundled Flutter UI overlay into {flutter_target_dex} ({len(flutter_dex_bytes)//1024} KB)")

        # 3. Add any missing native libraries from app/src/main/jniLibs
        jni_dir = os.path.join(ROOT_DIR, 'app', 'src', 'main', 'jniLibs')
        for arch in ['x86_64', 'arm64-v8a', 'armeabi-v7a']:
            arch_dir = os.path.join(jni_dir, arch)
            if os.path.exists(arch_dir):
                for lib in os.listdir(arch_dir):
                    entry = f'lib/{arch}/{lib}'
                    if entry not in added_entries:
                        lib_path = os.path.join(arch_dir, lib)
                        with open(lib_path, 'rb') as lf:
                            zout.writestr(entry, lf.read(), compress_type=zipfile.ZIP_STORED)
                        added_entries.add(entry)
                        print(f"[3D-Engine] Injected {entry}")

        # 4. Add any missing assets
        assets_dir = os.path.join(ROOT_DIR, 'app', 'src', 'main', 'assets')
        for root, dirs, files in os.walk(assets_dir):
            for f in files:
                full = os.path.join(root, f)
                rel = os.path.relpath(full, assets_dir).replace(os.sep, '/')
                entry = f'assets/{rel}'
                if entry not in added_entries:
                    with open(full, 'rb') as af:
                        zout.writestr(entry, af.read())
                    added_entries.add(entry)

    # 5. Zipalign (4-byte alignment, page-aligned shared libraries)
    print("[3D-Engine] Running zipalign...")
    if os.path.exists(aligned_apk):
        os.remove(aligned_apk)
    subprocess.check_call([
        os.path.join(BUILD_TOOLS, 'zipalign.exe'),
        '-f', '-p', '4',
        temp_apk,
        aligned_apk
    ])
    
    # 6. Apksigner
    print("[3D-Engine] Signing APK with debug keystore...")
    subprocess.check_call([
        os.path.join(BUILD_TOOLS, 'apksigner.bat'),
        'sign',
        '--ks', KEYSTORE,
        '--ks-pass', 'pass:android',
        '--ks-key-alias', 'androiddebugkey',
        '--key-pass', 'pass:android',
        aligned_apk
    ])
    
    # 7. Replace original output apk
    shutil.copyfile(aligned_apk, APK_PATH)
    print(f"[3D-Engine] SUCCESS: Standalone 3D Engine bundled into {APK_PATH} ({os.path.getsize(APK_PATH)//(1024*1024)} MB)")

if __name__ == '__main__':
    package_3d_engine()
