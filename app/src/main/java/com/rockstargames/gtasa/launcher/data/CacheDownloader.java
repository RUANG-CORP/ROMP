package com.rockstargames.gtasa.launcher.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Universal Game Data Downloader & Sync Engine.
 * Supports:
 * 1. Official ReSA Game CDN Manifest streaming (258 files, 766 MB direct Cloudflare edge CDN).
 * 2. Instant 1-click local sync from ReSA Multiplayer (/sdcard/Android/data/com.alyn.resamp/files).
 * 3. Monolithic ZIP archive downloads with streaming extraction and Zip Slip security.
 * 4. Instant offline mock data generation.
 */
public class CacheDownloader {
    private static final String TAG = "CacheDownloader";
    private static final int BUFFER_SIZE = 64 * 1024; // 64KB chunk buffer for maximum I/O throughput
    private static final String USER_AGENT = "AlynLauncher/20.5";

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    public interface DownloadCallback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        void onExtracting(String entryName);
        void onComplete(File destinationDir);
        void onError(String errorMessage);
    }

    private static volatile boolean sIsDownloading = false;
    private static volatile boolean sCancelRequested = false;

    public static boolean isDownloading() {
        return sIsDownloading;
    }

    public static void cancel() {
        sCancelRequested = true;
    }

    /**
     * File entry model for CDN manifest.
     */
    public static class ManifestFileItem {
        public final String name;
        public final String path;
        public final long size;
        public final String url;

        public ManifestFileItem(String name, String path, long size, String url) {
            this.name = name;
            this.path = path;
            this.size = size;
            this.url = url;
        }
    }

    /**
     * Creates minimal placeholder game data assets for testing/offline use.
     */
    public static boolean createMockCache(File destDir) {
        try {
            if (!destDir.exists()) destDir.mkdirs();

            File dataDir = new File(destDir, "data");
            if (!dataDir.exists()) dataDir.mkdirs();
            File gtaDat = new File(dataDir, "gta.dat");
            if (!gtaDat.exists()) {
                try (FileOutputStream fos = new FileOutputStream(gtaDat)) {
                    fos.write("# GTA SA Data Configuration Placeholder\n".getBytes(StandardCharsets.UTF_8));
                }
            }

            File texturesDir = new File(destDir, "textures");
            if (!texturesDir.exists()) texturesDir.mkdirs();
            File gameBin = new File(texturesDir, "game.bin");
            if (!gameBin.exists()) {
                try (FileOutputStream fos = new FileOutputStream(gameBin)) {
                    fos.write(new byte[2048]);
                }
            }
            File gameImg = new File(texturesDir, "game.img");
            if (!gameImg.exists()) {
                try (FileOutputStream fos = new FileOutputStream(gameImg)) {
                    fos.write(new byte[2048]);
                }
            }

            File texdbDir = new File(destDir, "texdb");
            if (!texdbDir.exists()) texdbDir.mkdirs();
            File gta3Img = new File(texdbDir, "gta3.img");
            if (!gta3Img.exists()) {
                try (FileOutputStream fos = new FileOutputStream(gta3Img)) {
                    fos.write(new byte[1024]);
                }
            }
            File gtaIntImg = new File(texdbDir, "gta_int.img");
            if (!gtaIntImg.exists()) {
                try (FileOutputStream fos = new FileOutputStream(gtaIntImg)) {
                    fos.write(new byte[1024]);
                }
            }

            File sampDir = new File(destDir, "SAMP");
            if (!sampDir.exists()) sampDir.mkdirs();
            File sampIde = new File(sampDir, "SAMP.ide");
            if (!sampIde.exists()) {
                try (FileOutputStream fos = new FileOutputStream(sampIde)) {
                    fos.write("# SAMP IDE\n".getBytes(StandardCharsets.UTF_8));
                }
            }

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create mock cache: " + e.getMessage());
            return false;
        }
    }

    /**
     * 1-Click Instant Local Sync from ReSA app directory or any local game folder.
     */
    public static void syncFromLocalDirectory(final File sourceDir, final File destDir, final DownloadCallback callback) {
        if (sIsDownloading) {
            postError(callback, "Operasi download/sinkronisasi lain sedang berjalan.");
            return;
        }

        if (sourceDir == null || !sourceDir.exists() || !sourceDir.isDirectory()) {
            postError(callback, "Folder sumber tidak ditemukan atau tidak dapat diakses.");
            return;
        }

        sIsDownloading = true;
        sCancelRequested = false;

        sExecutor.execute(() -> {
            try {
                if (!destDir.exists()) {
                    destDir.mkdirs();
                }

                // 1. Scan all source files
                List<File> allFiles = new ArrayList<>();
                collectFiles(sourceDir, allFiles);

                long totalBytes = 0;
                for (File f : allFiles) {
                    totalBytes += f.length();
                }

                Log.i(TAG, "Starting local sync: " + allFiles.size() + " files (" + (totalBytes / (1024 * 1024)) + " MB)");

                long copiedBytes = 0;
                byte[] buffer = new byte[BUFFER_SIZE];
                String sourceBasePath = sourceDir.getAbsolutePath();

                for (int i = 0; i < allFiles.size(); i++) {
                    if (sCancelRequested) {
                        throw new Exception("Sinkronisasi dibatalkan oleh pengguna.");
                    }

                    File srcFile = allFiles.get(i);
                    String relPath = srcFile.getAbsolutePath().substring(sourceBasePath.length());
                    if (relPath.startsWith(File.separator)) {
                        relPath = relPath.substring(1);
                    }

                    File targetFile = new File(destDir, relPath);
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    postExtracting(callback, "[" + (i + 1) + "/" + allFiles.size() + "] " + relPath);

                    // Skip if target exists and size matches
                    if (targetFile.exists() && targetFile.length() == srcFile.length()) {
                        copiedBytes += srcFile.length();
                        if (totalBytes > 0) {
                            int percent = (int) ((copiedBytes * 100) / totalBytes);
                            postProgress(callback, percent, copiedBytes, totalBytes);
                        }
                        continue;
                    }

                    try (InputStream in = new BufferedInputStream(new FileInputStream(srcFile));
                         OutputStream out = new FileOutputStream(targetFile)) {
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            copiedBytes += read;
                            if (totalBytes > 0) {
                                int percent = (int) ((copiedBytes * 100) / totalBytes);
                                postProgress(callback, percent, copiedBytes, totalBytes);
                            }
                        }
                        out.flush();
                    }
                }

                sIsDownloading = false;
                postComplete(callback, destDir);

            } catch (Exception e) {
                Log.e(TAG, "Local sync failed: " + e.getMessage(), e);
                sIsDownloading = false;
                postError(callback, "Sinkronisasi gagal: " + e.getMessage());
            }
        });
    }

    private static void collectFiles(File dir, List<File> result) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectFiles(f, result);
            } else {
                result.add(f);
            }
        }
    }

    /**
     * Universal Smart Downloader:
     * - If target is a JSON Manifest (e.g. https://resa-mp.com/api/game-files), streams individual game assets.
     * - If target is a ZIP file, downloads and unpacks using ZipInputStream.
     */
    public static void downloadFromCdnOrZip(final String sourceUrl, final File destDir, final DownloadCallback callback) {
        if (sIsDownloading) {
            postError(callback, "Pengunduhan sedang berjalan.");
            return;
        }

        sIsDownloading = true;
        sCancelRequested = false;

        sExecutor.execute(() -> {
            try {
                if (!destDir.exists()) {
                    destDir.mkdirs();
                }

                if (sourceUrl.toLowerCase().endsWith(".zip")) {
                    // Download and unpack monolithic ZIP
                    downloadAndExtractZipInternal(sourceUrl, destDir, callback);
                } else {
                    // Download via CDN Manifest (JSON)
                    downloadManifestInternal(sourceUrl, destDir, callback);
                }

                sIsDownloading = false;
                postComplete(callback, destDir);

            } catch (Exception e) {
                Log.e(TAG, "Download failed: " + e.getMessage(), e);
                sIsDownloading = false;
                postError(callback, e.getMessage());
            }
        });
    }

    /**
     * Streams all game files listed in the ReSA CDN JSON manifest.
     */
    private static void downloadManifestInternal(String manifestUrl, File destDir, DownloadCallback callback) throws Exception {
        Log.i(TAG, "Fetching CDN Manifest from: " + manifestUrl);
        postExtracting(callback, "Mengambil manifes berkas dari CDN...");

        HttpURLConnection conn = openConnectionWithRedirects(manifestUrl);
        if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new Exception("Gagal menghubungi server CDN (HTTP " + conn.getResponseCode() + ")");
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        conn.disconnect();

        JSONObject root = new JSONObject(sb.toString());
        if (!root.has("files")) {
            throw new Exception("Format manifes tidak valid (kunci 'files' tidak ditemukan).");
        }

        JSONArray filesArray = root.getJSONArray("files");
        List<ManifestFileItem> manifestList = new ArrayList<>();
        long totalManifestBytes = 0;

        for (int i = 0; i < filesArray.length(); i++) {
            JSONObject item = filesArray.getJSONObject(i);
            String name = item.optString("name", "");
            String path = item.optString("path", name);
            long size = item.optLong("size", 0);
            String url = item.optString("url", "");
            manifestList.add(new ManifestFileItem(name, path, size, url));
            totalManifestBytes += size;
        }

        Log.i(TAG, "Manifest parsed successfully: " + manifestList.size() + " files, " + (totalManifestBytes / (1024 * 1024)) + " MB");

        long overallDownloaded = 0;
        byte[] buffer = new byte[BUFFER_SIZE];

        for (int i = 0; i < manifestList.size(); i++) {
            if (sCancelRequested) {
                throw new Exception("Pengunduhan dibatalkan.");
            }

            ManifestFileItem item = manifestList.get(i);
            File targetFile = new File(destDir, item.path);
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            postExtracting(callback, "[" + (i + 1) + "/" + manifestList.size() + "] " + item.path);

            // If file already exists and has matching size, skip
            if (targetFile.exists() && targetFile.length() == item.size) {
                overallDownloaded += item.size;
                if (totalManifestBytes > 0) {
                    int percent = (int) ((overallDownloaded * 100) / totalManifestBytes);
                    postProgress(callback, percent, overallDownloaded, totalManifestBytes);
                }
                continue;
            }

            // Download file from CDN
            HttpURLConnection fileConn = null;
            try {
                fileConn = openConnectionWithRedirects(item.url);
                if (fileConn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Gagal mengunduh " + item.path + " (HTTP " + fileConn.getResponseCode() + ")");
                }

                try (InputStream in = new BufferedInputStream(fileConn.getInputStream());
                     OutputStream out = new FileOutputStream(targetFile)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        if (sCancelRequested) {
                            throw new Exception("Pengunduhan dibatalkan.");
                        }
                        out.write(buffer, 0, read);
                        overallDownloaded += read;
                        if (totalManifestBytes > 0) {
                            int percent = (int) ((overallDownloaded * 100) / totalManifestBytes);
                            postProgress(callback, percent, overallDownloaded, totalManifestBytes);
                        }
                    }
                    out.flush();
                }
            } finally {
                if (fileConn != null) {
                    fileConn.disconnect();
                }
            }
        }
    }

    /**
     * Downloads and unpacks monolithic ZIP file.
     */
    private static void downloadAndExtractZipInternal(String sourceUrl, File destDir, DownloadCallback callback) throws Exception {
        File tempZip = new File(destDir, "cache_temp.zip");
        try {
            Log.i(TAG, "Starting ZIP download from: " + sourceUrl);
            HttpURLConnection conn = openConnectionWithRedirects(sourceUrl);
            int responseCode = conn.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new Exception("File ZIP tidak ditemukan di server (HTTP 404).");
            } else if (responseCode == HttpURLConnection.HTTP_UNAVAILABLE) {
                throw new Exception("Server CDN sedang sibuk (HTTP 503).");
            } else if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new Exception("Server mengembalikan kode: HTTP " + responseCode);
            }

            long totalBytes = conn.getContentLengthLong();
            long downloadedBytes = 0;

            try (InputStream in = new BufferedInputStream(conn.getInputStream());
                 OutputStream out = new FileOutputStream(tempZip)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    if (sCancelRequested) {
                        throw new Exception("Pengunduhan dibatalkan.");
                    }
                    out.write(buffer, 0, bytesRead);
                    downloadedBytes += bytesRead;

                    if (totalBytes > 0) {
                        int percent = (int) ((downloadedBytes * 100) / totalBytes);
                        postProgress(callback, percent, downloadedBytes, totalBytes);
                    }
                }
                out.flush();
            }
            conn.disconnect();

            // Extract ZIP
            extractZip(tempZip, destDir, callback);

        } finally {
            if (tempZip.exists()) {
                tempZip.delete();
            }
        }
    }

    public static void extractLocalZip(final File localZipFile, final File destDir, final DownloadCallback callback) {
        if (!localZipFile.exists()) {
            postError(callback, "File zip lokal tidak ditemukan: " + localZipFile.getAbsolutePath());
            return;
        }

        sExecutor.execute(() -> {
            try {
                if (!destDir.exists()) destDir.mkdirs();
                extractZip(localZipFile, destDir, callback);
                postComplete(callback, destDir);
            } catch (Exception e) {
                postError(callback, "Ekstraksi gagal: " + e.getMessage());
            }
        });
    }

    private static void extractZip(File zipFile, File destDir, DownloadCallback callback) throws Exception {
        String canonicalDestDirPath = destDir.getCanonicalPath();

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            byte[] buffer = new byte[BUFFER_SIZE];

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                File targetFile = new File(destDir, entryName);

                // Zip Slip vulnerability check
                String canonicalTargetFilePath = targetFile.getCanonicalPath();
                if (!canonicalTargetFilePath.startsWith(canonicalDestDirPath + File.separator) && !canonicalTargetFilePath.equals(canonicalDestDirPath)) {
                    throw new SecurityException("Zip Slip entry attempted: " + entryName);
                }

                postExtracting(callback, entryName);

                if (entry.isDirectory()) {
                    targetFile.mkdirs();
                } else {
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, count);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static HttpURLConnection openConnectionWithRedirects(String sourceUrl) throws Exception {
        URL url = new URL(sourceUrl);
        int redirects = 0;
        HttpURLConnection conn = null;

        while (redirects < 5) {
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "*/*");
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM 
                    || responseCode == HttpURLConnection.HTTP_MOVED_TEMP 
                    || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                    || responseCode == 307 
                    || responseCode == 308) {
                String newLocation = conn.getHeaderField("Location");
                conn.disconnect();
                if (newLocation == null || newLocation.isEmpty()) {
                    throw new Exception("HTTP redirect tanpa header Location.");
                }
                url = new URL(newLocation);
                redirects++;
            } else {
                break;
            }
        }
        return conn;
    }

    // Deprecated alias for backwards compatibility
    public static void downloadAndExtract(final String sourceUrl, final File destDir, final DownloadCallback callback) {
        downloadFromCdnOrZip(sourceUrl, destDir, callback);
    }

    private static void postProgress(DownloadCallback callback, int percent, long current, long total) {
        if (callback == null) return;
        sMainHandler.post(() -> callback.onProgress(percent, current, total));
    }

    private static void postExtracting(DownloadCallback callback, String name) {
        if (callback == null) return;
        sMainHandler.post(() -> callback.onExtracting(name));
    }

    private static void postComplete(DownloadCallback callback, File dir) {
        if (callback == null) return;
        sMainHandler.post(() -> callback.onComplete(dir));
    }

    private static void postError(DownloadCallback callback, String error) {
        if (callback == null) return;
        sMainHandler.post(() -> callback.onError(error));
    }
}
