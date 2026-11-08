import os
import re
import unittest

class TestCefManagerJava(unittest.TestCase):
    def test_files_exist(self):
        self.assertTrue(os.path.exists("app/src/main/java/com/rockstargames/gtasa/core/CefManager.java"))
        self.assertTrue(os.path.exists("app/src/main/res/layout/activity_main_overlay.xml"))
        self.assertTrue(os.path.exists("app/src/main/AndroidManifest.xml"))

    def test_cef_manager_contract(self):
        with open("app/src/main/java/com/rockstargames/gtasa/core/CefManager.java", "r", encoding="utf-8") as f:
            code = f.read()

        # JNI declaration
        self.assertIn("native void nativeSendCefEvent", code)
        self.assertIn("@JavascriptInterface", code)
        self.assertIn("sendToServer", code)

        # Asset loader & scheme rewriting
        self.assertIn("WebViewAssetLoader", code)
        self.assertIn("package://", code)
        self.assertIn("shouldInterceptRequest", code)

        # Dynamic touch routing
        self.assertIn("setClickable", code)
        self.assertIn("setOnTouchListener", code)

        # UI thread scheduling
        self.assertIn("runOnUiThread", code)
        self.assertIn("evaluateJavascript", code)

    def test_manifest_anti_crash_config(self):
        with open("app/src/main/AndroidManifest.xml", "r", encoding="utf-8") as f:
            xml = f.read()

        # EGL surface resize protection
        self.assertIn('android:windowSoftInputMode="adjustNothing"', xml)
        # Hardware acceleration
        self.assertIn('android:hardwareAccelerated="true"', xml)

if __name__ == '__main__':
    unittest.main()

