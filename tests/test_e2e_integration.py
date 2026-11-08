import os
import unittest

class TestE2EIntegration(unittest.TestCase):
    def test_required_project_files_exist(self):
        required_files = [
            "build.gradle",
            "settings.gradle",
            "app/build.gradle",
            "app/src/main/AndroidManifest.xml",
            "app/src/main/java/com/rockstargames/gtasa/core/CefManager.java",
            "app/src/main/res/layout/activity_main_overlay.xml",
            "app/src/main/assets/cef/index.html",
            "app/src/main/assets/cef/cef.js",
            "app/src/main/assets/cef/style.css",
            "server/pawno/include/cef_mobile.inc",
            "jni/cef/cef_net.h",
            "jni/cef/cef_net.cpp",
            "jni/util/CJavaWrapper.h",
            "jni/util/CJavaWrapper.cpp",
            "jni/CMakeLists.txt",
            "README.md"
        ]
        for rf in required_files:
            self.assertTrue(os.path.exists(rf), f"Missing required file: {rf}")

    def test_dual_abi_filters_configured(self):
        with open("app/build.gradle", "r", encoding="utf-8") as f:
            gradle = f.read()
        self.assertIn("'armeabi-v7a'", gradle)
        self.assertIn("'arm64-v8a'", gradle)
        self.assertIn("androidx.webkit:webkit", gradle)

    def test_cmake_native_configuration(self):
        with open("jni/CMakeLists.txt", "r", encoding="utf-8") as f:
            cmake = f.read()
        self.assertIn("add_library", cmake)
        self.assertIn("cef_net.cpp", cmake)
        self.assertIn("CJavaWrapper.cpp", cmake)

    def test_server_and_client_subid_sync(self):
        with open("server/pawno/include/cef_mobile.inc", "r", encoding="utf-8") as f:
            pawn = f.read()
        with open("jni/cef/cef_net.h", "r", encoding="utf-8") as f:
            cpp = f.read()

        # Both must define Sub-IDs 1 to 5 identically
        for sub_id in [1, 2, 3, 4, 5]:
            self.assertIn(f"({sub_id})", pawn)
            self.assertIn(f" {sub_id}", cpp)

if __name__ == '__main__':
    unittest.main()
