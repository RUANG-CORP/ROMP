import os
import struct
import unittest

def create_samp_query_packet(ip_str: str, port: int, opcode: str) -> bytes:
    octets = [int(x) for x in ip_str.split('.')]
    header = b'SAMP' + bytes(octets) + struct.pack('<H', port) + opcode.encode('latin1')
    return header

def parse_samp_info_response(data: bytes):
    if len(data) < 11 or data[:4] != b'SAMP':
        return None
    offset = 11
    passworded = struct.unpack_from('<B', data, offset)[0] != 0
    offset += 1
    players, max_players = struct.unpack_from('<HH', data, offset)
    offset += 4
    name_len = struct.unpack_from('<I', data, offset)[0]
    offset += 4
    hostname = data[offset:offset+name_len].decode('latin1', errors='replace')
    offset += name_len
    mode_len = struct.unpack_from('<I', data, offset)[0]
    offset += 4
    gamemode = data[offset:offset+mode_len].decode('latin1', errors='replace')
    return {
        'passworded': passworded,
        'players': players,
        'max_players': max_players,
        'hostname': hostname,
        'gamemode': gamemode
    }

class TestSampQueryProtocol(unittest.TestCase):
    def test_query_packet_format(self):
        pkt = create_samp_query_packet('127.0.0.1', 7777, 'i')
        self.assertEqual(len(pkt), 11)
        self.assertEqual(pkt[:4], b'SAMP')
        self.assertEqual(pkt[4:8], bytes([127, 0, 0, 1]))
        self.assertEqual(struct.unpack('<H', pkt[8:10])[0], 7777)
        self.assertEqual(pkt[10:11], b'i')

    def test_info_response_parsing(self):
        resp = b'SAMP\x7f\x00\x00\x01\x1e\x61i\x00\x19\x00\x64\x00\x12\x00\x00\x00My Roleplay Server\x08\x00\x00\x00Roleplay'
        info = parse_samp_info_response(resp)
        self.assertIsNotNone(info)
        self.assertFalse(info['passworded'])
        self.assertEqual(info['players'], 25)
        self.assertEqual(info['max_players'], 100)
        self.assertEqual(info['hostname'], 'My Roleplay Server')
        self.assertEqual(info['gamemode'], 'Roleplay')

    def test_java_files_exist(self):
        self.assertTrue(os.path.exists("app/src/main/java/com/rockstargames/gtasa/launcher/net/ServerInfo.java"))
        self.assertTrue(os.path.exists("app/src/main/java/com/rockstargames/gtasa/launcher/net/SampQueryClient.java"))

if __name__ == '__main__':
    unittest.main()
