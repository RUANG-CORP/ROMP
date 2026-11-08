package com.rockstargames.gtasa;

import android.app.Activity;
import android.os.Bundle;
import android.widget.FrameLayout;

import com.rockstargames.gtasa.core.CefManager;

public class GTASA extends Activity {
    private FrameLayout mRootLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_overlay);

        mRootLayout = findViewById(R.id.game_root_layout);
        
        // Initialize CEF Overlay
        CefManager.getInstance().init(this, mRootLayout);

        // Load default local asset SPA
        CefManager.getInstance().loadUrl("package://cef/index.html", false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CefManager.getInstance().onDestroy();
    }
}

