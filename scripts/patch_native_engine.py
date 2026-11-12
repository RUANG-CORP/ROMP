import os

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JNI_DIR = os.path.join(ROOT_DIR, 'app', 'src', 'main', 'jniLibs')

def patch_file(path, patches):
    print(f"[*] Patching {path}...")
    with open(path, 'rb') as f:
        data = bytearray(f.read())

    for offset, expected_bytes, new_bytes, desc in patches:
        expected_list = expected_bytes if isinstance(expected_bytes, (list, tuple)) else [expected_bytes]
        current = data[offset : offset + len(new_bytes)]
        if current == new_bytes:
            print(f"  [-] Already patched: {desc} at {hex(offset)}")
            continue
        if all(current != exp for exp in expected_list):
            exp_hex = " | ".join(e.hex() for e in expected_list)
            raise ValueError(f"Byte mismatch at {hex(offset)} ({desc})! Expected {exp_hex}, got {current.hex()}")
        data[offset : offset + len(new_bytes)] = new_bytes
        print(f"  [+] Patched {desc} at {hex(offset)}: {current.hex()} -> {new_bytes.hex()}")

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

        # PLT _exit stub at 0x1815fc0: replace with 'retq; nop; ...'
        (0x1815fc0, bytes.fromhex('c3 90 90 90 90 90'), bytes.fromhex('c3 90 90 90 90 90'), "x86_64 PLT _exit stub return early"),

        # Watchdog thread launcher & body disable
        (0x1296880, bytes.fromhex('c3 90 90 90'), bytes.fromhex('c3 90 90 90'), "x86_64 watchdog thread launcher disable"),
        (0x1296910, bytes.fromhex('c3 90 90 90'), bytes.fromhex('c3 90 90 90'), "x86_64 watchdog thread body disable"),

        # Silence 4 error log print sites
        (0x105c377, bytes.fromhex('90 90 90 90 90'), bytes.fromhex('90 90 90 90 90'), "x86_64 silence log print 1"),
        (0x1296fe8, bytes.fromhex('90 90 90 90 90'), bytes.fromhex('90 90 90 90 90'), "x86_64 silence log print 2"),
        (0x129799e, bytes.fromhex('90 90 90 90 90'), bytes.fromhex('90 90 90 90 90'), "x86_64 silence log print 3"),
        (0x12a2ed7, bytes.fromhex('90 90 90 90 90'), bytes.fromhex('90 90 90 90 90'), "x86_64 silence log print 4"),

        # Master integrity & memory check at 0x12a2a70: return 2
        (0x12a2a70, bytes.fromhex('55 48 89 e5 41 57 41 56'), bytes.fromhex('b8 02 00 00 00 c3 90 90'), "x86_64 master integrity check return 2"),

        # Bypass _exit and __stack_chk_fail at 0x12a2eed / 0x12a2ef2 with retq
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
        # JNI_OnLoad branch at 0xf87b7c: nop
        (0xf87b7c, bytes.fromhex('1f 20 03 d5'), bytes.fromhex('1f 20 03 d5'), "arm64 JNI_OnLoad bypass b.eq error"),
        # JNI_OnLoad call _exit at 0xf87eb0: nop
        (0xf87eb0, bytes.fromhex('1f 20 03 d5'), bytes.fromhex('1f 20 03 d5'), "arm64 JNI_OnLoad bypass _exit"),
        # Killer function at 0x118c254: ret; nop
        (0x118c254, bytes.fromhex('c0 03 5f d6 1f 20 03 d5'), bytes.fromhex('c0 03 5f d6 1f 20 03 d5'), "arm64 killer_func return early"),
        # Killer / secondary _exit calls
        (0x118c2c4, bytes.fromhex('1f 20 03 d5'), bytes.fromhex('1f 20 03 d5'), "arm64 killer_func bypass _exit"),
        (0x118b908, bytes.fromhex('1f 20 03 d5'), bytes.fromhex('1f 20 03 d5'), "arm64 bypass _exit at 0x118b908"),
        (0x1197dc0, bytes.fromhex('1f 20 03 d5'), bytes.fromhex('1f 20 03 d5'), "arm64 bypass _exit at 0x1197dc0"),
        (0xf8a6e0, bytes.fromhex('1f 20 03 d5'), bytes.fromhex('1f 20 03 d5'), "arm64 bypass _exit at 0xf8a6e0"),
        # PLT _exit stub at 0x1668050: ret
        (0x1668050, [bytes.fromhex('50 04 00 f0'), bytes.fromhex('c0 03 5f d6')], bytes.fromhex('c0 03 5f d6'), "arm64 PLT _exit stub return early"),

        # ONLINE CONNECT GATES (AArch64):
        # 0x1189ee4: mov w0, #0; ret
        (0x1189ee4, bytes.fromhex('88 2d 00 f0 08 c1 2b 91'), bytes.fromhex('e0 03 1f 2a c0 03 5f d6'), "arm64 tamper flag check always false (0)"),
        # 0x118cf80: mov w0, #1; ret
        (0x118cf80, bytes.fromhex('fd 7b be a9 f3 0b 00 f9'), bytes.fromhex('20 00 80 52 c0 03 5f d6'), "arm64 ClientSecurity session valid check always true (1)"),
        # 0x11d6a68: mov w0, #1; ret
        (0x11d6a68, bytes.fromhex('aa 82 8f d2 28 2e 01 f0'), bytes.fromhex('20 00 80 52 c0 03 5f d6'), "arm64 integrity canary check always true (1)"),
        # 0x118d22c: ret; nop
        (0x118d22c, bytes.fromhex('ff 03 02 d1 fd 7b 02 a9'), bytes.fromhex('c0 03 5f d6 1f 20 03 d5'), "arm64 disable ClientSecurity periodic renewal/unauthorised warning"),
        # 0x11d681c: ret; nop
        (0x11d681c, bytes.fromhex('ff 83 01 d1 fd 7b 04 a9'), bytes.fromhex('c0 03 5f d6 1f 20 03 d5'), "arm64 disable canary periodic warning"),
        # Watchdog / maps_reader / master integrity check parity with x86_64:
        # maps_reader at 0x1185338: mov w0, #2; ret
        (0x1185338, bytes.fromhex('fd 7b bd a9 fc 0b 00 f9'), bytes.fromhex('40 00 80 52 c0 03 5f d6'), "arm64 maps_reader return 2"),
        # watchdog thread launcher at 0x118b15c: ret; nop
        (0x118b15c, bytes.fromhex('ff c3 00 d1 fd 7b 01 a9'), bytes.fromhex('c0 03 5f d6 1f 20 03 d5'), "arm64 disable watchdog thread launcher"),
        # watchdog thread body at 0x118b204: ret; nop
        (0x118b204, bytes.fromhex('e9 23 b9 6d fd 7b 01 a9'), bytes.fromhex('c0 03 5f d6 1f 20 03 d5'), "arm64 disable watchdog thread body"),
        # master integrity & memory check at 0x1197964: mov w0, #2; ret
        (0x1197964, bytes.fromhex('fd 7b ba a9 fc 6f 01 a9'), bytes.fromhex('40 00 80 52 c0 03 5f d6'), "arm64 master integrity check return 2"),
    ]
    patch_file(arm64_so, arm64_patches)

    # 3. armeabi-v7a
    armv7_so = os.path.join(JNI_DIR, 'armeabi-v7a', 'libalyn_resa.so')
    armv7_patches = [
        # security_check at 0xd3a838 (Thumb-2): movs r0, #2; bx lr; nop; nop
        (0xd3a838, bytes.fromhex('f0 b5 03 af 2d e9 00 0f'), bytes.fromhex('02 20 70 47 00 bf 00 bf'), "armv7 security_check return 2"),
        # JNI_OnLoad branch at 0xb87b2a (Thumb-2): nop; nop
        (0xb87b2a, bytes.fromhex('00 f0 f2 80'), bytes.fromhex('00 bf 00 bf'), "armv7 JNI_OnLoad bypass b.eq error"),
        # PLT _exit stub at 0x112f9a0 (ARM state): bx lr
        (0x112f9a0, bytes.fromhex('00 c6 8f e2'), bytes.fromhex('1e ff 2f e1'), "armv7 PLT _exit stub return early"),

        # ONLINE CONNECT GATES (Thumb-2):
        # 0xd3b3ac: movs r0, #0; bx lr; nop; nop
        (0xd3b3ac, bytes.fromhex('02 48 78 44 00 78 bf f3'), bytes.fromhex('00 20 70 47 00 bf 00 bf'), "armv7 tamper flag check always false (0)"),
        # 0xd3e0e0: movs r0, #1; bx lr; nop; nop
        (0xd3e0e0, bytes.fromhex('b0 b5 02 af 84 b0 37 48'), bytes.fromhex('01 20 70 47 00 bf 00 bf'), "armv7 ClientSecurity session valid check always true (1)"),
        # 0xd7e880: movs r0, #1; bx lr; nop; nop
        (0xd7e880, bytes.fromhex('15 48 47 f6 b9 13 15 49'), bytes.fromhex('01 20 70 47 00 bf 00 bf'), "armv7 integrity canary check always true (1)"),
        # 0xd3e3c4: bx lr; nop; ...
        (0xd3e3c4, bytes.fromhex('f0 b5 03 af 2d e9 00 0f'), bytes.fromhex('70 47 00 bf 00 bf 00 bf'), "armv7 disable ClientSecurity periodic renewal/unauthorised warning"),
        # 0xd7e638: bx lr; nop; ...
        (0xd7e638, bytes.fromhex('d0 b5 02 af 8c b0 72 48'), bytes.fromhex('70 47 00 bf 00 bf 00 bf'), "armv7 disable canary periodic warning"),
        # Watchdog / maps_reader / master integrity check parity with x86_64 (Thumb-2):
        # maps_reader at 0xd368f4: movs r0, #2; bx lr; nop; nop
        (0xd368f4, bytes.fromhex('d0 b5 02 af ad f5 80 5d'), bytes.fromhex('02 20 70 47 00 bf 00 bf'), "armv7 maps_reader return 2"),
        # watchdog thread launcher at 0xd3c37c: bx lr; nop; nop; nop
        (0xd3c37c, bytes.fromhex('80 b5 6f 46 82 b0 1b 48'), bytes.fromhex('70 47 00 bf 00 bf 00 bf'), "armv7 disable watchdog thread launcher"),
        # watchdog thread body at 0xd3c400: bx lr; nop; nop; nop
        (0xd3c400, bytes.fromhex('f0 b5 03 af 2d e9 00 0f'), bytes.fromhex('70 47 00 bf 00 bf 00 bf'), "armv7 disable watchdog thread body"),
        # master integrity check at 0xd469a0: movs r0, #2; bx lr; nop; nop
        (0xd469a0, bytes.fromhex('f0 b5 03 af 2d e9 00 0f'), bytes.fromhex('02 20 70 47 00 bf 00 bf'), "armv7 master integrity check return 2"),
    ]
    patch_file(armv7_so, armv7_patches)

    print("\n[+] All native RenderWare / SA-MP libraries successfully patched across all architectures!")

if __name__ == '__main__':
    run()
