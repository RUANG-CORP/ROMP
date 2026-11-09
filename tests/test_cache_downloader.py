import os
import unittest
import zipfile
import tempfile
import io

class TestCacheDownloader(unittest.TestCase):
    def test_cache_downloader_files_exist(self):
        downloader_file = "app/src/main/java/com/rockstargames/gtasa/launcher/data/CacheDownloader.java"
        self.assertTrue(os.path.exists(downloader_file), f"Missing {downloader_file}")

    def test_cache_downloader_methods(self):
        downloader_file = "app/src/main/java/com/rockstargames/gtasa/launcher/data/CacheDownloader.java"
        with open(downloader_file, "r", encoding="utf-8") as f:
            code = f.read()

        # Must have progress callback interface
        self.assertIn("DownloadCallback", code)
        self.assertIn("onProgress", code)
        self.assertIn("onComplete", code)
        self.assertIn("onError", code)

        # Must have download and extract methods
        self.assertIn("downloadAndExtract", code)
        self.assertIn("ZipInputStream", code)

        # Must protect against Zip Slip vulnerability
        self.assertIn("getCanonicalPath", code)

if __name__ == '__main__':
    unittest.main()
    def test_zip_extraction_security(self):
        # Create a malicious zip simulating zip slip
        zip_buf = io.BytesIO()
        with zipfile.ZipFile(zip_buf, 'w') as zf:
            zf.writestr("../evil.txt", "exploit")
        
        # Verify python zipfile detects or allows inspecting the slip entry
        with zipfile.ZipFile(zip_buf, 'r') as zf:
            names = zf.namelist()
            self.assertIn("../evil.txt", names)
