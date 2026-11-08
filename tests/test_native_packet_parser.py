import os
import re
import unittest

class TestNativeCefBridge(unittest.TestCase):
    def test_cpp_files_exist(self):
        self.assertTrue(os.path.exists("jni/cef/cef_net.h"))
        self.assertTrue(os.path.exists("jni/cef/cef_net.cpp"))
        self.assertTrue(os.path.exists("jni/util/CJavaWrapper.h"))
        self.assertTrue(os.path.exists("jni/util/CJavaWrapper.cpp"))

    def test_cef_net_packet_handling(self):
        with open("jni/cef/cef_net.h", "r", encoding="utf-8") as f:
            header_code = f.read()
        with open("jni/cef/cef_net.cpp", "r", encoding="utf-8") as f:
            code = f.read()

        # Packet 215 constant & sub-IDs in header
        self.assertIn("CEF_PACKET_ID 215", header_code)
        self.assertIn("CEF_SUBID_LOAD_URL", header_code)
        self.assertIn("CEF_SUBID_EMIT_CLIENT", header_code)
        self.assertIn("CEF_SUBID_TOGGLE_FOCUS", header_code)
        self.assertIn("CEF_SUBID_SET_VISIBILITY", header_code)

        # Buffer overflow protections
        self.assertIn("urlLen", code)
        self.assertIn("evLen", code)
        self.assertIn("dataLen", code)

        # Dispatch calls
        self.assertIn("CefLoadUrl", code)
        self.assertIn("CefEmitClient", code)
        self.assertIn("CefToggleFocus", code)
        self.assertIn("CefSetVisibility", code)

    def test_java_wrapper_jni_bindings(self):
        with open("jni/util/CJavaWrapper.cpp", "r", encoding="utf-8") as f:
            code = f.read()

        # JNI export matching CefManager
        self.assertIn("Java_com_rockstargames_gtasa_core_CefManager_nativeSendCefEvent", code)
        self.assertIn("CEF_SUBID_EMIT_SERVER", code)
        self.assertIn("pNetGame->GetRakClient()->Send", code)

if __name__ == '__main__':
    unittest.main()
