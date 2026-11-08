#include "cef_net.h"
#include "../util/CJavaWrapper.h"
#include <cstring>
#include <algorithm>

void ProcessCefPacket(const unsigned char *data, size_t length) {
    if (!data || length < 2) return;
    if (data[0] != CEF_PACKET_ID) return;
    if (!pJavaWrapper) return;

    uint8_t subId = data[1];
    size_t offset = 2;

    switch (subId) {
        case CEF_SUBID_LOAD_URL: {
            if (length - offset < sizeof(uint32_t)) return;
            uint32_t urlLen = 0;
            memcpy(&urlLen, &data[offset], sizeof(uint32_t));
            offset += sizeof(uint32_t);

            // Safe subtraction prevents 32-bit integer overflow wrap-around
            if (urlLen > CEF_MAX_URL_LEN || (length - offset - sizeof(uint8_t)) < urlLen) return;
            
            char url[CEF_MAX_URL_LEN + 1] = { 0 };
            memcpy(url, &data[offset], urlLen);
            url[urlLen] = '\0';
            offset += urlLen;

            uint8_t isFocused = 0;
            memcpy(&isFocused, &data[offset], sizeof(uint8_t));

            pJavaWrapper->CefLoadUrl(url, isFocused != 0);
            break;
        }

        case CEF_SUBID_EMIT_CLIENT: {
            if (length - offset < sizeof(uint16_t)) return;
            uint16_t evLen = 0;
            memcpy(&evLen, &data[offset], sizeof(uint16_t));
            offset += sizeof(uint16_t);

            if (evLen > CEF_MAX_EVENT_LEN || (length - offset - sizeof(uint16_t)) < evLen) return;
            char eventName[CEF_MAX_EVENT_LEN + 1] = { 0 };
            memcpy(eventName, &data[offset], evLen);
            eventName[evLen] = '\0';
            offset += evLen;

            uint16_t dataLen = 0;
            memcpy(&dataLen, &data[offset], sizeof(uint16_t));
            offset += sizeof(uint16_t);

            if (dataLen > CEF_MAX_DATA_LEN || (length - offset) < dataLen) return;
            char jsonPayload[CEF_MAX_DATA_LEN + 1] = { 0 };
            memcpy(jsonPayload, &data[offset], dataLen);
            jsonPayload[dataLen] = '\0';

            pJavaWrapper->CefEmitClient(eventName, jsonPayload);
            break;
        }

        case CEF_SUBID_TOGGLE_FOCUS: {
            if (length - offset < sizeof(uint8_t)) return;
            uint8_t isInteractive = 0;
            memcpy(&isInteractive, &data[offset], sizeof(uint8_t));

            pJavaWrapper->CefToggleFocus(isInteractive != 0);
            break;
        }

        case CEF_SUBID_SET_VISIBILITY: {
            if (length - offset < sizeof(uint8_t)) return;
            uint8_t isVisible = 0;
            memcpy(&isVisible, &data[offset], sizeof(uint8_t));

            pJavaWrapper->CefSetVisibility(isVisible != 0);
            break;
        }

        default:
            break;
    }
}
