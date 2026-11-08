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
            if (offset + sizeof(uint32_t) > length) return;
            uint32_t urlLen = 0;
            memcpy(&urlLen, &data[offset], sizeof(uint32_t));
            offset += sizeof(uint32_t);

            if (offset + urlLen + sizeof(uint8_t) > length) return;
            
            // Protect buffer from overflow
            char url[CEF_MAX_URL_LEN + 1] = { 0 };
            size_t copyLen = std::min((size_t)urlLen, (size_t)CEF_MAX_URL_LEN);
            memcpy(url, &data[offset], copyLen);
            url[copyLen] = '\0';
            offset += urlLen;

            uint8_t isFocused = 0;
            memcpy(&isFocused, &data[offset], sizeof(uint8_t));

            pJavaWrapper->CefLoadUrl(url, isFocused != 0);
            break;
        }

        case CEF_SUBID_EMIT_CLIENT: {
            if (offset + sizeof(uint16_t) > length) return;
            uint16_t evLen = 0;
            memcpy(&evLen, &data[offset], sizeof(uint16_t));
            offset += sizeof(uint16_t);

            if (offset + evLen + sizeof(uint16_t) > length) return;
            char eventName[CEF_MAX_EVENT_LEN + 1] = { 0 };
            size_t copyEvLen = std::min((size_t)evLen, (size_t)CEF_MAX_EVENT_LEN);
            memcpy(eventName, &data[offset], copyEvLen);
            eventName[copyEvLen] = '\0';
            offset += evLen;

            uint16_t dataLen = 0;
            memcpy(&dataLen, &data[offset], sizeof(uint16_t));
            offset += sizeof(uint16_t);

            if (offset + dataLen > length) return;
            char jsonPayload[CEF_MAX_DATA_LEN + 1] = { 0 };
            size_t copyDataLen = std::min((size_t)dataLen, (size_t)CEF_MAX_DATA_LEN);
            memcpy(jsonPayload, &data[offset], copyDataLen);
            jsonPayload[copyDataLen] = '\0';

            pJavaWrapper->CefEmitClient(eventName, jsonPayload);
            break;
        }

        case CEF_SUBID_TOGGLE_FOCUS: {
            if (offset + sizeof(uint8_t) > length) return;
            uint8_t isInteractive = 0;
            memcpy(&isInteractive, &data[offset], sizeof(uint8_t));

            pJavaWrapper->CefToggleFocus(isInteractive != 0);
            break;
        }

        case CEF_SUBID_SET_VISIBILITY: {
            if (offset + sizeof(uint8_t) > length) return;
            uint8_t isVisible = 0;
            memcpy(&isVisible, &data[offset], sizeof(uint8_t));

            pJavaWrapper->CefSetVisibility(isVisible != 0);
            break;
        }

        default:
            break;
    }
}
