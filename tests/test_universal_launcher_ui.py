import os
import unittest

class TestUniversalLauncherUI(unittest.TestCase):
    def test_ui_files_exist(self):
        required = [
            "app/src/main/java/com/rockstargames/gtasa/launcher/ui/ServerAdapter.java",
            "app/src/main/java/com/rockstargames/gtasa/launcher/ui/LauncherActivity.java",
            "app/src/main/res/layout/activity_launcher.xml",
            "app/src/main/res/layout/item_server_card.xml",
            "app/src/main/res/layout/dialog_add_server.xml"
        ]
        for rf in required:
            self.assertTrue(os.path.exists(rf), f"Missing UI file: {rf}")

    def test_no_ads_in_launcher_code(self):
        with open("app/src/main/java/com/rockstargames/gtasa/launcher/ui/LauncherActivity.java", "r", encoding="utf-8") as f:
            code = f.read()
        for ad_kw in ["admob", "unityads", "startapp", "ironsource", "interstitialad", "bannerad"]:
            self.assertNotIn(ad_kw, code.lower())

    def test_launcher_activity_structure(self):
        with open("app/src/main/java/com/rockstargames/gtasa/launcher/ui/LauncherActivity.java", "r", encoding="utf-8") as f:
            code = f.read()
        self.assertIn("ServerRepository", code)
        self.assertIn("SettingsManager", code)
        self.assertIn("CacheVerifier", code)
        self.assertIn("showAddServerDialog", code)
        self.assertIn("launchGameWithServer", code)
        self.assertIn("syncToNativeSettings", code)

    def test_server_adapter_structure(self):
        with open("app/src/main/java/com/rockstargames/gtasa/launcher/ui/ServerAdapter.java", "r", encoding="utf-8") as f:
            code = f.read()
        self.assertIn("BaseAdapter", code)
        self.assertIn("SampQueryClient", code)
        self.assertIn("OnServerActionListener", code)
        self.assertIn("item_server_card", code)

if __name__ == '__main__':
    unittest.main()
