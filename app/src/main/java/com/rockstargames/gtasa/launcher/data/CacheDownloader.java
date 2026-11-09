package com.rockstargames.gtasa.launcher.data;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * In-App High-Performance Game Data (GTA SA Cache) Downloader & Extractor.
 * Downloads game assets (texdb, data, audio) from CDN and unpacks directly
 * into the app external storage without manual file copying.
 */
public class CacheDownloader {
    private static final String TAG = "CacheDownloader";
    private static final int BUFFER_SIZE = 32 * 1024; // 32KB chunk buffer

    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    public interface DownloadCallback {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        void onExtracting(String entryName);
        void onComplete(File destinationDir);
        void onError(String errorMessage);
    }

    private static volatile boolean sIsDownloading = false;

    public static boolean isDownloading() {
        return sIsDownloading;
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
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create mock cache: " + e.getMessage());
            return false;
        }
    }

    /**
     * Asynchronously extracts an existing local zip file into destDir.
     */
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

    /**
     * Asynchronously downloads a zip archive from sourceUrl and unpacks it into destDir.
     */
    public static void downloadAndExtract(final String sourceUrl, final File destDir, final DownloadCallback callback) {
        if (sIsDownloading) {
            postError(callback, "Download already in progress.");
            return;
        }

        sIsDownloading = true;

        sExecutor.execute(() -> {
            File tempZip = new File(destDir, "cache_temp.zip");
            try {
                if (!destDir.exists()) {
                    destDir.mkdirs();
                }

                // 1. Download Phase with multi-hop redirect and User-Agent
                Log.i(TAG, "Starting download from: " + sourceUrl);
                URL url = new URL(sourceUrl);
                int redirects = 0;
                HttpURLConnection conn = null;
                int responseCode = 0;

                while (redirects < 5) {
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setInstanceFollowRedirects(true);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    conn.setRequestProperty("Accept", "*/*");
                    conn.connect();

                    responseCode = conn.getResponseCode();
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

                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    throw new Exception("File tidak ditemukan di server (HTTP 404). Silakan periksa URL unduhan di tab Pengaturan.");
                } else if (responseCode == HttpURLConnection.HTTP_UNAVAILABLE) {
                    throw new Exception("Server CDN sedang sibuk / membatasi akses (HTTP 503). Silakan coba lagi nanti atau gunakan tombol 'Pasang dari ZIP Lokal'.");
                } else if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    throw new Exception("Akses server ditolak (HTTP 403 Forbidden). Tautan mungkin memerlukan izin login.");
                } else if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new Exception("Server mengembalikan kode: HTTP " + responseCode);
                }

                long totalBytes = conn.getContentLengthLong();
                long downloadedBytes = 0;

                try (InputStream in = new BufferedInputStream(conn.getInputStream());
                     OutputStream out = new FileOutputStream(tempZip)) {

                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;
                    int lastPercent = -1;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;

                        if (totalBytes > 0) {
                            int percent = (int) ((downloadedBytes * 100) / totalBytes);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                final long current = downloadedBytes;
                                postProgress(callback, percent, current, totalBytes);
                            }
                        }
                    }
                    out.flush();
                }
                conn.disconnect();

                Log.i(TAG, "Download finished (" + downloadedBytes + " bytes). Starting extraction...");

                // 2. Extraction Phase (Streaming ZipInputStream)
                extractZip(tempZip, destDir, callback);

                // 3. Cleanup temp file
                if (tempZip.exists()) {
                    tempZip.delete();
                }

                sIsDownloading = false;
                postComplete(callback, destDir);

            } catch (Exception e) {
                Log.e(TAG, "Download or extraction failed: " + e.getMessage(), e);
                if (tempZip.exists()) {
                    tempZip.delete();
                }
                sIsDownloading = false;
                postError(callback, e.getMessage());
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

