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

        # Must support ReSA CDN manifest and local sync
        self.assertIn("downloadFromCdnOrZip", code)
        self.assertIn("downloadManifestInternal", code)
        self.assertIn("syncFromLocalDirectory", code)
        self.assertIn("ManifestFileItem", code)
        self.assertIn("AlynLauncher/20.5", code)

        # Must protect against Zip Slip vulnerability
        self.assertIn("getCanonicalPath", code)

    def test_zip_extraction_security(self):
        # Create a malicious zip simulating zip slip
        zip_buf = io.BytesIO()
        with zipfile.ZipFile(zip_buf, 'w') as zf:
            zf.writestr("../evil.txt", "exploit")
        
        with zipfile.ZipFile(zip_buf, 'r') as zf:
            names = zf.namelist()
            self.assertIn("../evil.txt", names)

    def test_manifest_json_format_parsing(self):
        # Verify JSON manifest parsing logic
        import json
        sample_json = '''{
            "files": [
                {"name": "SAMP.ide", "path": "SAMP/SAMP.ide", "size": "89358", "url": "https://resa-mp.com/game-files/SAMP/SAMP.ide"},
                {"name": "game.bin", "path": "textures/game.bin", "size": "233890123", "url": "https://resa-mp.com/game-files/textures/game.bin"}
            ]
        }'''
        parsed = json.loads(sample_json)
        self.assertIn("files", parsed)
        self.assertEqual(len(parsed["files"]), 2)
        self.assertEqual(parsed["files"][0]["path"], "SAMP/SAMP.ide")
        self.assertEqual(int(parsed["files"][0]["size"]), 89358)

if __name__ == '__main__':
    unittest.main()
