#ifndef CJAVA_WRAPPER_H
#define CJAVA_WRAPPER_H

#include <jni.h>

class CJavaWrapper {
public:
    CJavaWrapper(JNIEnv *env, jobject activity);
    ~CJavaWrapper();

    void CefLoadUrl(const char *url, bool isFocused);
    void CefEmitClient(const char *eventName, const char *jsonPayload);
    void CefToggleFocus(bool isInteractive);
    void CefSetVisibility(bool isVisible);

private:
    JavaVM *m_pVM;
    jobject m_activityObj;
    jclass m_cefManagerClass;
    jmethodID m_midGetInstance;
    jmethodID m_midLoadUrl;
    jmethodID m_midEmitClient;
    jmethodID m_midToggleFocus;
    jmethodID m_midSetVisibility;

    JNIEnv *GetEnv();
};

extern CJavaWrapper *pJavaWrapper;

#endif // CJAVA_WRAPPER_H
