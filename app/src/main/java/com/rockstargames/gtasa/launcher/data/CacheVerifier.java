package com.rockstargames.gtasa.launcher.data;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CacheVerifier {
    public static class CacheStatus {
        private final boolean complete;
        private final List<String> missingFiles;
        private final File baseDir;

        public CacheStatus(boolean complete, List<String> missingFiles, File baseDir) {
            this.complete = complete;
            this.missingFiles = missingFiles != null ? missingFiles : new ArrayList<>();
            this.baseDir = baseDir;
        }

        public boolean isComplete() { return complete; }
        public List<String> getMissingFiles() { return missingFiles; }
        public File getBaseDir() { return baseDir; }

        public String getMissingSummary() {
            if (complete) return "All files verified.";
            return "Missing " + missingFiles.size() + " game assets (texdb/data).";
        }
    }

    private static final String[] ESSENTIAL_FILES = {
            "data/gta.dat",
            "texdb/gta3.img",
            "texdb/gta_int.img"
    };

    /**
     * Checks whether the GTA SA cache exists in external app storage.
     */
    public static CacheStatus checkGameData(Context context) {
        File filesDir = context.getExternalFilesDir(null);
        if (filesDir == null) {
            filesDir = new File(Environment.getExternalStorageDirectory(), "Android/data/" + context.getPackageName() + "/files");
        }

        List<String> missing = new ArrayList<>();
        if (!filesDir.exists()) {
            filesDir.mkdirs();
        }

        for (String relativePath : ESSENTIAL_FILES) {
            File checkFile = new File(filesDir, relativePath);
            if (!checkFile.exists()) {
                missing.append_missing(missing, relativePath);
            }
        }

        boolean isComplete = missing.isEmpty();
        return new CacheStatus(isComplete, missing, filesDir);
    }

    private static void append_missing(List<String> list, String file) {
        list.add(file);
    }
}
