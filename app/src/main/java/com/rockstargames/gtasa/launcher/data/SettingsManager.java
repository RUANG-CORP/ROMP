package com.rockstargames.gtasa.launcher.data;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.util.regex.Pattern;

public class SettingsManager {
    private static final String TAG = "SettingsManager";
    private static final String PREF_NAME = "samp_settings_pref";

    public static final String ENGINE_AUTO = "auto";
    public static final String ENGINE_RESA = "resa";
    public static final String ENGINE_ROMP = "romp";

    private static final String KEY_NICKNAME = "player_nickname";
    private static final String KEY_FPS_LIMIT = "fps_limit";
    private static final String KEY_FAST_CHAT = "fast_chat";
    private static final String KEY_VOICE_CHAT = "voice_chat";
    private static final String KEY_GAME_ENGINE = "game_engine_mode";
    public static final String OFFICIAL_RESA_CDN_MANIFEST = "https://resa-mp.com/api/game-files";
    private static final String KEY_CACHE_URL = "cache_cdn_url";
    private static final String DEFAULT_CACHE_URL = OFFICIAL_RESA_CDN_MANIFEST;

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

    public boolean isVoiceChat() {
        return mPrefs.getBoolean(KEY_VOICE_CHAT, true);
    }

    public void setVoiceChat(boolean voiceChat) {
        mPrefs.edit().putBoolean(KEY_VOICE_CHAT, voiceChat).apply();
    }

    public String getGameEngine() {
        return mPrefs.getString(KEY_GAME_ENGINE, ENGINE_AUTO);
    }

    public void setGameEngine(String engine) {
        mPrefs.edit().putString(KEY_GAME_ENGINE, engine).apply();
    }

    public String getCacheUrl() {
        return mPrefs.getString(KEY_CACHE_URL, DEFAULT_CACHE_URL);
    }

    public void setCacheUrl(String url) {
        mPrefs.edit().putString(KEY_CACHE_URL, url).apply();
    }

    /**
     * Checks if official ReSA:MP application is installed on the system.
     */
    public static boolean isResaInstalled(Context context) {
        if (context == null) return false;
        try {
            context.getPackageManager().getPackageInfo("com.alyn.resamp", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Finds the external files directory for com.alyn.resamp if accessible.
     */
    public static File findResaFilesDir() {
        File resa = new File("/sdcard/Android/data/com.alyn.resamp/files");
        if (resa.exists() && resa.isDirectory()) {
            return resa;
        }
        File alt = new File("/storage/emulated/0/Android/data/com.alyn.resamp/files");
        if (alt.exists() && alt.isDirectory()) {
            return alt;
        }
        return null;
    }

    /**
     * Synchronizes player and server configuration to /files/SAMP/settings.ini and /files/client_settings.json.
     * Also synchronizes to ReSA files directory if available.
     */
    public boolean syncToNativeSettings(ServerItem server, File externalFilesDir) {
        if (server == null) return false;
        boolean appSuccess = writeDirectoryConfig(externalFilesDir, server);

        // Also sync to ReSA files directory if available
        File resaDir = findResaFilesDir();
        if (resaDir != null && !resaDir.equals(externalFilesDir)) {
            writeDirectoryConfig(resaDir, server);
        }

        return appSuccess;
    }

    private boolean writeDirectoryConfig(File baseDir, ServerItem server) {
        if (baseDir == null || !baseDir.exists()) return false;
        try {
            // 1. Write SAMP/settings.ini
            File sampDir = new File(baseDir, "SAMP");
            if (!sampDir.exists()) {
                sampDir.mkdirs();
            }

            File iniFile = new File(sampDir, "settings.ini");
            String iniContent = "[client]\n" +
                    "name = " + getNickname() + "\n" +
                    "server = " + server.getIp() + "\n" +
                    "port = " + server.getPort() + "\n" +
                    "fps_limit = " + getFpsLimit() + "\n" +
                    "fast_chat = " + (isFastChat() ? 1 : 0) + "\n" +
                    "voice_chat = " + (isVoiceChat() ? 1 : 0) + "\n" +
                    "cef_overlay = 1\n";

            try (FileWriter writer = new FileWriter(iniFile, false)) {
                writer.write(iniContent);
            }

            // 2. Write client_settings.json (consumed by ReSA GameProcessPreferences.migrate)
            File jsonFile = new File(baseDir, "client_settings.json");
            String serverName = server.getName() != null ? server.getName() : server.getAddress();
            String jsonContent = "{\n" +
                    "  \"nick_name\": \"" + getNickname() + "\",\n" +
                    "  \"server_ip\": \"" + server.getIp() + "\",\n" +
                    "  \"server_port\": " + server.getPort() + ",\n" +
                    "  \"server_password\": \"\",\n" +
                    "  \"offline_mode\": false,\n" +
                    "  \"last_server_name\": \"" + serverName.replace("\"", "\\\"") + "\",\n" +
                    "  \"fps_limit\": " + getFpsLimit() + ",\n" +
                    "  \"fast_chat\": " + isFastChat() + ",\n" +
                    "  \"voice_chat\": " + isVoiceChat() + "\n" +
                    "}\n";

            try (FileWriter writer = new FileWriter(jsonFile, false)) {
                writer.write(jsonContent);
            }

            Log.i(TAG, "Successfully synced configuration to " + baseDir.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to write directory config: " + e.getMessage());
            return false;
        }
    }

    /**
     * Launches the game using the official ReSA:MP 3D game engine deep link.
     */
    public static boolean launchResaGame(Context context, ServerItem server) {
        if (context == null || server == null) return false;
        try {
            Uri connectUri = Uri.parse("resa://connect?ip=" + server.getIp() + "&port=" + server.getPort());
            Intent intent = new Intent(Intent.ACTION_VIEW, connectUri);
            intent.setPackage("com.alyn.resamp");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch ReSA engine: " + e.getMessage());
            return false;
        }
    }
}
