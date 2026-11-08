#include "CJavaWrapper.h"
#include "../cef/cef_net.h"
#include <cstring>
#include <cstdlib>

// Forward declarations for NetGame / RakClient mocking & integration
class RakClientInterface {
public:
    virtual bool Send(const void *data, int length, int priority, int reliability, char orderingChannel) = 0;
};

class CNetGame {
public:
    RakClientInterface* GetRakClient() { return m_pRakClient; }
    void SetRakClient(RakClientInterface *client) { m_pRakClient = client; }
private:
    RakClientInterface *m_pRakClient = nullptr;
};

CJavaWrapper *pJavaWrapper = nullptr;
bool g_bCefInputActive = false;
CNetGame *pNetGame = nullptr;

void CJavaWrapper::InitSubsystem(JNIEnv *env, jobject activity) {
    if (!pJavaWrapper && env && activity) {
        pJavaWrapper = new CJavaWrapper(env, activity);
    }
}

CJavaWrapper::CJavaWrapper(JNIEnv *env, jobject activity) {
    env->GetJavaVM(&m_pVM);
    m_activityObj = env->NewGlobalRef(activity);

    jclass localClass = env->FindClass("com/rockstargames/gtasa/core/CefManager");
    if (localClass) {
        m_cefManagerClass = (jclass)env->NewGlobalRef(localClass);
        m_midGetInstance = env->GetStaticMethodID(m_cefManagerClass, "getInstance", "()Lcom/rockstargames/gtasa/core/CefManager;");
        m_midLoadUrl = env->GetMethodID(m_cefManagerClass, "loadUrl", "(Ljava/lang/String;Z)V");
        m_midEmitClient = env->GetMethodID(m_cefManagerClass, "emitClient", "(Ljava/lang/String;Ljava/lang/String;)V");
        m_midToggleFocus = env->GetMethodID(m_cefManagerClass, "toggleFocus", "(Z)V");
        m_midSetVisibility = env->GetMethodID(m_cefManagerClass, "setVisibility", "(Z)V");
        env->DeleteLocalRef(localClass);
    }
}

CJavaWrapper::~CJavaWrapper() {
    JNIEnv *env = GetEnv();
    if (env) {
        if (m_activityObj) env->DeleteGlobalRef(m_activityObj);
        if (m_cefManagerClass) env->DeleteGlobalRef(m_cefManagerClass);
    }
}

JNIEnv* CJavaWrapper::GetEnv() {
    JNIEnv *env = nullptr;
    if (m_pVM) {
        jint res = m_pVM->GetEnv((void**)&env, JNI_VERSION_1_6);
        if (res == JNI_EDETACHED) {
            // Attach native thread (e.g. RakNet worker thread) to JVM
            if (m_pVM->AttachCurrentThread(&env, nullptr) != JNI_OK) {
                return nullptr;
            }
        }
    }
    return env;
}

void CJavaWrapper::CefLoadUrl(const char *url, bool isFocused) {
    JNIEnv *env = GetEnv();
    if (!env || !m_cefManagerClass || !m_midGetInstance || !m_midLoadUrl) return;

    jobject instance = env->CallStaticObjectMethod(m_cefManagerClass, m_midGetInstance);
    if (!instance) return;

    jstring jUrl = env->NewStringUTF(url ? url : "");
    env->CallVoidMethod(instance, m_midLoadUrl, jUrl, (jboolean)isFocused);
    env->DeleteLocalRef(jUrl);
    env->DeleteLocalRef(instance);
}

void CJavaWrapper::CefEmitClient(const char *eventName, const char *jsonPayload) {
    JNIEnv *env = GetEnv();
    if (!env || !m_cefManagerClass || !m_midGetInstance || !m_midEmitClient) return;

    jobject instance = env->CallStaticObjectMethod(m_cefManagerClass, m_midGetInstance);
    if (!instance) return;

    jstring jEvent = env->NewStringUTF(eventName ? eventName : "");
    jstring jJson = env->NewStringUTF(jsonPayload ? jsonPayload : "{}");

    env->CallVoidMethod(instance, m_midEmitClient, jEvent, jJson);

    env->DeleteLocalRef(jEvent);
    env->DeleteLocalRef(jJson);
    env->DeleteLocalRef(instance);
}

void CJavaWrapper::CefToggleFocus(bool isInteractive) {
    g_bCefInputActive = isInteractive;

    JNIEnv *env = GetEnv();
    if (!env || !m_cefManagerClass || !m_midGetInstance || !m_midToggleFocus) return;

    jobject instance = env->CallStaticObjectMethod(m_cefManagerClass, m_midGetInstance);
    if (!instance) return;

    env->CallVoidMethod(instance, m_midToggleFocus, (jboolean)isInteractive);
    env->DeleteLocalRef(instance);
}

void CJavaWrapper::CefSetVisibility(bool isVisible) {
    JNIEnv *env = GetEnv();
    if (!env || !m_cefManagerClass || !m_midGetInstance || !m_midSetVisibility) return;

    jobject instance = env->CallStaticObjectMethod(m_cefManagerClass, m_midGetInstance);
    if (!instance) return;

    env->CallVoidMethod(instance, m_midSetVisibility, (jboolean)isVisible);
    env->DeleteLocalRef(instance);
}

// JNI Entry point for native initialization from Java
extern "C"
JNIEXPORT void JNICALL
Java_com_rockstargames_gtasa_core_CefManager_nativeInit(JNIEnv *env, jobject thiz, jobject activity) {
    CJavaWrapper::InitSubsystem(env, activity);
}

// JNI Bridge Method implementation called from Java AndroidBridge
extern "C"
JNIEXPORT void JNICALL
Java_com_rockstargames_gtasa_core_CefManager_nativeSendCefEvent(JNIEnv *env, jobject thiz, jstring event, jstring json) {
    if (!pNetGame || !pNetGame->GetRakClient()) return;
    if (!event || !json) return;

    const char *szEvent = env->GetStringUTFChars(event, nullptr);
    const char *szJson = env->GetStringUTFChars(json, nullptr);

    uint16_t evLen = (uint16_t)strlen(szEvent);
    uint16_t jsonLen = (uint16_t)strlen(szJson);

    // Format Packet 215 Sub-ID 4
    size_t totalPacketSize = sizeof(uint8_t) + sizeof(uint8_t) + sizeof(uint16_t) + evLen + sizeof(uint16_t) + jsonLen;
    uint8_t *packetBuffer = (uint8_t*)malloc(totalPacketSize);
    if (packetBuffer) {
        size_t offset = 0;
        packetBuffer[offset++] = (uint8_t)CEF_PACKET_ID;
        packetBuffer[offset++] = (uint8_t)CEF_SUBID_EMIT_SERVER;

        memcpy(&packetBuffer[offset], &evLen, sizeof(uint16_t));
        offset += sizeof(uint16_t);
        memcpy(&packetBuffer[offset], szEvent, evLen);
        offset += evLen;

        memcpy(&packetBuffer[offset], &jsonLen, sizeof(uint16_t));
        offset += sizeof(uint16_t);
        memcpy(&packetBuffer[offset], szJson, jsonLen);
        offset += jsonLen;

        // Send reliable ordered packet via RakNet (priority 1 = HIGH_PRIORITY, reliability 2 = RELIABLE_ORDERED)
        pNetGame->GetRakClient()->Send(packetBuffer, (int)totalPacketSize, 1, 2, 0);
        free(packetBuffer);
    }

    env->ReleaseStringUTFChars(event, szEvent);
    env->ReleaseStringUTFChars(json, szJson);
}
