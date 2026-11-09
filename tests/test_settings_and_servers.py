import json
import os
import re
import unittest
from urllib.parse import urlparse, parse_qs

def validate_nickname(name: str) -> bool:
    if not name or len(name) < 3 or len(name) > 20:
        return False
    return bool(re.match(r'^[a-zA-Z0-9_\[\]\$\@\.\=\?]+$', name))

def generate_settings_ini(nickname: str, ip: str, port: int, fps_limit: int, fast_chat: bool, voice_chat: bool = True) -> str:
    return (
        f"[client]\n"
        f"name = {nickname}\n"
        f"server = {ip}\n"
        f"port = {port}\n"
        f"fps_limit = {fps_limit}\n"
        f"fast_chat = {1 if fast_chat else 0}\n"
        f"voice_chat = {1 if voice_chat else 0}\n"
        f"cef_overlay = 1\n"
    )

def generate_client_settings_json(nickname: str, ip: str, port: int, server_name: str, fps_limit: int = 90, fast_chat: bool = True, voice_chat: bool = True) -> str:
    data = {
        "nick_name": nickname,
        "server_ip": ip,
        "server_port": port,
        "server_password": "",
        "offline_mode": False,
        "last_server_name": server_name,
        "fps_limit": fps_limit,
        "fast_chat": fast_chat,
        "voice_chat": voice_chat
    }
    return json.dumps(data, indent=2)

def generate_resa_connect_uri(ip: str, port: int) -> str:
    return f"resa://connect?ip={ip}&port={port}"

class TestSettingsAndServerModels(unittest.TestCase):
    def test_nickname_rules(self):
        self.assertTrue(validate_nickname("Valleta_RP"))
        self.assertTrue(validate_nickname("[ID]Player"))
        self.assertFalse(validate_nickname("ab"))
        self.assertFalse(validate_nickname("Invalid Player Name"))

    def test_settings_ini_generation(self):
        ini = generate_settings_ini("Player_One", "127.0.0.1", 7777, 90, True, True)
        self.assertIn("name = Player_One", ini)
        self.assertIn("server = 127.0.0.1", ini)
        self.assertIn("port = 7777", ini)
        self.assertIn("fps_limit = 90", ini)
        self.assertIn("fast_chat = 1", ini)
        self.assertIn("voice_chat = 1", ini)
        self.assertIn("cef_overlay = 1", ini)

    def test_client_settings_json_generation(self):
        raw = generate_client_settings_json("Valleta_Dev", "185.239.237.91", 7777, "Life of Player")
        data = json.loads(raw)
        self.assertEqual(data["nick_name"], "Valleta_Dev")
        self.assertEqual(data["server_ip"], "185.239.237.91")
        self.assertEqual(data["server_port"], 7777)
        self.assertEqual(data["last_server_name"], "Life of Player")
        self.assertFalse(data["offline_mode"])
        self.assertEqual(data["fps_limit"], 90)

    def test_resa_connect_uri_parsing(self):
        uri = generate_resa_connect_uri("185.239.237.91", 7777)
        parsed = urlparse(uri)
        self.assertEqual(parsed.scheme, "resa")
        self.assertEqual(parsed.netloc, "connect")
        qs = parse_qs(parsed.query)
        self.assertEqual(qs["ip"][0], "185.239.237.91")
        self.assertEqual(qs["port"][0], "7777")

    def test_java_files_exist(self):
        self.assertTrue(os.path.exists("app/src/main/java/com/rockstargames/gtasa/launcher/data/ServerItem.java"))
        self.assertTrue(os.path.exists("app/src/main/java/com/rockstargames/gtasa/launcher/data/ServerRepository.java"))
        self.assertTrue(os.path.exists("app/src/main/java/com/rockstargames/gtasa/launcher/data/SettingsManager.java"))
        self.assertTrue(os.path.exists("app/src/main/java/com/rockstargames/gtasa/core/GameViewportView.java"))
        self.assertTrue(os.path.exists("app/src/main/java/com/rockstargames/gtasa/GTASA.java"))

if __name__ == '__main__':
    unittest.main()
