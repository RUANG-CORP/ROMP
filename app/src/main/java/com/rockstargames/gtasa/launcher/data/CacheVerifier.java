package com.rockstargames.gtasa.launcher.data;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Universal Game Cache Verifier.
 * Supports both modern ReSA Mobile format (textures/game.bin & textures/game.img)
 * and legacy GTA SA Mobile format (texdb/gta3.img & texdb/gta_int.img).
 */
public class CacheVerifier {

    public static class CacheStatus {
        private final boolean complete;
        private final List<String> missingFiles;
        private final File baseDir;
        private final int totalFiles;
        private final long totalBytes;

        public CacheStatus(boolean complete, List<String> missingFiles, File baseDir, int totalFiles, long totalBytes) {
            this.complete = complete;
            this.missingFiles = missingFiles != null ? missingFiles : new ArrayList<>();
            this.baseDir = baseDir;
            this.totalFiles = totalFiles;
            this.totalBytes = totalBytes;
        }

        public boolean isComplete() { return complete; }
        public List<String> getMissingFiles() { return missingFiles; }
        public File getBaseDir() { return baseDir; }
        public int getTotalFiles() { return totalFiles; }
        public long getTotalBytes() { return totalBytes; }

        public String getMissingSummary() {
            if (complete) {
                long mb = totalBytes / (1024 * 1024);
                return "Semua berkas gim terverifikasi (" + totalFiles + " berkas, " + mb + " MB). Siap dimainkan!";
            }
            return "Berkas gim belum lengkap (Kurang " + missingFiles.size() + " aset inti).";
        }
    }

    private static final String[] RESA_CANDIDATE_PATHS = {
            "/sdcard/Android/data/com.alyn.resamp/files",
            "/storage/emulated/0/Android/data/com.alyn.resamp/files"
    };

    /**
     * Checks if local ReSA Multiplayer game files exist on device storage.
     */
    public static File findResaCacheDir() {
        for (String path : RESA_CANDIDATE_PATHS) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                File texturesDir = new File(dir, "textures");
                File dataDir = new File(dir, "data");
                if (texturesDir.exists() || dataDir.exists()) {
                    return dir;
                }
            }
        }
        return null;
    }

    /**
     * Checks whether the GTA SA cache exists and is complete in external app storage.
     */
    public static CacheStatus checkGameData(Context context) {
        File filesDir = context != null ? context.getExternalFilesDir(null) : null;
        if (filesDir == null) {
            String pkg = context != null ? context.getPackageName() : "com.rockstargames.gtasa";
            filesDir = new File(Environment.getExternalStorageDirectory(), "Android/data/" + pkg + "/files");
        }

        if (!filesDir.exists()) {
            filesDir.mkdirs();
        }

        List<String> missing = new ArrayList<>();

        // 1. Core data configuration check (libalyn_resa.so requires readable data/gta.dat on startup)
        File gtaDat = new File(filesDir, "data/gta.dat");
        if (!gtaDat.exists() || !gtaDat.canRead()) {
            missing.add("data/gta.dat");
        }

        // 2. Textures check:
        // Modern ReSA format: textures/game.bin or textures/game.img
        // Legacy format: texdb/gta3.img or texdb/gta_int.img
        File modernGameBin = new File(filesDir, "textures/game.bin");
        File modernGameImg = new File(filesDir, "textures/game.img");
        File legacyGta3 = new File(filesDir, "texdb/gta3.img");
        File legacyGtaInt = new File(filesDir, "texdb/gta_int.img");

        boolean hasModernTextures = (modernGameBin.exists() && modernGameBin.canRead())
                || (modernGameImg.exists() && modernGameImg.canRead());
        boolean hasLegacyTextures = (legacyGta3.exists() && legacyGta3.canRead())
                || (legacyGtaInt.exists() && legacyGtaInt.canRead());

        if (!hasModernTextures && !hasLegacyTextures) {
            missing.add("textures/game.bin (atau texdb/gta3.img)");
        }

        int[] countHolder = new int[1];
        long[] sizeHolder = new long[1];
        calcDirStats(filesDir, countHolder, sizeHolder);

        boolean isComplete = missing.isEmpty();
        return new CacheStatus(isComplete, missing, filesDir, countHolder[0], sizeHolder[0]);
    }

    public static class FolderInfo {
        public final String name;
        public final int expectedFiles;
        public final int actualFiles;
        public final long totalBytes;

        public FolderInfo(String name, int expectedFiles, int actualFiles, long totalBytes) {
            this.name = name;
            this.expectedFiles = expectedFiles;
            this.actualFiles = actualFiles;
            this.totalBytes = totalBytes;
        }

        public boolean isComplete() {
            return actualFiles >= expectedFiles && actualFiles > 0;
        }
    }

    public static List<FolderInfo> getFolderBreakdown(File baseDir) {
        List<FolderInfo> list = new ArrayList<>();
        if (baseDir == null || !baseDir.exists()) return list;

        String[][] folders = {
                {"textures", "12"},
                {"data", "178"},
                {"audio", "47"},
                {"anim", "3"},
                {"SAMP", "11"},
                {"fonts", "3"},
                {"models", "4"}
        };

        for (String[] def : folders) {
            String name = def[0];
            int expected = Integer.parseInt(def[1]);
            File sub = new File(baseDir, name);
            int[] count = new int[1];
            long[] size = new long[1];
            if (sub.exists()) {
                calcDirStats(sub, count, size);
            }
            list.add(new FolderInfo(name, expected, count[0], size[0]));
        }
        return list;
    }

    private static void calcDirStats(File dir, int[] count, long[] size) {
        File[] list = dir.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) {
                calcDirStats(f, count, size);
            } else {
                count[0]++;
                size[0] += f.length();
            }
        }
    }
}
