package com.rockstargames.gtasa.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.util.regex.Pattern;

public class SettingsManager {
    private static final String TAG = "SettingsManager";
    private static final String PREF_NAME = "samp_settings_pref";

    private static final String KEY_NICKNAME = "player_nickname";
    private static final String KEY_FPS_LIMIT = "fps_limit";
    private static final String KEY_FAST_CHAT = "fast_chat";

    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\[\\]\\$\\@\\.\\=\\?]{3,20}$");

    private final SharedPreferences mPrefs;

    public SettingsManager(Context context) {
        mPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isValidNickname(String name) {
        if (name == null) return false;
        return NICKNAME_PATTERN.matcher(name).matches();
    }

    public String getNickname() {
        return mPrefs.getString(KEY_NICKNAME, "Player_Valleta");
    }

    public void setNickname(String nickname) {
        mPrefs.edit().putString(KEY_NICKNAME, nickname).apply();
    }

    public int getFpsLimit() {
        return mPrefs.getInt(KEY_FPS_LIMIT, 90);
    }

    public void setFpsLimit(int fps) {
        mPrefs.edit().putInt(KEY_FPS_LIMIT, fps).apply();
    }

    public boolean isFastChat() {
        return mPrefs.getBoolean(KEY_FAST_CHAT, true);
    }

    public void setFastChat(boolean fastChat) {
        mPrefs.edit().putBoolean(KEY_FAST_CHAT, fastChat).apply();
    }

    /**
     * Synchronizes current player and server configuration to /files/SAMP/settings.ini
     */
    public boolean syncToNativeSettings(ServerItem server, File externalFilesDir) {
        if (externalFilesDir == null || server == null) return false;
        try {
            File sampDir = new File(externalFilesDir, "SAMP");
            if (!sampDir.exists()) {
                sampDir.mkdirs();
            }

            File iniFile = new File(sampDir, "settings.ini");
            String content = "[client]\n" +
                    "name = " + getNickname() + "\n" +
                    "server = " + server.getIp() + "\n" +
                    "port = " + server.getPort() + "\n" +
                    "fps_limit = " + getFpsLimit() + "\n" +
                    "fast_chat = " + (isFastChat() ? 1 : 0) + "\n" +
                    "cef_overlay = 1\n";

            try (FileWriter writer = new FileWriter(iniFile, false)) {
                writer.write(content);
            }
            Log.i(TAG, "Successfully synced settings.ini for server " + server.getAddress());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to sync settings.ini: " + e.getMessage());
            return false;
        }
    }
}

