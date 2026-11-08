import json
import os
import re
import unittest

def validate_nickname(name: str) -> bool:
    if not name or len(name) < 3 or len(name) > 20:
        return False
    return bool(re.match(r'^[a-zA-Z0-9_\[\]\$\@\.\=\?]+$', name))

def generate_settings_ini(nickname: str, ip: str, port: int, fps_limit: int, fast_chat: bool) -> str:
    return (
        f"[client]\n"
        f"name = {nickname}\n"
        f"server = {ip}\n"
        f"port = {port}\n"
        f"fps_limit = {fps_limit}\n"
        f"fast_chat = {1 if fast_chat else 0}\n"
    )

class TestSettingsAndServerModels(unittest.TestCase):
    def test_nickname_rules(self):
        self.assertTrue(validate_nickname("Valleta_RP"))
        self.assertTrue(validate_nickname("[ID]Player"))
        self.assertFalse(validate_nickname("ab"))
        self.assertFalse(validate_nickname("Invalid Player Name"))

    def test_settings_ini_generation(self):
        ini = generate_settings_ini("Player_One", "127.0.0.1", 7777, 90, True)
        self.assertIn("name = Player_One", ini)
        self.assertIn("server = 127.0.0.1", ini)
        self.assertIn("port = 7777", ini)
        self.assertIn("fps_limit = 90", ini)
        self.assertIn("fast_chat = 1", ini)

    def test_java_files_exist(self):
        self.assertTrue(os.path.exists("app/src/main/java/com/rockstargames/gtasa/launcher/data/ServerItem.java"))
        self.assertTrue(os.path.exists("app/src/main/java/com/rockstargames/gtasa/launcher/data/ServerRepository.java"))
        self.assertTrue(os.path.exists("app/src/main/java/com/rockstargames/gtasa/launcher/data/SettingsManager.java"))

if __name__ == '__main__':
    unittest.main()
