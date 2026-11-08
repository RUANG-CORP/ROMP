import struct
import unittest

PACKET_CEF = 215

CEF_SUBID_LOAD_URL = 1
CEF_SUBID_EMIT_CLIENT = 2
CEF_SUBID_TOGGLE_FOCUS = 3
CEF_SUBID_EMIT_SERVER = 4
CEF_SUBID_SET_VISIBILITY = 5

def pack_cef_load_url(url: str, is_focused: bool) -> bytes:
    url_bytes = url.encode('utf-8')
    # uint8 pid, uint8 subid, uint32 urlLen, char url[], uint8 focused
    return struct.pack('<BB I', PACKET_CEF, CEF_SUBID_LOAD_URL, len(url_bytes)) + url_bytes + struct.pack('<B', 1 if is_focused else 0)

def pack_cef_emit_client(event: str, json_data: str) -> bytes:
    ev_bytes = event.encode('utf-8')
    data_bytes = json_data.encode('utf-8')
    # uint8 pid, uint8 subid, uint16 evLen, char ev[], uint16 dataLen, char data[]
    return struct.pack('<BB H', PACKET_CEF, CEF_SUBID_EMIT_CLIENT, len(ev_bytes)) + ev_bytes + struct.pack('<H', len(data_bytes)) + data_bytes

def pack_cef_toggle_focus(interactive: bool) -> bytes:
    return struct.pack('<BB B', PACKET_CEF, CEF_SUBID_TOGGLE_FOCUS, 1 if interactive else 0)

def pack_cef_set_visibility(visible: bool) -> bytes:
    return struct.pack('<BB B', PACKET_CEF, CEF_SUBID_SET_VISIBILITY, 1 if visible else 0)

def pack_cef_emit_server(event: str, json_data: str) -> bytes:
    ev_bytes = event.encode('utf-8')
    data_bytes = json_data.encode('utf-8')
    return struct.pack('<BB H', PACKET_CEF, CEF_SUBID_EMIT_SERVER, len(ev_bytes)) + ev_bytes + struct.pack('<H', len(data_bytes)) + data_bytes

def unpack_cef_packet(data: bytes):
    pid, subid = struct.unpack_from('<BB', data, 0)
    offset = 2
    if subid == CEF_SUBID_LOAD_URL:
        url_len, = struct.unpack_from('<I', data, offset)
        offset += 4
        url = data[offset:offset+url_len].decode('utf-8')
        offset += url_len
        focused, = struct.unpack_from('<B', data, offset)
        return {'pid': pid, 'subid': subid, 'url': url, 'focused': bool(focused)}
    elif subid in (CEF_SUBID_EMIT_CLIENT, CEF_SUBID_EMIT_SERVER):
        ev_len, = struct.unpack_from('<H', data, offset)
        offset += 2
        event = data[offset:offset+ev_len].decode('utf-8')
        offset += ev_len
        data_len, = struct.unpack_from('<H', data, offset)
        offset += 2
        payload = data[offset:offset+data_len].decode('utf-8')
        return {'pid': pid, 'subid': subid, 'event': event, 'payload': payload}
    elif subid == CEF_SUBID_TOGGLE_FOCUS:
        interactive, = struct.unpack_from('<B', data, offset)
        return {'pid': pid, 'subid': subid, 'interactive': bool(interactive)}
    elif subid == CEF_SUBID_SET_VISIBILITY:
        visible, = struct.unpack_from('<B', data, offset)
        return {'pid': pid, 'subid': subid, 'visible': bool(visible)}
    raise ValueError(f"Unknown subid {subid}")

class TestPacketProtocol(unittest.TestCase):
    def test_verify_cef_mobile_inc_exists(self):
        # This will fail until server/pawno/include/cef_mobile.inc is created
        import os
        self.assertTrue(os.path.exists("server/pawno/include/cef_mobile.inc"), "cef_mobile.inc must exist")

    def test_load_url_serialization(self):
        pkt = pack_cef_load_url("package://cef/index.html", True)
        unpacked = unpack_cef_packet(pkt)
        self.assertEqual(unpacked['pid'], 215)
        self.assertEqual(unpacked['subid'], 1)
        self.assertEqual(unpacked['url'], "package://cef/index.html")
        self.assertTrue(unpacked['focused'])

    def test_emit_client_serialization(self):
        pkt = pack_cef_emit_client("hud:stats", '{"hp":100,"armor":50}')
        unpacked = unpack_cef_packet(pkt)
        self.assertEqual(unpacked['pid'], 215)
        self.assertEqual(unpacked['subid'], 2)
        self.assertEqual(unpacked['event'], "hud:stats")
        self.assertEqual(unpacked['payload'], '{"hp":100,"armor":50}')

    def test_toggle_focus_serialization(self):
        pkt = pack_cef_toggle_focus(True)
        unpacked = unpack_cef_packet(pkt)
        self.assertEqual(unpacked['subid'], 3)
        self.assertTrue(unpacked['interactive'])

    def test_set_visibility_serialization(self):
        pkt = pack_cef_set_visibility(False)
        unpacked = unpack_cef_packet(pkt)
        self.assertEqual(unpacked['subid'], 5)
        self.assertFalse(unpacked['visible'])

    def test_emit_server_serialization(self):
        pkt = pack_cef_emit_server("auth:login", '["admin","pass123"]')
        unpacked = unpack_cef_packet(pkt)
        self.assertEqual(unpacked['subid'], 4)
        self.assertEqual(unpacked['event'], "auth:login")
        self.assertEqual(unpacked['payload'], '["admin","pass123"]')

if __name__ == '__main__':
    unittest.main()
