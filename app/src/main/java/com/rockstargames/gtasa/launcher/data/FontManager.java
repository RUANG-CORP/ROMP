package com.rockstargames.gtasa.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Manages bundled UI/chat fonts and custom user .ttf/.otf font uploads
 * compatible with ROMP and InGameFlutterHost.
 */
public class FontManager {
    private static final String ASSET_FONTS_DIR = "flutter_assets/assets/fonts";
    private static final String PREF_NAME = "romp_font_pref";
    private static final String KEY_ACTIVE_FONT = "active_font_file";

    public static class FontEntry {
        public final String displayName;
        public final String fileName;
        public final boolean isCustom;
        public final String assetOrFilePath;

        public FontEntry(String displayName, String fileName, boolean isCustom, String assetOrFilePath) {
            this.displayName = displayName;
            this.fileName = fileName;
            this.isCustom = isCustom;
            this.assetOrFilePath = assetOrFilePath;
        }
    }

    private final Context mContext;
    private final SharedPreferences mPrefs;

    public FontManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public File getExternalFontsDir() {
        File base = mContext.getExternalFilesDir(null);
        if (base == null) base = mContext.getFilesDir();
        File dir = new File(base, "fonts");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public String getActiveFontFileName() {
        return mPrefs.getString(KEY_ACTIVE_FONT, "");
    }

    public List<FontEntry> listAvailableFonts() {
        List<FontEntry> list = new ArrayList<>();
        list.add(new FontEntry("Default Engine Font", "", false, ""));

        // 1. Custom user fonts in /files/fonts/
        File extDir = getExternalFontsDir();
        File[] extFiles = extDir.listFiles();
        if (extFiles != null) {
            Arrays.sort(extFiles, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : extFiles) {
                if (f.isFile() && isFontFile(f.getName())) {
                    list.add(new FontEntry("Custom: " + stripExt(f.getName()), f.getName(), true, f.getAbsolutePath()));
                }
            }
        }

        // 2. Bundled fonts from assets
        try {
            AssetManager am = mContext.getAssets();
            String[] bundled = am.list(ASSET_FONTS_DIR);
            if (bundled != null) {
                Arrays.sort(bundled, String::compareToIgnoreCase);
                for (String name : bundled) {
                    if (isFontFile(name)) {
                        list.add(new FontEntry(stripExt(name), name, false, ASSET_FONTS_DIR + "/" + name));
                    }
                }
            }
        } catch (IOException ignored) {}
        return list;
    }

    public boolean activateFont(FontEntry entry) {
        if (entry == null || entry.fileName.isEmpty()) {
            resetToDefault();
            return true;
        }
        try {
            File target = new File(getExternalFontsDir(), entry.fileName);
            if (!entry.isCustom && !entry.assetOrFilePath.isEmpty()) {
                copyAsset(entry.assetOrFilePath, target);
            }
            // Also copy as SAMP/samp.ttf or write preference recognized by InGameFlutterHost
            mPrefs.edit().putString(KEY_ACTIVE_FONT, entry.fileName).apply();
            syncToGamePrefs(entry.fileName);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean importCustomFont(InputStream in, String fileName) {
        if (in == null || fileName == null || !isFontFile(fileName)) return false;
        String safeName = new File(fileName).getName().replaceAll("[^A-Za-z0-9._-]", "_");
        File dest = new File(getExternalFontsDir(), safeName);
        try (FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
            mPrefs.edit().putString(KEY_ACTIVE_FONT, safeName).apply();
            syncToGamePrefs(safeName);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void resetToDefault() {
        mPrefs.edit().putString(KEY_ACTIVE_FONT, "").apply();
        syncToGamePrefs("");
    }

    private void syncToGamePrefs(String fontFileName) {
        SharedPreferences resa = mContext.getSharedPreferences("resa_samp", Context.MODE_PRIVATE);
        SharedPreferences client = mContext.getSharedPreferences("client_settings", Context.MODE_PRIVATE);
        if (fontFileName == null || fontFileName.isEmpty()) {
            resa.edit().remove("font").remove("custom_font").apply();
            client.edit().remove("font").remove("custom_font").apply();
        } else {
            resa.edit().putString("font", fontFileName).putString("custom_font", fontFileName).apply();
            client.edit().putString("font", fontFileName).putString("custom_font", fontFileName).apply();
        }
    }

    private void copyAsset(String assetPath, File dest) throws IOException {
        try (InputStream in = mContext.getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
        }
    }

    private static boolean isFontFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static String readFileHeadSafe(File f, int maxBytes) {
        try (FileInputStream fis = new FileInputStream(f)) {
            byte[] b = new byte[Math.min(maxBytes, (int) f.length())];
            int read = fis.read(b);
            return read > 0 ? new String(b, 0, read) : "";
        } catch (IOException e) {
            return "";
        }
    }
}

