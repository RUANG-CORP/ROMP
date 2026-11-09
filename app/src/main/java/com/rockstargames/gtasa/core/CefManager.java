package com.rockstargames.gtasa.core;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

/**
 * CefManager - High-Performance Chromium/WebView Overlay Manager for GTA SA-MP / open.mp Mobile.
 */
public class CefManager {
    private static final String TAG = "CefManager";
    private static volatile CefManager sInstance;

    static {
        try {
            System.loadLibrary("samp_cef_mobile");
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "samp_cef_mobile native library not found in standalone mode: " + e.getMessage());
        }
    }

    private Activity mActivity;
    private WebView mWebView;
    private WebViewAssetLoader mAssetLoader;
    private boolean mIsInteractive = false;
    private boolean mIsInitialized = false;

    // Native C++ JNI bridge methods
    private native void nativeInit(Activity activity);
    private native void nativeSendCefEvent(String event, String json);

    private CefManager() {}

    public static CefManager getInstance() {
        if (sInstance == null) {
            synchronized (CefManager.class) {
                if (sInstance == null) {
                    sInstance = new CefManager();
                }
            }
        }
        return sInstance;
    }

    /**
     * Initializes the CEF WebView inside the game activity's root layout.
     */
    public void init(@NonNull final Activity activity, @NonNull final FrameLayout rootLayout) {
        if (mIsInitialized && mActivity == activity) return;
        this.mActivity = activity;

        try {
            nativeInit(activity);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "nativeInit unsatisfied: " + e.getMessage());
        }

        activity.runOnUiThread(() -> {
            mAssetLoader = new WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(activity))
                    .build();

            mWebView = new WebView(activity);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            mWebView.setLayoutParams(params);

            // Hardware acceleration & transparency
            mWebView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            mWebView.setBackgroundColor(Color.TRANSPARENT);

            // WebSettings configuration
            WebSettings settings = mWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setAllowContentAccess(false);
            settings.setMediaPlaybackRequiresUserGesture(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                settings.setSafeBrowsingEnabled(false);
            }

            // Injected Javascript Interface
            mWebView.addJavascriptInterface(new CefAndroidBridge(), "AndroidBridge");

            // Setup Custom Scheme Interception Client
            mWebView.setWebViewClient(new WebViewClient() {
                @Nullable
                @Override
                public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                    Uri url = request.getUrl();
                    String urlStr = url.toString();

                    if (urlStr.startsWith("package://") || urlStr.startsWith("http://package/")) {
                        String cleanPath = urlStr.replaceFirst("^https?://package/?|^package://?", "");
                        Uri rewrittenUri = Uri.parse("https://appassets.androidplatform.net/assets/" + cleanPath);
                        return mAssetLoader.shouldInterceptRequest(rewrittenUri);
                    }

                    if ("appassets.androidplatform.net".equals(url.getHost())) {
                        return mAssetLoader.shouldInterceptRequest(url);
                    }

                    return super.shouldInterceptRequest(view, request);
                }

                @Override
                public void onPageFinished(WebView view, String url) {
                    super.onPageFinished(view, url);
                    Log.i(TAG, "CEF Page Loaded: " + url);
                }

                @Override
                public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                    Log.w(TAG, "CEF WebView render process terminated (didCrash=" + (detail != null && detail.didCrash()) + ")");
                    if (mWebView != null && mWebView.getParent() != null) {
                        ((ViewGroup) mWebView.getParent()).removeView(mWebView);
                        mWebView.destroy();
                        mWebView = null;
                    }
                    mIsInitialized = false;
                    return true;
                }
            });

            // Initial Touch State: Passive Pass-Through (false)
            setTouchState(false);

            // Add WebView over GLSurfaceView
            rootLayout.addView(mWebView);
            mIsInitialized = true;
            Log.i(TAG, "CefManager successfully initialized.");
        });
    }

    /**
     * Sub-ID 1: Loads a URL into the WebView.
     */
    public void loadUrl(final String url, final boolean isFocused) {
        if (mActivity == null) return;
        mActivity.runOnUiThread(() -> {
            if (mWebView != null) {
                String targetUrl = url;
                if (url.startsWith("package://") || url.startsWith("http://package/")) {
                    String cleanPath = url.replaceFirst("^https?://package/?|^package://?", "");
                    targetUrl = "https://appassets.androidplatform.net/assets/" + cleanPath;
                }
                mWebView.loadUrl(targetUrl);
                toggleFocus(isFocused);
            }
        });
    }

    /**
     * Sub-ID 2: Emits an event to the client JavaScript layer.
     */
    public void emitClient(final String eventName, final String jsonPayload) {
        if (mActivity == null) return;
        mActivity.runOnUiThread(() -> {
            if (mWebView != null) {
                String safePayload = (jsonPayload != null && !jsonPayload.isEmpty()) ? jsonPayload : "{}";
                // Pass raw json as properly escaped string parameter to window.cef._trigger
                String script = String.format("if (window.cef && window.cef._trigger) { window.cef._trigger('%s', %s); }",
                        escapeJsString(eventName),
                        JSONObject.quote(safePayload));
                mWebView.evaluateJavascript(script, null);
            }
        });
    }

    /**
     * Sub-ID 3: Toggles dynamic touch routing between passive HUD and interactive dialog.
     */
    public void toggleFocus(final boolean interactive) {
        if (mActivity == null) return;
        mActivity.runOnUiThread(() -> setTouchState(interactive));
    }

    /**
     * Sub-ID 5: Toggles WebView visibility.
     */
    public void setVisibility(final boolean visible) {
        if (mActivity == null) return;
        mActivity.runOnUiThread(() -> {
            if (mWebView != null) {
                mWebView.setVisibility(visible ? View.VISIBLE : View.GONE);
            }
        });
    }

    public void onDestroy() {
        if (mWebView != null) {
            mWebView.destroy();
            mWebView = null;
        }
        mIsInitialized = false;
        mActivity = null;
    }

    private void setTouchState(boolean interactive) {
        mIsInteractive = interactive;
        if (mWebView == null) return;

        if (interactive) {
            mWebView.setClickable(true);
            mWebView.setFocusable(true);
            mWebView.setFocusableInTouchMode(true);
            mWebView.setOnTouchListener(null);
            mWebView.requestFocus();
        } else {
            mWebView.setClickable(false);
            mWebView.setFocusable(false);
            mWebView.setFocusableInTouchMode(false);
            mWebView.setOnTouchListener((v, event) -> false);
            mWebView.clearFocus();
        }
    }

    private String escapeJsString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
    }

    public class CefAndroidBridge {
        @JavascriptInterface
        public void sendToServer(String event, String json) {
            try {
                nativeSendCefEvent(event, json);
            } catch (UnsatisfiedLinkError e) {
                Log.e(TAG, "nativeSendCefEvent native library not linked: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Error in sendToServer: " + e.getMessage());
            }
        }
    }
}
