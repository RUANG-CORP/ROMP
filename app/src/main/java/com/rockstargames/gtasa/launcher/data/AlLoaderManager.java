package com.rockstargames.gtasa.launcher.data;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Manages ReSA AL Loader (MoonLoader-compatible Lua 5.1/LuaJIT mod environment):
 * - Extracts runtime Lua libraries (assets/al_loader/lib -> mods/AL Loader/lib)
 * - Lists, installs & removes bundled recommended scripts from catalog.json
 * - Toggles, imports & deletes user .lua scripts under mods/AL Loader/scripts
 * - Reads al_api.json for in-launcher Lua API documentation & alloader.log
 */
public class AlLoaderManager {
    private static final String TAG = "AlLoaderManager";
    private static final String ASSET_LIB_DIR = "al_loader/lib";
    private static final String ASSET_LIB_VERSION = "al_loader_meta/lib_version";
    private static final String ASSET_CATALOG = "flutter_assets/assets/al_scripts/catalog.json";
    private static final String ASSET_SCRIPT_PREFIX = "flutter_assets/assets/al_scripts/";
    private static final String ASSET_API_DOC = "flutter_assets/assets/al_docs/al_api.json";

    public static class RecommendedScript {
        public final String file;
        public final String name;
        public final String version;
        public final String author;
        public final String commands;
        public final String summary;

        public RecommendedScript(String file, String name, String version, String author, String commands, String summary) {
            this.file = file;
            this.name = name;
            this.version = version;
            this.author = author;
            this.commands = commands;
            this.summary = summary;
        }
    }

    public static class InstalledScript {
        public final String fileName;
        public final boolean enabled;
        public final long sizeBytes;
        public final File file;

        public InstalledScript(File file) {
            this.file = file;
            this.fileName = file.getName();
            this.enabled = this.fileName.toLowerCase().endsWith(".lua");
            this.sizeBytes = file.length();
        }

        public String getDisplayName() {
            if (fileName.endsWith(".lua.disabled")) {
                return fileName.substring(0, fileName.length() - ".disabled".length());
            }
            return fileName;
        }
    }

    public static File getAlLoaderRoot(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        File root = new File(base, "mods/AL Loader");
        if (!root.exists()) root.mkdirs();
        return root;
    }

    public static File getScriptsDir(Context context) {
        File dir = new File(getAlLoaderRoot(context), "scripts");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getLogFile(Context context) {
        File nested = new File(getAlLoaderRoot(context), "logs/alloader.log");
        if (nested.exists()) return nested;
        return new File(getAlLoaderRoot(context), "alloader.log");
    }

    public static boolean ensureLibrariesExtracted(Context context) {
        try {
            File root = getAlLoaderRoot(context);
            File libDir = new File(root, "lib");
            File configDir = new File(root, "config");
            File scriptsDir = getScriptsDir(context);
            if (!libDir.exists()) libDir.mkdirs();
            if (!configDir.exists()) configDir.mkdirs();
            if (!scriptsDir.exists()) scriptsDir.mkdirs();

            String assetVer = readAssetText(context, ASSET_LIB_VERSION).trim();
            File stamp = new File(root, ".al_lib_version");
            if (stamp.exists() && !assetVer.isEmpty()) {
                String currentVer = CrashLogHelper.readTail(stamp, 1024).trim();
                if (assetVer.equals(currentVer) && new File(libDir, "alloader.lua").exists()) {
                    return true;
                }
            }

            copyAssetTree(context.getAssets(), ASSET_LIB_DIR, libDir);
            if (!assetVer.isEmpty()) {
                try (FileOutputStream fos = new FileOutputStream(stamp, false)) {
                    fos.write(assetVer.getBytes(StandardCharsets.UTF_8));
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed extracting AL Loader libraries: " + e.getMessage(), e);
            return false;
        }
    }

    public static List<RecommendedScript> getRecommendedScripts(Context context) {
        List<RecommendedScript> list = new ArrayList<>();
        try {
            String raw = readAssetText(context, ASSET_CATALOG);
            JSONObject obj = new JSONObject(raw);
            JSONArray arr = obj.optJSONArray("scripts");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject item = arr.getJSONObject(i);
                    JSONArray cmds = item.optJSONArray("commands");
                    StringBuilder cmdSb = new StringBuilder();
                    if (cmds != null) {
                        for (int c = 0; c < cmds.length(); c++) {
                            if (c > 0) cmdSb.append(", ");
                            cmdSb.append(cmds.optString(c));
                        }
                    }
                    list.add(new RecommendedScript(
                            item.optString("file"),
                            item.optString("name"),
                            item.optString("version", "1.0"),
                            item.optString("author", "ROMP"),
                            cmdSb.toString(),
                            item.optString("summary", "")
                    ));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed parsing script catalog: " + e.getMessage());
        }
        return list;
    }

    public static boolean isRecommendedInstalled(Context context, String fileName) {
        File dir = getScriptsDir(context);
        return new File(dir, fileName).exists() || new File(dir, fileName + ".disabled").exists();
    }

    public static boolean installRecommendedScript(Context context, String fileName) {
        ensureLibrariesExtracted(context);
        File target = new File(getScriptsDir(context), fileName);
        File disabled = new File(getScriptsDir(context), fileName + ".disabled");
        if (disabled.exists()) disabled.delete();
        try (InputStream in = context.getAssets().open(ASSET_SCRIPT_PREFIX + fileName);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Install script failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean uninstallScript(Context context, String fileName) {
        File dir = getScriptsDir(context);
        boolean ok = false;
        File active = new File(dir, fileName);
        if (active.exists()) ok |= active.delete();
        File disabled = new File(dir, fileName + ".disabled");
        if (disabled.exists()) ok |= disabled.delete();
        return ok;
    }

    public static List<InstalledScript> listInstalledScripts(Context context) {
        List<InstalledScript> out = new ArrayList<>();
        File[] files = getScriptsDir(context).listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File f : files) {
                String lower = f.getName().toLowerCase();
                if (f.isFile() && (lower.endsWith(".lua") || lower.endsWith(".lua.disabled"))) {
                    out.add(new InstalledScript(f));
                }
            }
        }
        return out;
    }

    public static boolean toggleScript(InstalledScript script) {
        if (script == null || !script.file.exists()) return false;
        File target;
        if (script.enabled) {
            target = new File(script.file.getParentFile(), script.fileName + ".disabled");
        } else {
            target = new File(script.file.getParentFile(), script.getDisplayName());
        }
        return script.file.renameTo(target);
    }

    public static boolean importLuaScript(Context context, InputStream in, String fileName) {
        if (in == null || fileName == null) return false;
        ensureLibrariesExtracted(context);
        String cleanName = new File(fileName).getName().replaceAll("[^A-Za-z0-9._-]", "_");
        if (!cleanName.toLowerCase().endsWith(".lua")) cleanName += ".lua";
        File dest = new File(getScriptsDir(context), cleanName);
        try (FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String formatApiReference(Context context, String query) {
        String lower = query != null ? query.trim().toLowerCase() : "";
        StringBuilder sb = new StringBuilder();
        try {
            JSONObject doc = new JSONObject(readAssetText(context, ASSET_API_DOC));
            JSONArray globals = doc.optJSONArray("globals");
            if (globals != null) {
                sb.append("=== GLOBALS ===\n");
                for (int i = 0; i < globals.length(); i++) {
                    JSONObject g = globals.getJSONObject(i);
                    String name = g.optString("name");
                    String sum = g.optString("summary");
                    if (lower.isEmpty() || name.toLowerCase().contains(lower) || sum.toLowerCase().contains(lower)) {
                        sb.append("• ").append(name).append(" (").append(g.optString("type")).append("): ").append(sum).append("\n");
                    }
                }
                sb.append("\n");
            }
            JSONArray events = doc.optJSONArray("events");
            if (events != null) {
                sb.append("=== EVENTS ===\n");
                for (int i = 0; i < events.length(); i++) {
                    String ev = events.optString(i);
                    if (lower.isEmpty() || ev.toLowerCase().contains(lower)) {
                        sb.append("• ").append(ev).append("\n");
                    }
                }
                sb.append("\n");
            }
            JSONArray funcs = doc.optJSONArray("functions");
            if (funcs != null) {
                sb.append("=== FUNCTIONS / OPCODES ===\n");
                int shown = 0;
                for (int i = 0; i < funcs.length() && shown < 180; i++) {
                    JSONObject fn = funcs.getJSONObject(i);
                    String name = fn.optString("name");
                    String cat = fn.optString("category");
                    if (lower.isEmpty() || name.toLowerCase().contains(lower) || cat.toLowerCase().contains(lower)) {
                        sb.append("[").append(cat).append("] ").append(name).append("(");
                        JSONArray params = fn.optJSONArray("params");
                        if (params != null) {
                            for (int p = 0; p < params.length(); p++) {
                                if (p > 0) sb.append(", ");
                                JSONObject po = params.getJSONObject(p);
                                sb.append(po.optString("type")).append(" ").append(po.optString("name"));
                            }
                        }
                        sb.append(")");
                        String op = fn.optString("opcode");
                        if (!op.isEmpty()) sb.append("  [0x").append(op).append("]");
                        sb.append("\n");
                        shown++;
                    }
                }
            }
        } catch (Exception e) {
            return "Gagal memuat API reference: " + e.getMessage();
        }
        return sb.length() > 0 ? sb.toString() : "Tidak ada simbol yang cocok.";
    }

    private static void copyAssetTree(AssetManager am, String assetPath, File destDir) throws IOException {
        String[] children = am.list(assetPath);
        if (children == null || children.length == 0) {
            try (InputStream in = am.open(assetPath);
                 FileOutputStream out = new FileOutputStream(destDir)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                out.flush();
            }
        } else {
            if (!destDir.exists()) destDir.mkdirs();
            for (String child : children) {
                copyAssetTree(am, assetPath + "/" + child, new File(destDir, child));
            }
        }
    }

    private static String readAssetText(Context context, String path) throws IOException {
        try (InputStream in = context.getAssets().open(path);
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }
}

