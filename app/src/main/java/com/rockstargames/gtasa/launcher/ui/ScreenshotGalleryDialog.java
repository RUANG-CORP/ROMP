package com.rockstargames.gtasa.launcher.ui;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.rockstargames.gtasa.launcher.data.ThemeManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.Locale;

/**
 * In-launcher gallery for in-game F8 / /ss / InGameScreenshot captures
 * stored under SAMP/screenshots.
 */
public class ScreenshotGalleryDialog {

    public static void show(Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) base = context.getFilesDir();
        File shotDir = new File(base, "SAMP/screenshots");
        if (!shotDir.exists()) shotDir.mkdirs();

        List<File> images = collectScreenshots(shotDir);
        if (images.isEmpty()) {
            new AlertDialog.Builder(context)
                    .setTitle("Galeri Screenshot ROMP")
                    .setMessage("Belum ada screenshot di:\n" + shotDir.getAbsolutePath()
                            + "\n\nGunakan perintah /ss, tombol kamera, atau F8 saat bermain untuk menyimpan tangkapan layar.")
                    .setPositiveButton("Tutup", null)
                    .show();
            return;
        }

        List<String> labels = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        for (File f : images) {
            labels.add(f.getName() + " (" + (f.length() / 1024) + " KB · " + fmt.format(new Date(f.lastModified())) + ")");
        }

        ListView lv = new ListView(context);
        lv.setBackgroundColor(Color.parseColor("#0F172A"));
        lv.setAdapter(new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, labels) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Color.parseColor("#F8FAFC"));
                tv.setTextSize(13);
                return tv;
            }
        });

        AlertDialog listDialog = new AlertDialog.Builder(context)
                .setTitle("Galeri Screenshot (" + images.size() + ")")
                .setView(lv)
                .setNegativeButton("Tutup", null)
                .create();

        lv.setOnItemClickListener((p, v, pos, id) -> showDetail(context, images.get(pos), listDialog));
        listDialog.show();
    }

    private static List<File> collectScreenshots(File dir) {
        List<File> list = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null) return list;
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : files) {
            String lower = f.getName().toLowerCase(Locale.US);
            if (f.isFile() && (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))) {
                list.add(f);
            }
        }
        return list;
    }

    private static void showDetail(Context context, File imgFile, AlertDialog parentDialog) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.BLACK);
        container.setPadding(12, 12, 12, 12);

        ImageView iv = new ImageView(context);
        iv.setAdjustViewBounds(true);
        iv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 420));
        container.addView(iv);
        Executors.newSingleThreadExecutor().execute(() -> {
            Bitmap bmp = ThemeManager.decodeSampledBitmapFromFile(imgFile.getAbsolutePath(), 1280, 720);
            if (bmp != null) {
                iv.post(() -> iv.setImageBitmap(bmp));
            }
        });

        new AlertDialog.Builder(context)
                .setTitle(imgFile.getName())
                .setView(container)
                .setPositiveButton("Simpan ke Galeri", (d, w) -> {
                    boolean ok = saveToDeviceGallery(context, imgFile);
                    Toast.makeText(context, ok ? "Disimpan ke Pictures/ROMP by HARFA" : "Gagal menyimpan ke galeri", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("Bagikan", (d, w) -> shareImage(context, imgFile))
                .setNegativeButton("Hapus", (d, w) -> {
                    if (imgFile.delete()) {
                        Toast.makeText(context, "Screenshot dihapus", Toast.LENGTH_SHORT).show();
                        parentDialog.dismiss();
                        show(context);
                    }
                })
                .show();
    }

    private static boolean saveToDeviceGallery(Context context, File imgFile) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, imgFile.getName());
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ROMP by HARFA");
                cv.put(MediaStore.Images.Media.IS_PENDING, 1);
            }
            Uri uri = context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return false;
            try (FileInputStream in = new FileInputStream(imgFile);
                 OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out == null) return false;
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cv.clear();
                cv.put(MediaStore.Images.Media.IS_PENDING, 0);
                context.getContentResolver().update(uri, cv, null, null);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void shareImage(Context context, File file) {
        try {
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/*");
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            context.startActivity(Intent.createChooser(i, "Bagikan Screenshot ROMP"));
        } catch (Exception e) {
            Toast.makeText(context, "Gagal membagikan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}

