import os
import unittest

def verify_cache_directory(root_dir: str) -> list:
    missing = []
    has_gta_dat = os.path.exists(os.path.join(root_dir, "data", "gta.dat")) or os.path.exists(os.path.join(root_dir, "SAMP", "gta.dat"))
    if not has_gta_dat:
        missing.append("data/gta.dat")
    
    has_modern_tex = os.path.exists(os.path.join(root_dir, "textures", "game.bin")) or os.path.exists(os.path.join(root_dir, "textures", "game.img"))
    has_legacy_tex = os.path.exists(os.path.join(root_dir, "texdb", "gta3.img")) or os.path.exists(os.path.join(root_dir, "texdb", "gta_int.img"))
    if not has_modern_tex and not has_legacy_tex:
        missing.append("textures/game.bin (atau texdb/gta3.img)")

    return missing

class TestCacheVerifier(unittest.TestCase):
    def test_verifier_logic_empty_dir(self):
        missing = verify_cache_directory("non_existent_folder_abc")
        self.assertEqual(len(missing), 2)

    def test_verifier_logic_modern_structure(self):
        import tempfile
        with tempfile.TemporaryDirectory() as td:
            os.makedirs(os.path.join(td, "data"))
            os.makedirs(os.path.join(td, "textures"))
            with open(os.path.join(td, "data", "gta.dat"), "w") as f: f.write("test")
            with open(os.path.join(td, "textures", "game.bin"), "w") as f: f.write("test")
            
            missing = verify_cache_directory(td)
            self.assertEqual(len(missing), 0)

    def test_java_file_exists_and_has_features(self):
        java_path = "app/src/main/java/com/rockstargames/gtasa/launcher/data/CacheVerifier.java"
        self.assertTrue(os.path.exists(java_path))
        with open(java_path, "r", encoding="utf-8") as f:
            content = f.read()
        self.assertIn("findResaCacheDir", content)
        self.assertIn("checkGameData", content)
        self.assertIn("CacheStatus", content)
        self.assertIn("calcDirStats", content)
        self.assertIn("getFolderBreakdown", content)
        self.assertIn("FolderInfo", content)

if __name__ == '__main__':
    unittest.main()
