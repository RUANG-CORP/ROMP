package com.rockstargames.gtasa.launcher.data;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.util.List;
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
        boolean appSuccess = writeDirectoryConfig(externalFilesDir, server, false);

        // Also sync to ReSA files directory if available
        File resaDir = findResaFilesDir();
        if (resaDir != null && !resaDir.equals(externalFilesDir)) {
            writeDirectoryConfig(resaDir, server, false);
        }

        return appSuccess;
    }

    private boolean writeDirectoryConfig(File baseDir, ServerItem server, boolean offline) {
        if (baseDir == null || !baseDir.exists()) return false;
        try {
            String serverIp = server != null ? server.getIp() : "127.0.0.1";
            int serverPort = server != null ? server.getPort() : 7777;
            String serverName = server != null
                    ? (server.getName() != null ? server.getName() : server.getAddress())
                    : "Offline";

            // 1. Write SAMP/settings.ini
            File sampDir = new File(baseDir, "SAMP");
            if (!sampDir.exists()) {
                sampDir.mkdirs();
            }

            File iniFile = new File(sampDir, "settings.ini");
            String iniContent = "[client]\n" +
                    "name = " + getNickname() + "\n" +
                    "server = " + serverIp + "\n" +
                    "port = " + serverPort + "\n" +
                    "fps_limit = " + getFpsLimit() + "\n" +
                    "fast_chat = " + (isFastChat() ? 1 : 0) + "\n" +
                    "voice_chat = " + (isVoiceChat() ? 1 : 0) + "\n" +
                    "cef_overlay = 1\n";

            try (FileWriter writer = new FileWriter(iniFile, false)) {
                writer.write(iniContent);
            }

            // 2. Write client_settings.json (consumed by ReSA GameProcessPreferences.migrate)
            File jsonFile = new File(baseDir, "client_settings.json");
            String jsonContent = "{\n" +
                    "  \"nick_name\": \"" + getNickname() + "\",\n" +
                    "  \"server_ip\": \"" + serverIp + "\",\n" +
                    "  \"server_port\": " + serverPort + ",\n" +
                    "  \"server_password\": \"\",\n" +
                    "  \"offline_mode\": " + offline + ",\n" +
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
     * Synchronizes comprehensive game settings to both 'client_settings' and 'resa_samp' SharedPreferences.
     */
    public void syncSharedPreferences(Context context, ServerItem server, boolean offline) {
        if (context == null) return;
        String nickname = getNickname();
        String serverIp = server != null ? server.getIp() : "127.0.0.1";
        int serverPort = server != null ? server.getPort() : 7777;
        String serverName = server != null ? (server.getName() != null ? server.getName() : server.getAddress()) : "Offline";
        int fps = getFpsLimit();
        boolean fastChat = isFastChat();
        boolean voiceChat = isVoiceChat();

        // 1. Write client_settings
        SharedPreferences clientPrefs = context.getSharedPreferences("client_settings", Context.MODE_PRIVATE);
        clientPrefs.edit()
                .putString("nick_name", nickname)
                .putString("server_ip", serverIp)
                .putInt("server_port", serverPort)
                .putString("server_password", "")
                .putBoolean("offline_mode", offline)
                .putString("last_server_name", serverName)
                .putInt("fps_limit", fps)
                .putBoolean("fast_chat", fastChat)
                .putBoolean("voice_chat", voiceChat)
                .commit();

        // 2. Write resa_samp (directly consumed by native libalyn_resa.so)
        SharedPreferences resaPrefs = context.getSharedPreferences("resa_samp", Context.MODE_PRIVATE);
        resaPrefs.edit()
                .putString("nick_name", nickname)
                .putString("server_ip", serverIp)
                .putInt("server_port", serverPort)
                .putString("server_password", "")
                .putBoolean("offline_mode", offline)
                .putString("last_server_name", serverName)
                .putInt("fps_limit", fps)
                .putBoolean("fast_chat", fastChat)
                .putBoolean("voice_chat", voiceChat)
                .commit();

        Log.i(TAG, "Synchronized SharedPreferences (offline=" + offline + ", server=" + serverIp + ":" + serverPort + ", nick=" + nickname + ")");
    }

    /**
     * Directly launches the embedded 3D RenderWare SA-MP engine inside ROMP.
     * Synchronizes server and player settings to external files dir and boots com.alyn.resa.Game.
     */
    public boolean launchEmbedded3dEngine(Context context, ServerItem server) {
        if (context == null || server == null) return false;
        return launchEmbedded3dGame(context, server, false);
    }

    /**
     * Directly launches the embedded 3D RenderWare engine in single-player offline mode.
     */
    public boolean launchEmbedded3dEngineOffline(Context context) {
        if (context == null) return false;
        return launchEmbedded3dGame(context, null, true);
    }

    private boolean launchEmbedded3dGame(Context context, ServerItem server, boolean offline) {
        try {
            // Ensure previous :game process does not hold stale in-memory SharedPreferences across mode switches
            killStaleGameProcess(context);

            File baseDir = context.getExternalFilesDir(null);
            if (baseDir != null) {
                writeDirectoryConfig(baseDir, server, offline);
                File marker = new File(baseDir, ".resa-data");
                if (!marker.exists()) {
                    marker.createNewFile();
                }
            }

            syncSharedPreferences(context, server, offline);

            String serverName = server != null
                    ? (server.getName() != null ? server.getName() : server.getAddress())
                    : "Offline";
            long now = System.currentTimeMillis();
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(context.getPackageName(), "com.alyn.resa.Game"));
            intent.putExtra("extra_check", "alynsampmobile1337");
            intent.putExtra("offline_mode", offline);
            intent.putExtra("player_name", getNickname());
            intent.putExtra("nick_name", getNickname());
            if (server != null) {
                intent.putExtra("server_ip", server.getIp());
                intent.putExtra("server_port", server.getPort());
                intent.putExtra("server_password", "");
                intent.putExtra("server_name", serverName);
                intent.putExtra("last_server_name", serverName);
            }
            intent.putExtra("alyn_session", "alyn_sess_" + now);
            intent.putExtra("alyn_session_exp", now + 86400000L);
            intent.putExtra("resa_game_process", String.valueOf(now));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
            Log.i(TAG, "Successfully launched embedded 3D engine (offline=" + offline + ", target=" + serverName + ")");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch embedded 3D engine: " + e.getMessage(), e);
            return false;
        }
    }

    private static void killStaleGameProcess(Context context) {
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            String gameProcName = context.getPackageName() + ":game";
            List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
            if (procs == null) return;
            for (ActivityManager.RunningAppProcessInfo proc : procs) {
                if (gameProcName.equals(proc.processName) && proc.pid != Process.myPid()) {
                    Log.i(TAG, "Terminating stale game process pid=" + proc.pid + " before relaunch");
                    Process.killProcess(proc.pid);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not clean stale game process: " + e.getMessage());
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
