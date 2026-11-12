package com.rockstargames.gtasa.launcher.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * In-app text/code editor for .ini, .cfg, .lua, .json, .txt config and script files.
 */
public class CodeEditorDialog {

    public interface OnSavedListener {
        void onSaved(File file);
    }

    public static void open(Context context, File file, OnSavedListener listener) {
        if (file == null || !file.exists() || !file.isFile()) {
            Toast.makeText(context, "Berkas tidak ditemukan.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (file.length() > 512 * 1024) {
            Toast.makeText(context, "Berkas terlalu besar untuk editor (>512 KB).", Toast.LENGTH_LONG).show();
            return;
        }

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
        } catch (Exception e) {
            Toast.makeText(context, "Gagal membaca berkas: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.parseColor("#0B1120"));
        container.setPadding(16, 12, 16, 12);

        TextView tvMeta = new TextView(context);
        tvMeta.setText(file.getName() + " · " + file.length() + " bytes");
        tvMeta.setTextColor(Color.parseColor("#38BDF8"));
        tvMeta.setTextSize(11);
        tvMeta.setPadding(4, 0, 4, 8);
        container.addView(tvMeta);

        EditText et = new EditText(context);
        et.setTypeface(Typeface.MONOSPACE);
        et.setTextSize(11);
        et.setTextColor(Color.parseColor("#E2E8F0"));
        et.setBackgroundColor(Color.parseColor("#0F172A"));
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setPadding(14, 14, 14, 14);
        et.setHorizontallyScrolling(false);
        et.setText(content.toString());

        ScrollView scroll = new ScrollView(context);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 480));
        scroll.addView(et);
        container.addView(scroll);

        new AlertDialog.Builder(context)
                .setTitle("Editor: " + file.getName())
                .setView(container)
                .setNegativeButton("Tutup", null)
                .setPositiveButton("Simpan", (d, w) -> {
                    try (FileOutputStream fos = new FileOutputStream(file, false)) {
                        fos.write(et.getText().toString().getBytes(StandardCharsets.UTF_8));
                        fos.flush();
                        Toast.makeText(context, "Disimpan: " + file.getName(), Toast.LENGTH_SHORT).show();
                        if (listener != null) listener.onSaved(file);
                    } catch (Exception e) {
                        Toast.makeText(context, "Gagal menyimpan: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }
}

