import os
import unittest
import xml.etree.ElementTree as ET

class TestUniversalE2E(unittest.TestCase):
    def setUp(self):
        self.manifest_path = "app/src/main/AndroidManifest.xml"
        self.build_gradle_path = "app/build.gradle"

    def test_launcher_is_main_entry(self):
        self.assertTrue(os.path.exists(self.manifest_path))
        tree = ET.parse(self.manifest_path)
        root = tree.getroot()
        app = root.find("application")
        self.assertIsNotNone(app)

        launcher_found = False
        gtasa_found = False

        for activity in app.findall("activity"):
            name = activity.attrib.get("{http://schemas.android.com/apk/res/android}name", "")
            if "LauncherActivity" in name:
                launcher_found = True
                # Must have MAIN and LAUNCHER intent filter
                has_main = False
                has_launcher = False
                for ifilter in activity.findall("intent-filter"):
                    for action in ifilter.findall("action"):
                        if action.attrib.get("{http://schemas.android.com/apk/res/android}name") == "android.intent.action.MAIN":
                            has_main = True
                    for cat in ifilter.findall("category"):
                        if cat.attrib.get("{http://schemas.android.com/apk/res/android}name") == "android.intent.category.LAUNCHER":
                            has_launcher = True
                self.assertTrue(has_main, "LauncherActivity must have action.MAIN")
                self.assertTrue(has_launcher, "LauncherActivity must have category.LAUNCHER")

            if "GTASA" in name:
                gtasa_found = True
                soft_input = activity.attrib.get("{http://schemas.android.com/apk/res/android}windowSoftInputMode")
                self.assertEqual(soft_input, "adjustNothing", "GTASA must have windowSoftInputMode='adjustNothing' to prevent EGL crashes")

        self.assertTrue(launcher_found, "LauncherActivity must be declared in AndroidManifest.xml")
        self.assertTrue(gtasa_found, "GTASA game activity must be declared in AndroidManifest.xml")

    def test_zero_ads_in_build_gradle(self):
        self.assertTrue(os.path.exists(self.build_gradle_path))
        with open(self.build_gradle_path, "r", encoding="utf-8") as f:
            gradle_content = f.read().lower()
        for ad_sdk in ["admob", "play-services-ads", "unity-ads", "startapp", "ironsource", "applovin"]:
            self.assertNotIn(ad_sdk, gradle_content, f"Prohibited ad SDK detected: {ad_sdk}")

if __name__ == '__main__':
    unittest.main()

