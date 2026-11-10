import os

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JNI_DIR = os.path.join(ROOT_DIR, 'app', 'src', 'main', 'jniLibs')

def patch_file(path, patches):
    print(f"[*] Patching {path}...")
    with open(path, 'rb') as f:
        data = bytearray(f.read())
    
    for offset, old_bytes, new_bytes, desc in patches:
        current = data[offset : offset + len(old_bytes)]
        if current == new_bytes:
            print(f"  [-] Already patched: {desc} at {hex(offset)}")
            continue
        if current != old_bytes:
            raise ValueError(f"Byte mismatch at {hex(offset)} ({desc})! Expected {old_bytes.hex()}, got {current.hex()}")
        data[offset : offset + len(new_bytes)] = new_bytes
        print(f"  [+] Patched {desc} at {hex(offset)}: {old_bytes.hex()} -> {new_bytes.hex()}")

    with open(path, 'wb') as f:
        f.write(data)
    print(f"[+] Successfully wrote {path}")

def run():
    # 1. x86_64
    x86_64_so = os.path.join(JNI_DIR, 'x86_64', 'libalyn_resa.so')
    x86_64_patches = [
        # security_check at 0x1294970: replace with 'movb $2, %al; retq'
        (0x1294970, bytes.fromhex('b0 02 c3 90 90 90 90 90'), bytes.fromhex('b0 02 c3 90 90 90 90 90'), "x86_64 security_check return 2"),
        # maps_reader at 0x1290bf0: replace with 'movb $2, %al; retq'
        (0x1290bf0, bytes.fromhex('b0 02 c3 90 90 90 90 90 90'), bytes.fromhex('b0 02 c3 90 90 90 90 90 90'), "x86_64 maps_reader return 2"),
        # JNI_OnLoad conditional jump at 0x105c04c: replace 'je 0x105c2b5' with 6 NOPs
        (0x105c04c, bytes.fromhex('90 90 90 90 90 90'), bytes.fromhex('90 90 90 90 90 90'), "x86_64 JNI_OnLoad bypass je error"),
        # JNI_OnLoad call _exit at 0x105c391: replace with 5 NOPs
        (0x105c391, bytes.fromhex('90 90 90 90 90'), bytes.fromhex('90 90 90 90 90'), "x86_64 JNI_OnLoad bypass _exit"),
        # Killer function at 0x1297940: replace with 'retq; nop; ...'
        (0x1297940, bytes.fromhex('c3 90 90 90 90 90 90 90 90 90'), bytes.fromhex('c3 90 90 90 90 90 90 90 90 90'), "x86_64 killer_func return early"),
        # Killer function call _exit at 0x12979a5: replace with 5 NOPs
        (0x12979a5, bytes.fromhex('90 90 90 90 90'), bytes.fromhex('90 90 90 90 90'), "x86_64 killer_func bypass _exit"),
        # Secondary call _exit at 0x1297002: replace with 5 NOPs
        (0x1297002, bytes.fromhex('90 90 90 90 90'), bytes.fromhex('90 90 90 90 90'), "x86_64 bypass _exit at 0x1297002"),
        # JNI_OnLoad tail call _exit at 0x105ed1c: replace with 5 NOPs
        (0x105ed1c, bytes.fromhex('90 90 90 90 90'), bytes.fromhex('90 90 90 90 90'), "x86_64 bypass _exit at 0x105ed1c"),

        # NEW: PLT _exit stub at 0x1815fc0: replace 'ff 25 8a 98 07 00' with 'retq; nop; nop; nop; nop; nop'
        (0x1815fc0, bytes.fromhex('c3 90 90 90 90 90'), bytes.fromhex('c3 90 90 90 90 90'), "x86_64 PLT _exit stub return early"),

        # NEW: Watchdog thread launcher at 0x1296880: replace entry with 'retq; nop; nop; nop'
        (0x1296880, bytes.fromhex('c3 90 90 90'), bytes.fromhex('c3 90 90 90'), "x86_64 watchdog thread launcher disable"),

        # NEW: Watchdog thread body at 0x1296910: replace entry with 'retq; nop; nop; nop'
        (0x1296910, bytes.fromhex('c3 90 90 90'), bytes.fromhex('c3 90 90 90'), "x86_64 watchdog thread body disable"),

        # NEW: 4 error log print sites: replace call __android_log_print with 5 NOPs
        (0x105c377, bytes.fromhex('90 90 90 90 90'), bytes.fromhex('90 90 90 90 90'), "x86_64 silence log print 1"),
        (0x1296fe8, bytes.fromhex('90 90 90 90 90'), bytes.fromhex('90 90 90 90 90'), "x86_64 silence log print 2"),
        (0x129799e, bytes.fromhex('90 90 90 90 90'), bytes.fromhex('90 90 90 90 90'), "x86_64 silence log print 3"),
        (0x12a2ed7, bytes.fromhex('90 90 90 90 90'), bytes.fromhex('90 90 90 90 90'), "x86_64 silence log print 4"),

        # NEW: Master integrity & memory check at 0x12a2a70: replace with 'mov $2, %eax; retq; nop; nop'
        (0x12a2a70, bytes.fromhex('55 48 89 e5 41 57 41 56'), bytes.fromhex('b8 02 00 00 00 c3 90 90'), "x86_64 master integrity check return 2"),

        # NEW: Bypass _exit and __stack_chk_fail at 0x12a2eed / 0x12a2ef2 with retq
        (0x12a2eed, bytes.fromhex('90 90 90 90 90'), bytes.fromhex('c3 90 90 90 90'), "x86_64 bypass _exit at 0x12a2eed with retq"),
        (0x12a2ef2, bytes.fromhex('e8 19 14 57 00'), bytes.fromhex('c3 90 90 90 90'), "x86_64 bypass __stack_chk_fail at 0x12a2ef2 with retq"),

        # ONLINE CONNECT GATES: allow CNetGame::Process -> pRakClient->Connect
        (0x1295640, bytes.fromhex('55 48 89 e5 0f b6 05 75'), bytes.fromhex('31 c0 c3 90 90 90 90 90'), "x86_64 tamper flag check always false (0)"),
        (0x1298620, bytes.fromhex('48 8b 15 d9 78 68 02 48'), bytes.fromhex('b0 01 c3 90 90 90 90 90'), "x86_64 ClientSecurity session valid check always true (1)"),
        (0x12e27f0, bytes.fromhex('55 48 89 e5 48 8b 05 25'), bytes.fromhex('b0 01 c3 90 90 90 90 90'), "x86_64 integrity canary check always true (1)"),
        (0x12988f0, bytes.fromhex('55 48 89 e5 41 57 41 56'), bytes.fromhex('c3 90 90 90 90 90 90 90'), "x86_64 disable ClientSecurity periodic renewal/unauthorised warning"),
        (0x12e25a0, bytes.fromhex('55 48 89 e5 53 48 83 ec'), bytes.fromhex('c3 90 90 90 90 90 90 90'), "x86_64 disable canary periodic warning"),
    ]
    patch_file(x86_64_so, x86_64_patches)

    # 2. arm64-v8a
    arm64_so = os.path.join(JNI_DIR, 'arm64-v8a', 'libalyn_resa.so')
    arm64_patches = [
        # security_check at 0x1189124: replace with 'mov w0, #2; ret'
        (0x1189124, bytes.fromhex('40 00 80 52 c0 03 5f d6'), bytes.fromhex('40 00 80 52 c0 03 5f d6'), "arm64 security_check return 2"),
        # JNI_OnLoad branch at 0xf87b7c: replace 'b.eq 0xf87dcc' with 'nop'
        (0xf87b7c, bytes.fromhex('1f 20 03 d5'), bytes.fromhex('1f 20 03 d5'), "arm64 JNI_OnLoad bypass b.eq error"),
        # JNI_OnLoad call _exit at 0xf87eb0: replace with 'nop'
        (0xf87eb0, bytes.fromhex('1f 20 03 d5'), bytes.fromhex('1f 20 03 d5'), "arm64 JNI_OnLoad bypass _exit"),
        # Killer function at 0x118c254: replace with 'ret'
        (0x118c254, bytes.fromhex('c0 03 5f d6 1f 20 03 d5'), bytes.fromhex('c0 03 5f d6 1f 20 03 d5'), "arm64 killer_func return early"),
        # Killer function call _exit at 0x118c2c4: replace with 'nop'
        (0x118c2c4, bytes.fromhex('1f 20 03 d5'), bytes.fromhex('1f 20 03 d5'), "arm64 killer_func bypass _exit"),
        # Secondary call _exit at 0x118b908: replace with 'nop'
        (0x118b908, bytes.fromhex('1f 20 03 d5'), bytes.fromhex('1f 20 03 d5'), "arm64 bypass _exit at 0x118b908"),
        # Secondary call _exit at 0x1197dc0: replace with 'nop'
        (0x1197dc0, bytes.fromhex('1f 20 03 d5'), bytes.fromhex('1f 20 03 d5'), "arm64 bypass _exit at 0x1197dc0"),
        # JNI_OnLoad tail call _exit at 0xf8a6e0: replace with 'nop'
        (0xf8a6e0, bytes.fromhex('1f 20 03 d5'), bytes.fromhex('1f 20 03 d5'), "arm64 bypass _exit at 0xf8a6e0"),

        # NEW: PLT _exit stub at 0x1668050: replace with 'ret'
        (0x1668050, bytes.fromhex('50 04 00 f0'), bytes.fromhex('c0 03 5f d6'), "arm64 PLT _exit stub return early"),
    ]
    patch_file(arm64_so, arm64_patches)

    print("\n[+] All native RenderWare / SA-MP libraries successfully patched across all architectures!")

if __name__ == '__main__':
    run()
