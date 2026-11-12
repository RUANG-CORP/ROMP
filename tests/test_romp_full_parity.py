import json
import os
import struct
import unittest
import xml.etree.ElementTree as ET


class TestRompFullParity(unittest.TestCase):
    ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    def p(self, *parts):
        return os.path.join(self.ROOT, *parts)

    def test_multi_arch_native_patches_and_discord_sdk(self):
        expected_patches = {
            "x86_64": [
                (0x1294970, bytes.fromhex("b002c39090909090")),
                (0x12a2a70, bytes.fromhex("b802000000c39090")),
                (0x1295640, bytes.fromhex("31c0c39090909090")),
                (0x1298620, bytes.fromhex("b001c39090909090")),
                (0x12e27f0, bytes.fromhex("b001c39090909090")),
                (0x12988f0, bytes.fromhex("c390909090909090")),
                (0x12e25a0, bytes.fromhex("c390909090909090")),
                (0x1815fc0, bytes.fromhex("c39090909090")),
            ],
            "arm64-v8a": [
                (0x1189124, bytes.fromhex("40008052c0035fd6")),
                (0x1189ee4, bytes.fromhex("e0031f2ac0035fd6")),
                (0x118cf80, bytes.fromhex("20008052c0035fd6")),
                (0x11d6a68, bytes.fromhex("20008052c0035fd6")),
                (0x118d22c, bytes.fromhex("c0035fd61f2003d5")),
                (0x11d681c, bytes.fromhex("c0035fd61f2003d5")),
                (0x1668050, bytes.fromhex("c0035fd6")),
                (0x1185338, bytes.fromhex("40008052c0035fd6")),
                (0x118b15c, bytes.fromhex("c0035fd61f2003d5")),
                (0x118b204, bytes.fromhex("c0035fd61f2003d5")),
                (0x1197964, bytes.fromhex("40008052c0035fd6")),
            ],
            "armeabi-v7a": [
                (0xd3a838, bytes.fromhex("0220704700bf00bf")),
                (0xb87b2a, bytes.fromhex("00bf00bf")),
                (0xd3b3ac, bytes.fromhex("0020704700bf00bf")),
                (0xd3e0e0, bytes.fromhex("0120704700bf00bf")),
                (0xd7e880, bytes.fromhex("0120704700bf00bf")),
                (0xd3e3c4, bytes.fromhex("704700bf00bf00bf")),
                (0xd7e638, bytes.fromhex("704700bf00bf00bf")),
                (0x112f9a0, bytes.fromhex("1eff2fe1")),
                (0xd368f4, bytes.fromhex("0220704700bf00bf")),
                (0xd3c37c, bytes.fromhex("704700bf00bf00bf")),
                (0xd3c400, bytes.fromhex("704700bf00bf00bf")),
                (0xd469a0, bytes.fromhex("0220704700bf00bf")),
            ],
        }
        for abi, checks in expected_patches.items():
            so_path = self.p("app", "src", "main", "jniLibs", abi, "libalyn_resa.so")
            self.assertTrue(os.path.exists(so_path), f"Missing {so_path}")
            with open(so_path, "rb") as f:
                for offset, patched_bytes in checks:
                    f.seek(offset)
                    actual = f.read(len(patched_bytes))
                    self.assertEqual(
                        actual,
                        patched_bytes,
                        f"Patch mismatch in {abi} @ {hex(offset)}: got {actual.hex()}",
                    )
            discord_so = self.p("app", "src", "main", "jniLibs", abi, "libdiscord_partner_sdk.so")
            self.assertTrue(os.path.exists(discord_so), f"Missing Discord SDK for {abi}")

    def test_settings_catalogue_binary_rsc1(self):
        cat_path = self.p("app", "src", "main", "assets", "flutter_assets", "assets", "settings", "catalogue_phone.bin")
        self.assertTrue(os.path.exists(cat_path))
        with open(cat_path, "rb") as f:
            data = f.read()

        self.assertEqual(data[:4], b"RSC1")
        ver, unk, count = struct.unpack_from("<III", data, 4)
        self.assertEqual(ver, 1)
        self.assertEqual(count, 142)

        pos = 16
        def read_str():
            nonlocal pos
            slen = struct.unpack_from("<I", data, pos)[0]
            pos += 4
            s = data[pos:pos + slen].decode("utf-8", errors="replace")
            pos += slen
            return s

        keys = []
        for _ in range(count):
            _id, _cat, _typ, _vis, _def, _fmin, _fmax, _fstep = struct.unpack_from("<IIIIIfff", data, pos)
            pos += 32
            key = read_str()
            title = read_str()
            _desc = read_str()
            self.assertTrue(len(key) > 0)
            self.assertTrue(len(title) > 0)
            keys.append(key)
            opt_count = struct.unpack_from("<I", data, pos)[0]
            pos += 4
            for _o in range(opt_count):
                pos += 4
                read_str()

        self.assertEqual(len(keys), 142)
        for expected_key in (
            "adjust_widgets",
            "visual_effects",
            "resolution_scale",
            "fps_limit",
            "voice_chat",
            "voice_micro_enable",
            "bind_foot_fec_fir",
        ):
            self.assertIn(expected_key, keys)

        java_cat_path = self.p("app", "src", "main", "java", "com", "rockstargames", "gtasa", "launcher", "data", "SettingsCatalogue.java")
        with open(java_cat_path, "r", encoding="utf-8") as f:
            java_cat = f.read()
        for extra_key in ("overlay_renderer", "al_loader", "mod_loader", "cef_overlay", "discord_rpc", "open_crosshair_editor", "open_font_picker"):
            self.assertIn(f'"{extra_key}"', java_cat)

    def test_al_loader_and_scripts_assets(self):
        self.assertTrue(os.path.exists(self.p("app", "src", "main", "assets", "al_loader_meta", "lib_version")))
        self.assertTrue(os.path.exists(self.p("app", "src", "main", "assets", "al_loader", "lib", "alloader.lua")))
        self.assertTrue(os.path.exists(self.p("app", "src", "main", "assets", "al_loader", "lib", "android.lua")))

        catalog_path = self.p("app", "src", "main", "assets", "flutter_assets", "assets", "al_scripts", "catalog.json")
        with open(catalog_path, "r", encoding="utf-8") as f:
            catalog = json.load(f)
        scripts = catalog.get("scripts", [])
        self.assertEqual(len(scripts), 7)
        for entry in scripts:
            script_file = self.p("app", "src", "main", "assets", "flutter_assets", "assets", "al_scripts", entry["file"])
            self.assertTrue(os.path.exists(script_file), f"Missing recommended script asset {script_file}")

        api_path = self.p("app", "src", "main", "assets", "flutter_assets", "assets", "al_docs", "al_api.json")
        with open(api_path, "r", encoding="utf-8") as f:
            api_doc = json.load(f)
        for k in ("globals", "events", "functions", "modules", "guides"):
            self.assertIn(k, api_doc)

    def test_parity_java_classes_and_dex_bridges(self):
        required_java = [
            "data/ThemeManager.java",
            "data/FontManager.java",
            "data/CrashLogHelper.java",
            "data/SettingsCatalogue.java",
            "data/AlLoaderManager.java",
            "data/ModLoaderManager.java",
            "data/ServerItem.java",
            "data/ServerRepository.java",
            "data/SettingsManager.java",
            "net/MasterlistClient.java",
            "provider/GameFilesDocumentsProvider.java",
            "service/DownloadService.java",
            "ui/JoinLinkActivity.java",
            "ui/CrosshairEditorDialog.java",
            "ui/FontPickerDialog.java",
            "ui/CodeEditorDialog.java",
            "ui/ThemeCustomizerDialog.java",
            "ui/ScreenshotGalleryDialog.java",
            "ui/LogsViewerDialog.java",
            "ui/HelpFaqChangelogDialogs.java",
            "ui/SettingsViewController.java",
            "ui/FilesWorkspaceController.java",
            "ui/ServerBrowserController.java",
            "ui/LauncherActivity.java",
        ]
        base = self.p("app", "src", "main", "java", "com", "rockstargames", "gtasa", "launcher")
        for rel in required_java:
            self.assertTrue(os.path.exists(os.path.join(base, rel)), f"Missing {rel}")

        with open(os.path.join(base, "net", "MasterlistClient.java"), "r", encoding="utf-8") as f:
            mc = f.read()
        self.assertIn("https://api.open.mp/servers", mc)
        self.assertIn("parseMasterlistJson", mc)

        dex_path = self.p("replica", "flutter_dex", "classes.dex")
        self.assertTrue(os.path.exists(dex_path))
        with open(dex_path, "rb") as f:
            dex_bytes = f.read()
        self.assertIn(b"Lcom/alyn/alloader/InputSystem;", dex_bytes)
        self.assertIn(b"Lcom/discord/socialsdk/", dex_bytes)

    def test_manifest_branding_and_zero_ads_trackers(self):
        manifest_path = self.p("app", "src", "main", "AndroidManifest.xml")
        with open(manifest_path, "r", encoding="utf-8") as f:
            manifest_raw = f.read()
        ET.fromstring(manifest_raw)

        for required in (
            "android.permission.RECORD_AUDIO",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
            "androidx.core.content.FileProvider",
            "com.rockstargames.gtasa.launcher.provider.GameFilesDocumentsProvider",
            "com.rockstargames.gtasa.launcher.service.DownloadService",
            "com.rockstargames.gtasa.launcher.ui.JoinLinkActivity",
            'android:scheme="romp"',
            'android:label="ROMP by HARFA"',
        ):
            self.assertIn(required, manifest_raw, f"Manifest missing {required}")

        for forbidden in ("applovin", "firebase", "google.android.gms.ads", "admob"):
            self.assertNotIn(forbidden, manifest_raw.lower(), f"Manifest contains forbidden tracker/ad {forbidden}")

        layout_path = self.p("app", "src", "main", "res", "layout", "activity_launcher.xml")
        with open(layout_path, "r", encoding="utf-8") as f:
            layout_raw = f.read()
        self.assertIn("ROMP by HARFA", layout_raw)
        self.assertNotIn("ReSA Multiplayer", layout_raw)
        self.assertNotIn("Designed & developed by Alyn", layout_raw)
        self.assertNotIn("Designed &amp; developed by Alyn", layout_raw)

    def test_ui_ux_pro_max_design_system_and_vector_icons(self):
        required_res = [
            "app/src/main/res/values/colors.xml",
            "app/src/main/res/values/dimens.xml",
            "app/src/main/res/values/styles.xml",
            "app/src/main/res/layout/item_spinner_romp.xml",
            "app/src/main/res/layout/item_spinner_dropdown_romp.xml",
            "app/src/main/res/drawable/bg_hero_scrim.xml",
            "app/src/main/res/drawable/ic_romp_home.xml",
            "app/src/main/res/drawable/ic_romp_globe.xml",
            "app/src/main/res/drawable/ic_romp_folder.xml",
            "app/src/main/res/drawable/ic_romp_settings.xml",
            "app/src/main/res/drawable/ic_romp_play.xml",
            "design-system/romp-by-harfa/MASTER.md",
        ]
        for rel in required_res:
            self.assertTrue(os.path.exists(self.p(*rel.split("/"))), f"Missing UI/UX Pro Max file: {rel}")

        for layout_rel in ("activity_launcher.xml", "item_server_card.xml"):
            with open(self.p("app", "src", "main", "res", "layout", layout_rel), "r", encoding="utf-8") as f:
                xml = f.read()
            for emoji in ("🏠", "🌐", "📦", "⚙️", "🎨", "⚠️", "🔥", "❔", "📋", "📷", "ℹ️", "🕹️", "🪵", "🔒"):
                self.assertNotIn(emoji, xml, f"Structural emoji {emoji} should use VectorDrawable in {layout_rel}")

    def test_custom_romp_logos_replaced_everywhere(self):
        emblem_res = self.p("app", "src", "main", "res", "drawable", "romp_emblem.png")
        resa_emb_res = self.p("app", "src", "main", "res", "drawable", "resa_emblem.png")
        resa_emb_flt = self.p("app", "src", "main", "assets", "flutter_assets", "assets", "brand", "resa_emblem.png")
        resa_logo_res = self.p("app", "src", "main", "res", "drawable", "resa_logo.png")
        resa_logo_flt = self.p("app", "src", "main", "assets", "flutter_assets", "assets", "brand", "resa_logo.png")
        master_flt = self.p("app", "src", "main", "assets", "flutter_assets", "assets", "brand", "resa_icon_master.png")
        logo_webp = self.p("app", "src", "main", "assets", "flutter_assets", "assets", "logo.webp")

        for p in (
            emblem_res,
            resa_emb_res,
            resa_emb_flt,
            resa_logo_res,
            resa_logo_flt,
            master_flt,
            logo_webp,
            self.p("app", "src", "main", "res", "mipmap-anydpi-v26", "ic_launcher_romp.xml"),
            self.p("app", "src", "main", "res", "mipmap-xxxhdpi", "ic_launcher_romp.png"),
            self.p("app", "src", "main", "res", "drawable-nodpi", "ic_romp_adaptive_fg.png"),
        ):
            self.assertTrue(os.path.exists(p), f"Missing ROMP logo asset: {p}")

        with open(resa_emb_res, "rb") as f1, open(resa_emb_flt, "rb") as f2:
            self.assertEqual(f1.read(), f2.read())
        with open(resa_logo_res, "rb") as f1, open(resa_logo_flt, "rb") as f2:
            self.assertEqual(f1.read(), f2.read())
        with open(logo_webp, "rb") as fw:
            hdr = fw.read(12)
            self.assertTrue(hdr.startswith(b"RIFF") and b"WEBP" in hdr)

    def test_loading_screen_romp_branding_and_background(self):
        bg_path = self.p("app", "src", "main", "assets", "flutter_assets", "assets", "bg", "resa_coast_loading.png")
        self.assertTrue(os.path.exists(bg_path), "Missing resa_coast_loading.png")
        with open(bg_path, "rb") as f:
            hdr = f.read(24)
        self.assertTrue(hdr.startswith(b"\x89PNG\r\n\x1a\n"))
        width, height = struct.unpack(">II", hdr[16:24])
        self.assertEqual((width, height), (1672, 941))

        for abi in ("arm64-v8a", "armeabi-v7a", "x86_64"):
            app_so = self.p("app", "src", "main", "jniLibs", abi, "libapp.so")
            with open(app_so, "rb") as f:
                data = f.read()
            self.assertIn(b"ROMP by HARFA SA | v", data, f"Missing ROMP footer in {abi}/libapp.so")
            self.assertIn(b"BY HARFA   ", data, f"Missing BY HARFA subtitle in {abi}/libapp.so")
            self.assertNotIn(b"ReSA Multiplayer | v", data, f"Stale ReSA footer remained in {abi}/libapp.so")


if __name__ == "__main__":
    unittest.main()
