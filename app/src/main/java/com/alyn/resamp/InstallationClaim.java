package com.alyn.resamp;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides the installation claim metadata expected by {@code com.alyn.resa.Game.installationClaim()}.
 */
public final class InstallationClaim {
    public static final InstallationClaim INSTANCE = new InstallationClaim();

    private InstallationClaim() {}

    public Map<String, Object> of(Context context) {
        Map<String, Object> map = new HashMap<>();
        map.put("certSha256", "F9E241A19E9C952DC5B313C112534441B6EF2C20E53AFC8304A173EA39D5F621");
        map.put("packageName", "com.alyn.resamp");
        map.put("versionCode", 122L);
        map.put("modsEnabled", false);
        return map;
    }
}

