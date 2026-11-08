/**
 * ==============================================================================
 * cef_mobile.inc - Chromium Embedded Framework Mobile Subsystem for SA-MP & OMP
 * ==============================================================================
 * Compatibility: SA-MP 0.3.7, open.mp (OMP)
 * Networking: Pawn.RakNet Custom Packet ID 215 (0xD7)
 * ==============================================================================
 */

#if defined _cef_mobile_included
    #endinput
#endif
#define _cef_mobile_included

#if !defined _pawn_raknet_included
    #error "Please include <Pawn.RakNet> before <cef_mobile>"
#endif

// Packet ID Definition
#define CEF_PACKET_ID               (215)

// Sub-IDs
#define CEF_SUBID_LOAD_URL          (1)
#define CEF_SUBID_EMIT_CLIENT       (2)
#define CEF_SUBID_TOGGLE_FOCUS      (3)
#define CEF_SUBID_EMIT_SERVER       (4)
#define CEF_SUBID_SET_VISIBILITY    (5)

// Max Buffer Sizes
#define CEF_MAX_EVENT_NAME          (128)
#define CEF_MAX_PAYLOAD_SIZE        (4096)

// Forward callback declaration for server gamemodes
forward OnCefEvent(playerid, const event[], const json[]);

/**
 * Loads a URL into the client's WebView.
 * For local assets, url can be:
 *   "package://cef/index.html"
 *   "http://package/index.html"
 *   "https://appassets.androidplatform.net/assets/index.html"
 */
stock CEF_LoadUrl(playerid, const url[], bool:focused = false) {
    new BitStream:bs = BS_New();
    BS_WriteValue(bs,
        PR_UINT8, CEF_PACKET_ID,
        PR_UINT8, CEF_SUBID_LOAD_URL,
        PR_UINT32, strlen(url),
        PR_STRING, url,
        PR_UINT8, focused ? 1 : 0
    );
    PR_SendPacket(bs, playerid, PR_HIGH_PRIORITY, PR_RELIABLE_ORDERED);
    BS_Delete(bs);
    return 1;
}

/**
 * Emits an event to the client's JavaScript layer (triggers window.cef._trigger).
 */
stock CEF_Emit(playerid, const event[], const json[]) {
    new BitStream:bs = BS_New();
    BS_WriteValue(bs,
        PR_UINT8, CEF_PACKET_ID,
        PR_UINT8, CEF_SUBID_EMIT_CLIENT,
        PR_UINT16, strlen(event),
        PR_STRING, event,
        PR_UINT16, strlen(json),
        PR_STRING, json
    );
    PR_SendPacket(bs, playerid, PR_HIGH_PRIORITY, PR_RELIABLE_ORDERED);
    BS_Delete(bs);
    return 1;
}

/**
 * Toggles touch input focus between game controls and CEF WebView.
 *   interactive = false: touch pass-through (gamepad/camera active)
 *   interactive = true:  touch focus claimed by WebView (dialogs/inputs active)
 */
stock CEF_ToggleFocus(playerid, bool:interactive) {
    new BitStream:bs = BS_New();
    BS_WriteValue(bs,
        PR_UINT8, CEF_PACKET_ID,
        PR_UINT8, CEF_SUBID_TOGGLE_FOCUS,
        PR_UINT8, interactive ? 1 : 0
    );
    PR_SendPacket(bs, playerid, PR_HIGH_PRIORITY, PR_RELIABLE_ORDERED);
    BS_Delete(bs);
    return 1;
}

/**
 * Toggles client WebView visibility without destroying the DOM state.
 *   visible = false: View.GONE
 *   visible = true:  View.VISIBLE
 */
stock CEF_SetVisibility(playerid, bool:visible) {
    new BitStream:bs = BS_New();
    BS_WriteValue(bs,
        PR_UINT8, CEF_PACKET_ID,
        PR_UINT8, CEF_SUBID_SET_VISIBILITY,
        PR_UINT8, visible ? 1 : 0
    );
    PR_SendPacket(bs, playerid, PR_HIGH_PRIORITY, PR_RELIABLE_ORDERED);
    BS_Delete(bs);
    return 1;
}

/**
 * Packet handler for incoming client events (CEF_SUBID_EMIT_SERVER)
 */
IPacket:CEF_PACKET_ID(playerid, BitStream:bs) {
    new subid;
    BS_ReadUint8(bs, subid); // Read sub-ID
    
    if (subid == CEF_SUBID_EMIT_SERVER) {
        new event[CEF_MAX_EVENT_NAME], json[CEF_MAX_PAYLOAD_SIZE];
        new evLen = 0, jsonLen = 0;
        
        BS_ReadUint16(bs, evLen);
        if (evLen > sizeof(event) - 1) evLen = sizeof(event) - 1;
        BS_ReadString(bs, event, evLen);
        event[evLen] = '\0';
        
        BS_ReadUint16(bs, jsonLen);
        if (jsonLen > sizeof(json) - 1) jsonLen = sizeof(json) - 1;
        BS_ReadString(bs, json, jsonLen);
        json[jsonLen] = '\0';
        
        #if defined OnCefEvent
            return OnCefEvent(playerid, event, json);
        #else
            return 1;
        #endif
    }
    return 1;
}
