import argparse
import os
import subprocess
import zipfile
import shutil
import re

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENGINE_DEX_DIR = os.path.join(ROOT_DIR, 'replica', 'engine_dex')
ENGINE_DEX = os.path.join(ENGINE_DEX_DIR, 'classes.dex')
FLUTTER_DEX_DIR = os.path.join(ROOT_DIR, 'replica', 'flutter_dex')
FLUTTER_DEX = os.path.join(FLUTTER_DEX_DIR, 'classes.dex')


def resolve_build_tools():
    sdk_candidates = [
        os.environ.get('ANDROID_HOME'),
        os.environ.get('ANDROID_SDK_ROOT'),
        r'C:\Android\Sdk',
        '/usr/local/lib/android/sdk',
        os.path.expanduser('~/Android/Sdk'),
    ]
    for sdk in sdk_candidates:
        if not sdk or not os.path.isdir(sdk):
            continue
        bt_root = os.path.join(sdk, 'build-tools')
        if not os.path.isdir(bt_root):
            continue
        preferred = os.path.join(bt_root, '34.0.0')
        if os.path.isdir(preferred):
            return preferred
        versions = sorted(d for d in os.listdir(bt_root) if os.path.isdir(os.path.join(bt_root, d)))
        if versions:
            return os.path.join(bt_root, versions[-1])
    raise FileNotFoundError("Android SDK build-tools directory not found; set ANDROID_HOME.")


def resolve_build_tool(name):
    bt = resolve_build_tools()
    exts = ('.exe', '.bat', '') if os.name == 'nt' else ('',)
    for ext in exts:
        candidate = os.path.join(bt, f'{name}{ext}')
        if os.path.exists(candidate):
            return candidate
    found = shutil.which(name)
    if found:
        return found
    raise FileNotFoundError(f"Required Android build-tool '{name}' not found in {bt}")


def default_debug_keystore():
    return os.path.expanduser(os.path.join('~', '.android', 'debug.keystore'))


def ensure_engine_dex():
    if not os.path.exists(ENGINE_DEX) or os.path.getsize(ENGINE_DEX) < 10000:
        raise FileNotFoundError(f"Engine DEX not found at {ENGINE_DEX}")


def get_next_dex_name(existing_dex_names):
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


def load_release_signing_config():
    props_path = os.path.join(ROOT_DIR, 'keystore.properties')
    if not os.path.exists(props_path):
        raise FileNotFoundError(
            f"Release keystore.properties not found at {props_path}. "
            "Provide --keystore/--ks-pass/--ks-alias/--key-pass or create keystore.properties."
        )
    props = {}
    with open(props_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#') or '=' not in line:
                continue
            k, v = line.split('=', 1)
            props[k.strip()] = v.strip()
    store_file = props['storeFile']
    if not os.path.isabs(store_file):
        store_file = os.path.join(ROOT_DIR, store_file)
    return store_file, props['storePassword'], props['keyAlias'], props['keyPassword']


def resolve_default_apk(variant):
    if variant == 'release':
        signed = os.path.join(ROOT_DIR, 'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk')
        unsigned = os.path.join(ROOT_DIR, 'app', 'build', 'outputs', 'apk', 'release', 'app-release-unsigned.apk')
        return signed if os.path.exists(signed) else unsigned
    return os.path.join(ROOT_DIR, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk')


def package_3d_engine(
    variant='debug',
    apk_path=None,
    out_path=None,
    keystore=None,
    ks_pass=None,
    ks_alias=None,
    key_pass=None,
    abis=('arm64-v8a', 'armeabi-v7a'),
    compress_libs=True,
):
    target_apk = os.path.abspath(apk_path) if apk_path else resolve_default_apk(variant)
    if not os.path.exists(target_apk):
        raise FileNotFoundError(f"APK not found: {target_apk}")
    target_abis = tuple(a.strip() for a in abis if a.strip())
    lib_compress_type = zipfile.ZIP_DEFLATED if compress_libs else zipfile.ZIP_STORED

    if variant == 'release':
        if keystore:
            sign_ks = os.path.abspath(keystore)
            sign_ks_pass = ks_pass
            sign_alias = ks_alias or 'romp'
            sign_key_pass = key_pass or ks_pass
        else:
            sign_ks, sign_ks_pass, sign_alias, sign_key_pass = load_release_signing_config()
    else:
        sign_ks = keystore or default_debug_keystore()
        sign_ks_pass = ks_pass or 'android'
        sign_alias = ks_alias or 'androiddebugkey'
        sign_key_pass = key_pass or 'android'

    print(f"[3D-Engine] Processing ({variant}) {target_apk}...")
    ensure_engine_dex()

    temp_apk = os.path.join(ROOT_DIR, 'replica', f'temp_{variant}_packed.apk')
    aligned_apk = os.path.join(ROOT_DIR, 'replica', f'temp_{variant}_aligned.apk')

    added_entries = set()
    existing_dex = []
    engine_dex_entry = None
    flutter_dex_entry = None

    with zipfile.ZipFile(target_apk, 'r') as zin:
        for item in zin.infolist():
            if item.filename.endswith('.dex'):
                existing_dex.append(item.filename)
                data = zin.read(item.filename)
                if b'Lcom/alyn/resa/Game;' in data:
                    engine_dex_entry = item.filename
                elif b'Lio/flutter/embedding/engine/FlutterEngine;' in data:
                    flutter_dex_entry = item.filename

    print(f"[3D-Engine] Existing DEX files: {existing_dex}")
    print(f"[3D-Engine] Engine DEX entry in APK: {engine_dex_entry}")
    print(f"[3D-Engine] Flutter DEX entry in APK: {flutter_dex_entry}")

    jni_dir = os.path.join(ROOT_DIR, 'app', 'src', 'main', 'jniLibs')

    with zipfile.ZipFile(target_apk, 'r') as zin, zipfile.ZipFile(temp_apk, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            if item.filename.startswith('META-INF/'):
                continue
            if item.filename in added_entries:
                continue
            if item.filename in (engine_dex_entry, flutter_dex_entry):
                continue
            if item.filename.startswith('lib/'):
                parts = item.filename.split('/')
                arch = parts[1] if len(parts) >= 3 else None
                if arch not in target_abis:
                    continue
                disk_path = os.path.join(jni_dir, arch, parts[2]) if len(parts) >= 3 else None
                if disk_path and os.path.exists(disk_path):
                    with open(disk_path, 'rb') as f_disk:
                        content = f_disk.read()
                else:
                    content = zin.read(item.filename)
                zout.writestr(item.filename, content, compress_type=lib_compress_type)
            elif item.filename == 'resources.arsc':
                # Android 11+ (targetSdk 30+) requires uncompressed & 4-byte aligned resources.arsc
                zout.writestr('resources.arsc', zin.read(item.filename), compress_type=zipfile.ZIP_STORED)
            else:
                zout.writestr(item, zin.read(item.filename))
            added_entries.add(item.filename)

        target_dex = engine_dex_entry if engine_dex_entry else get_next_dex_name(existing_dex)
        with open(ENGINE_DEX, 'rb') as ef:
            engine_dex_bytes = ef.read()
        zout.writestr(target_dex, engine_dex_bytes)
        added_entries.add(target_dex)
        if target_dex not in existing_dex:
            existing_dex.append(target_dex)
        print(f"[3D-Engine] Bundled 3D RenderWare engine into {target_dex} ({len(engine_dex_bytes)//1024} KB)")

        if os.path.exists(FLUTTER_DEX):
            flutter_target_dex = flutter_dex_entry if flutter_dex_entry else get_next_dex_name(existing_dex)
            with open(FLUTTER_DEX, 'rb') as ff:
                flutter_dex_bytes = ff.read()
            zout.writestr(flutter_target_dex, flutter_dex_bytes)
            added_entries.add(flutter_target_dex)
            print(f"[3D-Engine] Bundled Flutter UI overlay into {flutter_target_dex} ({len(flutter_dex_bytes)//1024} KB)")

        for arch in target_abis:
            arch_dir = os.path.join(jni_dir, arch)
            if os.path.exists(arch_dir):
                for lib in os.listdir(arch_dir):
                    entry = f'lib/{arch}/{lib}'
                    if entry not in added_entries:
                        lib_path = os.path.join(arch_dir, lib)
                        with open(lib_path, 'rb') as lf:
                            zout.writestr(entry, lf.read(), compress_type=lib_compress_type)
                        added_entries.add(entry)
                        print(f"[3D-Engine] Injected {entry}")

        assets_dir = os.path.join(ROOT_DIR, 'app', 'src', 'main', 'assets')
        for root, _, files in os.walk(assets_dir):
            for f in files:
                full = os.path.join(root, f)
                rel = os.path.relpath(full, assets_dir).replace(os.sep, '/')
                entry = f'assets/{rel}'
                if entry not in added_entries:
                    with open(full, 'rb') as af:
                        zout.writestr(entry, af.read())
                    added_entries.add(entry)

    print("[3D-Engine] Running zipalign...")
    if os.path.exists(aligned_apk):
        os.remove(aligned_apk)
    subprocess.check_call([
        resolve_build_tool('zipalign'),
        '-f', '-p', '4',
        temp_apk,
        aligned_apk,
    ])

    print(f"[3D-Engine] Signing APK with {variant} keystore ({os.path.basename(sign_ks)})...")
    subprocess.check_call([
        resolve_build_tool('apksigner'),
        'sign',
        '--v1-signing-enabled', 'true',
        '--v2-signing-enabled', 'true',
        '--v3-signing-enabled', 'true',
        '--ks', sign_ks,
        '--ks-pass', f'pass:{sign_ks_pass}',
        '--ks-key-alias', sign_alias,
        '--key-pass', f'pass:{sign_key_pass}',
        aligned_apk,
    ])

    final_path = os.path.abspath(out_path) if out_path else target_apk
    os.makedirs(os.path.dirname(final_path), exist_ok=True)
    shutil.copyfile(aligned_apk, final_path)
    if out_path:
        print(f"[3D-Engine] Exported shareable APK to {final_path}")

    for tmp in (temp_apk, aligned_apk, aligned_apk + '.idsig'):
        if os.path.exists(tmp):
            os.remove(tmp)

    print(f"[3D-Engine] SUCCESS: Standalone 3D Engine bundled ({target_abis}, compress_libs={compress_libs}) -> {final_path} ({os.path.getsize(final_path)/(1024*1024):.1f} MB)")


def main():
    parser = argparse.ArgumentParser(description='Bundle ROMP 3D engine & Flutter overlay DEX/native libs into APK')
    parser.add_argument('--variant', choices=('debug', 'release'), default='debug')
    parser.add_argument('--apk', default=None, help='Override input/output APK path')
    parser.add_argument('--out', default=None, help='Optional shareable output APK path')
    parser.add_argument('--abis', default='arm64-v8a,armeabi-v7a', help='Comma-separated ABIs to include (phone-only default)')
    parser.add_argument('--no-compress-libs', action='store_true', help='Store .so uncompressed inside APK')
    parser.add_argument('--keystore', default=None)
    parser.add_argument('--ks-pass', default=None)
    parser.add_argument('--ks-alias', default=None)
    parser.add_argument('--key-pass', default=None)
    args = parser.parse_args()
    package_3d_engine(
        variant=args.variant,
        apk_path=args.apk,
        out_path=args.out,
        keystore=args.keystore,
        ks_pass=args.ks_pass,
        ks_alias=args.ks_alias,
        key_pass=args.key_pass,
        abis=tuple(args.abis.split(',')),
        compress_libs=not args.no_compress_libs,
    )


if __name__ == '__main__':
    main()
