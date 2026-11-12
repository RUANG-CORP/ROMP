package com.rockstargames.gtasa.launcher.data;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Process;
import android.util.Log;

import org.json.JSONArray;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SettingsManager {
    private static final String TAG = "SettingsManager";
    private static final String PREF_NAME = "samp_settings_pref";

    public static final String ENGINE_AUTO = "auto";
    public static final String ENGINE_RESA = "resa";
    public static final String ENGINE_ROMP = "romp";

    private static final String KEY_NICKNAME = "player_nickname";
    private static final String KEY_NICKNAME_HISTORY = "nickname_history";
    private static final String KEY_FPS_LIMIT = "fps_limit";
    private static final String KEY_FAST_CHAT = "fast_chat";
    private static final String KEY_VOICE_CHAT = "voice_chat";
    private static final String KEY_GAME_ENGINE = "game_engine_mode";
    private static final String KEY_OVERLAY_RENDERER = "overlay_renderer";
    private static final String KEY_AL_LOADER = "al_loader";
    private static final String KEY_MOD_LOADER = "mod_loader";
    private static final String KEY_CEF_OVERLAY = "cef_overlay";
    private static final String KEY_DISCORD_RPC = "discord_rpc";

    // Crosshair keys
    public static final String KEY_CROSSHAIR_STYLE = "crosshair_style";
    public static final String KEY_CROSSHAIR_SIZE = "crosshair_size";
    public static final String KEY_CROSSHAIR_THICKNESS = "crosshair_thickness";
    public static final String KEY_CROSSHAIR_OPACITY = "crosshair_opacity";
    public static final String KEY_CROSSHAIR_OUTLINE = "crosshair_outline";
    public static final String KEY_CROSSHAIR_COLOR = "crosshair_color";
    public static final String KEY_CROSSHAIR_IMAGE = "crosshair_image";

    public static final String OFFICIAL_RESA_CDN_MANIFEST = "https://resa-mp.com/api/game-files";
    private static final String KEY_CACHE_URL = "cache_cdn_url";
    private static final String DEFAULT_CACHE_URL = OFFICIAL_RESA_CDN_MANIFEST;

    private static final Pattern NICKNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\[\\]\\$\\@\\.\\=\\?]{3,20}$");

    private final Context mContext;
    private final SharedPreferences mPrefs;

    public SettingsManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isValidNickname(String name) {
        if (name == null) return false;
        return NICKNAME_PATTERN.matcher(name).matches();
    }

    public String getNickname() {
        return mPrefs.getString(KEY_NICKNAME, "Player_HARFA");
    }

    public void setNickname(String nickname) {
        mPrefs.edit().putString(KEY_NICKNAME, nickname).apply();
        addNicknameToHistory(nickname);
    }

    public List<String> getNicknameHistory() {
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(mPrefs.getString(KEY_NICKNAME_HISTORY, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String n = arr.optString(i, "");
                if (!n.isEmpty() && !list.contains(n)) list.add(n);
            }
        } catch (Exception ignored) {}
        String current = getNickname();
        if (!current.isEmpty() && !list.contains(current)) list.add(0, current);
        return list;
    }

    public void addNicknameToHistory(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) return;
        List<String> history = new ArrayList<>();
        history.add(nickname.trim());
        try {
            JSONArray arr = new JSONArray(mPrefs.getString(KEY_NICKNAME_HISTORY, "[]"));
            for (int i = 0; i < arr.length() && history.size() < 8; i++) {
                String item = arr.optString(i, "");
                if (!item.isEmpty() && !item.equals(nickname.trim())) history.add(item);
            }
        } catch (Exception ignored) {}
        JSONArray out = new JSONArray();
        for (String s : history) out.put(s);
        mPrefs.edit().putString(KEY_NICKNAME_HISTORY, out.toString()).apply();
    }

    public int getFpsLimit() {
        return mPrefs.getInt(KEY_FPS_LIMIT, 90);
    }

    public void setFpsLimit(int fps) {
        mPrefs.edit().putInt(KEY_FPS_LIMIT, fps).apply();
        setCatalogueInt("fps_limit", fps);
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
        setCatalogueBool("voice_chat", voiceChat);
    }

    public boolean isAlLoaderEnabled() {
        return mPrefs.getBoolean(KEY_AL_LOADER, true);
    }

    public void setAlLoaderEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_AL_LOADER, enabled).apply();
    }

    public boolean isModLoaderEnabled() {
        return mPrefs.getBoolean(KEY_MOD_LOADER, true);
    }

    public void setModLoaderEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_MOD_LOADER, enabled).apply();
        ModLoaderManager.applyMasterToggle(mContext, enabled);
    }

    public boolean isCefOverlayEnabled() {
        return mPrefs.getBoolean(KEY_CEF_OVERLAY, true);
    }

    public void setCefOverlayEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_CEF_OVERLAY, enabled).apply();
    }

    public boolean isDiscordRpcEnabled() {
        return mPrefs.getBoolean(KEY_DISCORD_RPC, true);
    }

    public void setDiscordRpcEnabled(boolean enabled) {
        mPrefs.edit().putBoolean(KEY_DISCORD_RPC, enabled).apply();
    }

    public String getOverlayRenderer() {
        return mPrefs.getString(KEY_OVERLAY_RENDERER, "auto");
    }

    public void setOverlayRenderer(String mode) {
        String clean = ("impeller".equals(mode) || "skia".equals(mode)) ? mode : "auto";
        mPrefs.edit().putString(KEY_OVERLAY_RENDERER, clean).apply();
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

    // =========================================================================
    // Full 142-key Catalogue Settings Accessors (synced with resa_samp / reSA.ini)
    // =========================================================================

    public boolean getCatalogueBool(String key, boolean defVal) {
        if (KEY_AL_LOADER.equals(key)) return isAlLoaderEnabled();
        if (KEY_MOD_LOADER.equals(key)) return isModLoaderEnabled();
        if (KEY_CEF_OVERLAY.equals(key)) return isCefOverlayEnabled();
        if (KEY_DISCORD_RPC.equals(key)) return isDiscordRpcEnabled();
        if (KEY_VOICE_CHAT.equals(key)) return isVoiceChat();
        return mPrefs.getBoolean("cat_b_" + key, defVal);
    }

    public void setCatalogueBool(String key, boolean value) {
        if (KEY_AL_LOADER.equals(key)) { mPrefs.edit().putBoolean(KEY_AL_LOADER, value).apply(); return; }
        if (KEY_MOD_LOADER.equals(key)) { mPrefs.edit().putBoolean(KEY_MOD_LOADER, value).apply(); ModLoaderManager.applyMasterToggle(mContext, value); return; }
        if (KEY_CEF_OVERLAY.equals(key)) { mPrefs.edit().putBoolean(KEY_CEF_OVERLAY, value).apply(); return; }
        if (KEY_DISCORD_RPC.equals(key)) { mPrefs.edit().putBoolean(KEY_DISCORD_RPC, value).apply(); return; }
        if (KEY_VOICE_CHAT.equals(key)) { mPrefs.edit().putBoolean(KEY_VOICE_CHAT, value).apply(); }
        mPrefs.edit().putBoolean("cat_b_" + key, value).apply();
    }

    public int getCatalogueInt(String key, int defVal) {
        if (KEY_FPS_LIMIT.equals(key)) return getFpsLimit();
        if (KEY_OVERLAY_RENDERER.equals(key)) {
            String r = getOverlayRenderer();
            if ("impeller".equals(r)) return 1;
            if ("skia".equals(r)) return 2;
            return 0;
        }
        return mPrefs.getInt("cat_i_" + key, defVal);
    }

    public void setCatalogueInt(String key, int value) {
        if (KEY_FPS_LIMIT.equals(key)) mPrefs.edit().putInt(KEY_FPS_LIMIT, value).apply();
        if (KEY_OVERLAY_RENDERER.equals(key)) {
            setOverlayRenderer(value == 1 ? "impeller" : (value == 2 ? "skia" : "auto"));
            return;
        }
        mPrefs.edit().putInt("cat_i_" + key, value).apply();
    }

    public float getCatalogueFloat(String key, float defVal) {
        return mPrefs.getFloat("cat_f_" + key, defVal);
    }

    public void setCatalogueFloat(String key, float value) {
        mPrefs.edit().putFloat("cat_f_" + key, value).apply();
    }

    public String getKeyBindingString(String key, String fallback) {
        return mPrefs.getString("cat_k_" + key, fallback);
    }

    public void setKeyBindingString(String key, String label) {
        mPrefs.edit().putString("cat_k_" + key, label).apply();
    }

    public void resetCatalogueDefaults(Context context) {
        SharedPreferences.Editor ed = mPrefs.edit();
        for (String k : mPrefs.getAll().keySet()) {
            if (k.startsWith("cat_")) ed.remove(k);
        }
        ed.putInt(KEY_FPS_LIMIT, 90)
          .putBoolean(KEY_FAST_CHAT, true)
          .putBoolean(KEY_VOICE_CHAT, true)
          .putBoolean(KEY_AL_LOADER, true)
          .putBoolean(KEY_MOD_LOADER, true)
          .putBoolean(KEY_CEF_OVERLAY, true)
          .putBoolean(KEY_DISCORD_RPC, true)
          .putString(KEY_OVERLAY_RENDERER, "auto")
          .apply();
    }

    // Crosshair accessors
    public int getCrosshairStyle() { return mPrefs.getInt(KEY_CROSSHAIR_STYLE, 0); }
    public int getCrosshairSize() { return mPrefs.getInt(KEY_CROSSHAIR_SIZE, 22); }
    public int getCrosshairThickness() { return mPrefs.getInt(KEY_CROSSHAIR_THICKNESS, 3); }
    public int getCrosshairOpacity() { return mPrefs.getInt(KEY_CROSSHAIR_OPACITY, 100); }
    public int getCrosshairOutline() { return mPrefs.getInt(KEY_CROSSHAIR_OUTLINE, 1); }
    public int getCrosshairColor() { return mPrefs.getInt(KEY_CROSSHAIR_COLOR, Color.WHITE); }
    public String getCrosshairImagePath() { return mPrefs.getString(KEY_CROSSHAIR_IMAGE, ""); }

    public void saveCrosshairConfig(int style, int size, int thickness, int opacity, int outline, int color, String imagePath) {
        mPrefs.edit()
                .putInt(KEY_CROSSHAIR_STYLE, style)
                .putInt(KEY_CROSSHAIR_SIZE, size)
                .putInt(KEY_CROSSHAIR_THICKNESS, thickness)
                .putInt(KEY_CROSSHAIR_OPACITY, opacity)
                .putInt(KEY_CROSSHAIR_OUTLINE, outline)
                .putInt(KEY_CROSSHAIR_COLOR, color)
                .putString(KEY_CROSSHAIR_IMAGE, imagePath != null ? imagePath : "")
                .apply();
    }

    public static boolean isResaInstalled(Context context) {
        if (context == null) return false;
        try {
            context.getPackageManager().getPackageInfo("com.alyn.resamp", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static File findResaFilesDir() {
        File resa = new File("/sdcard/Android/data/com.alyn.resamp/files");
        if (resa.exists() && resa.isDirectory()) return resa;
        File alt = new File("/storage/emulated/0/Android/data/com.alyn.resamp/files");
        if (alt.exists() && alt.isDirectory()) return alt;
        return null;
    }

    public boolean syncToNativeSettings(ServerItem server, File externalFilesDir) {
        if (server == null) return false;
        boolean appSuccess = writeDirectoryConfig(externalFilesDir, server, false);

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
            String serverPass = server != null ? server.getPassword() : "";
            String serverName = server != null
                    ? (server.getName() != null ? server.getName() : server.getAddress())
                    : "Offline";

            // 1. Write SAMP/settings.ini
            File sampDir = new File(baseDir, "SAMP");
            if (!sampDir.exists()) sampDir.mkdirs();

            File iniFile = new File(sampDir, "settings.ini");
            StringBuilder ini = new StringBuilder();
            ini.append("[client]\n")
                    .append("name = ").append(getNickname()).append("\n")
                    .append("server = ").append(serverIp).append("\n")
                    .append("port = ").append(serverPort).append("\n")
                    .append("password = ").append(serverPass).append("\n")
                    .append("fps_limit = ").append(getFpsLimit()).append("\n")
                    .append("fast_chat = ").append(isFastChat() ? 1 : 0).append("\n")
                    .append("voice_chat = ").append(isVoiceChat() ? 1 : 0).append("\n")
                    .append("cef_overlay = ").append(isCefOverlayEnabled() ? 1 : 0).append("\n")
                    .append("al_loader = ").append(isAlLoaderEnabled() ? 1 : 0).append("\n")
                    .append("mod_loader = ").append(isModLoaderEnabled() ? 1 : 0).append("\n")
                    .append("samp_version = ").append(getCatalogueInt("samp_version", 0)).append("\n");

            try (FileWriter writer = new FileWriter(iniFile, false)) {
                writer.write(ini.toString());
            }

            // 2. Write reSA.ini (key bindings & engine toggles)
            File resaIniFile = new File(baseDir, "reSA.ini");
            StringBuilder rini = new StringBuilder();
            rini.append("[client]\n")
                    .append("al_loader=").append(isAlLoaderEnabled() ? 1 : 0).append("\n")
                    .append("mod_loader=").append(isModLoaderEnabled() ? 1 : 0).append("\n")
                    .append("gyro_mode=").append(getCatalogueInt("gyro_mode", 0)).append("\n")
                    .append("\n[key_bindings]\n");
            for (SettingsCatalogue.Item item : SettingsCatalogue.load(mContext)) {
                if (item.type == SettingsCatalogue.TYPE_KEY_BIND) {
                    String defBind = !item.options.isEmpty() ? item.options.get(0).label : "";
                    String cur = getKeyBindingString(item.key, defBind);
                    if (!cur.isEmpty()) rini.append(item.key).append("=").append(cur).append("\n");
                }
            }
            try (FileWriter writer = new FileWriter(resaIniFile, false)) {
                writer.write(rini.toString());
            }

            // 3. Write client_settings.json (consumed by ReSA GameProcessPreferences.migrate)
            File jsonFile = new File(baseDir, "client_settings.json");
            String jsonContent = "{\n" +
                    "  \"nick_name\": \"" + getNickname() + "\",\n" +
                    "  \"server_ip\": \"" + serverIp + "\",\n" +
                    "  \"server_port\": " + serverPort + ",\n" +
                    "  \"server_password\": \"" + serverPass.replace("\"", "\\\"") + "\",\n" +
                    "  \"offline_mode\": " + offline + ",\n" +
                    "  \"last_server_name\": \"" + serverName.replace("\"", "\\\"") + "\",\n" +
                    "  \"fps_limit\": " + getFpsLimit() + ",\n" +
                    "  \"fast_chat\": " + isFastChat() + ",\n" +
                    "  \"voice_chat\": " + isVoiceChat() + ",\n" +
                    "  \"al_loader\": " + isAlLoaderEnabled() + "\n" +
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

    public void syncSharedPreferences(Context context, ServerItem server, boolean offline) {
        if (context == null) return;
        String nickname = getNickname();
        String serverIp = server != null ? server.getIp() : "127.0.0.1";
        int serverPort = server != null ? server.getPort() : 7777;
        String serverPassword = server != null ? server.getPassword() : "";
        String serverName = server != null ? (server.getName() != null ? server.getName() : server.getAddress()) : "Offline";

        SharedPreferences clientPrefs = context.getSharedPreferences("client_settings", Context.MODE_PRIVATE);
        SharedPreferences resaPrefs = context.getSharedPreferences("resa_samp", Context.MODE_PRIVATE);
        SharedPreferences.Editor ce = clientPrefs.edit();
        SharedPreferences.Editor re = resaPrefs.edit();

        List<SettingsCatalogue.Item> catalogue = SettingsCatalogue.load(context);
        for (SettingsCatalogue.Item item : catalogue) {
            if (item.type == SettingsCatalogue.TYPE_BOOL) {
                boolean v = getCatalogueBool(item.key, item.defaultRaw != 0);
                ce.putBoolean(item.key, v);
                re.putBoolean(item.key, v);
            } else if (item.type == SettingsCatalogue.TYPE_INT_SLIDER || item.type == SettingsCatalogue.TYPE_CHOICE) {
                int v = getCatalogueInt(item.key, item.defaultRaw);
                if ("sensitivity".equals(item.key) || "sensitivity_aim".equals(item.key) || "sensitivity_vehicle".equals(item.key)) {
                    // com.alyn.resa.Game reads camera sensitivity keys as Float from SharedPreferences
                    ce.putFloat(item.key, (float) v);
                    re.putFloat(item.key, (float) v);
                } else {
                    ce.putInt(item.key, v);
                    re.putInt(item.key, v);
                }
            } else if (item.type == SettingsCatalogue.TYPE_FLOAT_SLIDER) {
                float v = getCatalogueFloat(item.key, Float.intBitsToFloat(item.defaultRaw));
                ce.putFloat(item.key, v);
                re.putFloat(item.key, v);
            }
        }

        ThemeManager tm = new ThemeManager(context);
        FontManager fm = new FontManager(context);
        int accent = tm.getAccentColor();
        String lang = tm.getLanguage();
        String font = fm.getActiveFontFileName();
        String chImg = getCrosshairImagePath();

        for (SharedPreferences.Editor ed : new SharedPreferences.Editor[]{ce, re}) {
            ed.putString("nick_name", nickname)
              .putString("server_ip", serverIp)
              .putInt("server_port", serverPort)
              .putString("server_password", serverPassword)
              .putBoolean("offline_mode", offline)
              .putString("last_server_name", serverName)
              .putInt("fps_limit", getFpsLimit())
              .putBoolean("fast_chat", isFastChat())
              .putBoolean("voice_chat", isVoiceChat())
              .putBoolean("al_loader", isAlLoaderEnabled())
              .putBoolean("mod_loader", isModLoaderEnabled())
              .putString("overlay_renderer", getOverlayRenderer())
              .putInt("accent_rgba", accent)
              .putString("launcher_language", lang);
            if (!font.isEmpty()) ed.putString("font", font).putString("custom_font", font);
            else ed.remove("font").remove("custom_font");
            if (!chImg.isEmpty()) ed.putString("crosshair_image", chImg);
            else ed.remove("crosshair_image");
        }
        ce.commit();
        re.commit();

        Log.i(TAG, "Synchronized all SharedPreferences (offline=" + offline + ", server=" + serverIp + ":" + serverPort + ")");
    }

    public boolean launchEmbedded3dEngine(Context context, ServerItem server) {
        if (context == null || server == null) return false;
        return launchEmbedded3dGame(context, server, false, false);
    }

    public boolean launchEmbedded3dEngineOffline(Context context) {
        if (context == null) return false;
        return launchEmbedded3dGame(context, null, true, false);
    }

    public boolean launchLayoutEditor(Context context) {
        if (context == null) return false;
        return launchEmbedded3dGame(context, null, true, true);
    }

    private boolean launchEmbedded3dGame(Context context, ServerItem server, boolean offline, boolean layoutEditor) {
        try {
            killStaleGameProcess(context);
            if (isAlLoaderEnabled()) {
                AlLoaderManager.ensureLibrariesExtracted(context);
            }
            ModLoaderManager.applyMasterToggle(context, isModLoaderEnabled());

            File baseDir = context.getExternalFilesDir(null);
            if (baseDir != null) {
                if (!baseDir.exists()) baseDir.mkdirs();
                writeDirectoryConfig(baseDir, server, offline);
                File marker = new File(baseDir, ".resa-data");
                if (!marker.exists()) marker.createNewFile();
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
            if (layoutEditor) {
                intent.putExtra("launch_layout_editor", true);
            }
            if (server != null) {
                intent.putExtra("server_ip", server.getIp());
                intent.putExtra("server_port", server.getPort());
                intent.putExtra("server_password", server.getPassword());
                intent.putExtra("server_name", serverName);
                intent.putExtra("last_server_name", serverName);
            }
            intent.putExtra("alyn_session", "alyn_sess_" + now);
            intent.putExtra("alyn_session_exp", now + 86400000L);
            intent.putExtra("resa_game_process", String.valueOf(now));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
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
                    Process.killProcess(proc.pid);
                }
            }
        } catch (Exception ignored) {}
    }

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
