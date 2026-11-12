package com.rockstargames.gtasa.launcher.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

/**
 * Handles external join links (romp://connect, resa://connect, https://resa-mp.com/join)
 * and delivers parsed connection parameters safely to LauncherActivity.
 */
public class JoinLinkActivity extends Activity {
    public static final String EXTRA_JOIN_HOST = "join_host";
    public static final String EXTRA_JOIN_PORT = "join_port";
    public static final String EXTRA_JOIN_NAME = "join_name";
    public static final String EXTRA_JOIN_PASSWORD = "join_password";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
        finish();
    }

    private void handleIntent(Intent incoming) {
        Intent target = new Intent(this, LauncherActivity.class);
        target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        Uri data = incoming != null ? incoming.getData() : null;
        if (data != null) {
            String host = firstNonEmpty(
                    data.getQueryParameter("ip"),
                    data.getQueryParameter("host"),
                    data.getQueryParameter("address"),
                    data.getQueryParameter("server")
            );
            int port = 7777;
            String portStr = data.getQueryParameter("port");
            if (!TextUtils.isEmpty(portStr)) {
                try {
                    int parsed = Integer.parseInt(portStr.trim());
                    if (parsed > 0 && parsed <= 65535) port = parsed;
                } catch (NumberFormatException ignored) {}
            } else if (!TextUtils.isEmpty(host) && host.contains(":")) {
                String[] parts = host.split(":", 2);
                host = parts[0];
                try {
                    int parsed = Integer.parseInt(parts[1].trim());
                    if (parsed > 0 && parsed <= 65535) port = parsed;
                } catch (NumberFormatException ignored) {}
            }

            if (!TextUtils.isEmpty(host)) {
                target.putExtra(EXTRA_JOIN_HOST, host.trim());
                target.putExtra(EXTRA_JOIN_PORT, port);
                String name = firstNonEmpty(data.getQueryParameter("name"), data.getQueryParameter("title"));
                String password = firstNonEmpty(data.getQueryParameter("password"), data.getQueryParameter("pass"));
                if (!TextUtils.isEmpty(name)) target.putExtra(EXTRA_JOIN_NAME, name.trim());
                if (!TextUtils.isEmpty(password)) target.putExtra(EXTRA_JOIN_PASSWORD, password);
            }
        }
        startActivity(target);
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (!TextUtils.isEmpty(v)) return v;
        }
        return null;
    }
}

