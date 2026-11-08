#ifndef CEF_NET_H
#define CEF_NET_H

#include <stdint.h>
#include <stddef.h>

#define CEF_PACKET_ID 215

#define CEF_SUBID_LOAD_URL 1
#define CEF_SUBID_EMIT_CLIENT 2
#define CEF_SUBID_TOGGLE_FOCUS 3
#define CEF_SUBID_EMIT_SERVER 4
#define CEF_SUBID_SET_VISIBILITY 5

#define CEF_MAX_URL_LEN 512
#define CEF_MAX_EVENT_LEN 128
#define CEF_MAX_DATA_LEN 8192

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Intercepts and parses incoming RakNet Packet 215 from server.
 * Dispatches parsed data to Java CefManager on UI thread.
 */
void ProcessCefPacket(const unsigned char *data, size_t length);

#ifdef __cplusplus
}
#endif

#endif // CEF_NET_H
