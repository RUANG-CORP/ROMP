package com.rockstargames.gtasa.launcher.data;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Collects runtime diagnostics, crash tombstones (API 30+), AL Loader logs,
 * SAMP client logs, and Flutter overlay logs.
 */
public class CrashLogHelper {

    public static class LogEntry {
        public final String title;
        public final File file;
        public final long sizeBytes;
        public final long lastModified;

        public LogEntry(String title, File file) {
            this.title = title;
            this.file = file;
            this.sizeBytes = file.exists() ? file.length() : 0;
            this.lastModified = file.exists() ? file.lastModified() : 0;
        }
    }

    public static File getBaseDir(Context context) {
        File dir = context.getExternalFilesDir(null);
        return dir != null ? dir : context.getFilesDir();
    }

    public static boolean hasCrashFlag(Context context) {
        File base = getBaseDir(context);
        File flag = new File(base, ".crash_occurred");
        if (flag.exists()) return true;
        File[] crashes = base.listFiles((d, name) -> name.startsWith("crash_") && name.endsWith(".log"));
        return crashes != null && crashes.length > 0;
    }

    public static void clearCrashFlag(Context context) {
        File base = getBaseDir(context);
        File flag = new File(base, ".crash_occurred");
        if (flag.exists()) flag.delete();
    }

    public static List<LogEntry> listLogFiles(Context context) {
        List<LogEntry> list = new ArrayList<>();
        File base = getBaseDir(context);
        File logcatDir = new File(base, "logcat");
        addIfExists(list, "SAMP Client Log", new File(logcatDir, "client.log"));
        addIfExists(list, "Engine Debug Log", new File(logcatDir, "resa_debug.log"));
        addIfExists(list, "Flutter Overlay Log", new File(logcatDir, "flutter.log"));
        addIfExists(list, "Voice Chat Log", new File(logcatDir, "svlog.txt"));
        addIfExists(list, "Texture Debug Log", new File(logcatDir, "resa_texture_debug.log"));
        addIfExists(list, "Game Log", new File(logcatDir, "gamelog.txt"));
        addIfExists(list, "AL Loader (Lua) Log", new File(base, "mods/AL Loader/logs/alloader.log"));

        // Legacy / fallback root locations
        addIfExists(list, "SAMP Client Log (Legacy)", new File(base, "SAMP/client.log"));
        addIfExists(list, "Client Log (Root)", new File(base, "client.log"));
        addIfExists(list, "Engine Debug Log (Root)", new File(base, "resa_debug.log"));
        addIfExists(list, "Previous Debug Log", new File(base, "resa_debug.old.log"));
        addIfExists(list, "Flutter Overlay Log (Root)", new File(base, "flutter.log"));
        addIfExists(list, "AL Loader Log (Legacy)", new File(base, "mods/AL Loader/alloader.log"));
        addIfExists(list, "Texture Debug Log (Root)", new File(base, "resa_texture_debug.log"));

        for (File dir : new File[]{base, logcatDir}) {
            if (!dir.isDirectory()) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (File f : files) {
                if (f.isFile() && f.getName().startsWith("crash_")) {
                    list.add(new LogEntry("Crash: " + f.getName(), f));
                }
            }
        }
        File logsDir = new File(base, "logs");
        if (logsDir.isDirectory()) {
            File[] lfs = logsDir.listFiles();
            if (lfs != null) {
                for (File f : lfs) {
                    if (f.isFile()) list.add(new LogEntry("Log: " + f.getName(), f));
                }
            }
        }
        Collections.sort(list, (a, b) -> Long.compare(b.lastModified, a.lastModified));
        return list;
    }

    private static void addIfExists(List<LogEntry> list, String label, File f) {
        if (f.exists() && f.isFile()) {
            list.add(new LogEntry(label, f));
        }
    }

    public static String readTail(File file, int maxBytes) {
        if (file == null || !file.exists()) return "(Berkas log kosong atau belum dibuat)";
        try {
            long len = file.length();
            long skip = Math.max(0, len - maxBytes);
            StringBuilder sb = new StringBuilder();
            if (skip > 0) {
                sb.append("[...dipotong ").append(skip / 1024).append(" KB awal...]\n\n");
            }
            try (FileInputStream fis = new FileInputStream(file)) {
                if (skip > 0) fis.skip(skip);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.length() == 0 ? "(Log kosong)" : sb.toString();
        } catch (Exception e) {
            return "Gagal membaca log: " + e.getMessage();
        }
    }

    public static String getRecentTombstoneSummary(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return "";
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return "";
            List<ApplicationExitInfo> exits = am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 8);
            StringBuilder sb = new StringBuilder();
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            for (ApplicationExitInfo info : exits) {
                if (info.getReason() == ApplicationExitInfo.REASON_CRASH_NATIVE
                        || info.getReason() == ApplicationExitInfo.REASON_CRASH
                        || info.getReason() == ApplicationExitInfo.REASON_ANR) {
                    sb.append("• ").append(fmt.format(new Date(info.getTimestamp())))
                            .append(" [").append(info.getProcessName()).append("] ")
                            .append(info.getDescription() != null ? info.getDescription() : "Reason=" + info.getReason())
                            .append('\n');
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static int clearAllLogs(Context context) {
        int removed = 0;
        for (LogEntry entry : listLogFiles(context)) {
            if (entry.file.delete()) removed++;
        }
        clearCrashFlag(context);
        return removed;
    }
}

