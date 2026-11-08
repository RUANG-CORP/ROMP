import os
import unittest

REQUIRED_FILES = [
    "data/gta.dat",
    "texdb/gta3.img",
    "texdb/gta_int.img"
]

def verify_cache_directory(root_dir: str) -> list:
    missing = []
    for rf in REQUIRED_FILES:
        path = os.path.join(root_dir, rf.replace('/', os.sep))
        if not os.path.exists(path):
            missing.append(rf)
    return missing

class TestCacheVerifier(unittest.TestCase):
    def test_verifier_logic(self):
        missing = verify_cache_directory("non_existent_folder_abc")
        self.assertEqual(len(missing), 3)

    def test_java_file_exists(self):
        self.assertTrue(os.path.exists("app/src/main/java/com/rockstargames/gtasa/launcher/data/CacheVerifier.java"))

if __name__ == '__main__':
    unittest.main()

