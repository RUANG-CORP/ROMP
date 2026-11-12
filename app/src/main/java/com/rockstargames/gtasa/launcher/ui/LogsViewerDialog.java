package com.rockstargames.gtasa.launcher.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.rockstargames.gtasa.launcher.data.CrashLogHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays runtime engine logs, AL Loader logs, Flutter logs, and historical crash tombstones.
 */
public class LogsViewerDialog {

    public static void show(Context context) {
        List<CrashLogHelper.LogEntry> logs = CrashLogHelper.listLogFiles(context);
        String tombstones = CrashLogHelper.getRecentTombstoneSummary(context);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.parseColor("#0F172A"));
        container.setPadding(16, 12, 16, 12);

        if (!tombstones.isEmpty()) {
            TextView tvTomb = new TextView(context);
            tvTomb.setText("Riwayat Crash Sistem Terbaru:\n" + tombstones);
            tvTomb.setTextColor(Color.parseColor("#FBBF24"));
            tvTomb.setTextSize(12);
            tvTomb.setBackgroundColor(Color.parseColor("#1E293B"));
            tvTomb.setPadding(12, 10, 12, 10);
            container.addView(tvTomb);
        }

        if (logs.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("\nBelum ada berkas log.\nLog akan dibuat otomatis setelah menjalankan permainan.");
            empty.setTextColor(Color.parseColor("#94A3B8"));
            empty.setTextSize(13);
            empty.setPadding(8, 16, 8, 16);
            container.addView(empty);
        } else {
            List<String> titles = new ArrayList<>();
            for (CrashLogHelper.LogEntry e : logs) {
                titles.add(e.title + " (" + Math.max(1, e.sizeBytes / 1024) + " KB)");
            }
            ListView lv = new ListView(context);
            lv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 420));
            lv.setAdapter(new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, titles) {
                @Override
                public android.view.View getView(int position, android.view.View convertView, ViewGroup parent) {
                    TextView tv = (TextView) super.getView(position, convertView, parent);
                    tv.setTextColor(Color.parseColor("#F8FAFC"));
                    tv.setTextSize(13);
                    return tv;
                }
            });
            lv.setOnItemClickListener((p, v, pos, id) -> openLogContent(context, logs.get(pos)));
            container.addView(lv);
        }

        new AlertDialog.Builder(context)
                .setTitle("Log & Diagnostik ROMP")
                .setView(container)
                .setNeutralButton("Bersihkan Semua Log", (d, w) -> {
                    int n = CrashLogHelper.clearAllLogs(context);
                    Toast.makeText(context, n + " berkas log dibersihkan.", Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("Tutup", null)
                .show();
    }

    private static void openLogContent(Context context, CrashLogHelper.LogEntry entry) {
        String content = CrashLogHelper.readTail(entry.file, 64 * 1024);

        TextView tv = new TextView(context);
        tv.setText(content);
        tv.setTextColor(Color.parseColor("#CBD5E1"));
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(10);
        tv.setPadding(16, 12, 16, 12);
        tv.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Color.parseColor("#0B1120"));
        scroll.addView(tv);

        new AlertDialog.Builder(context)
                .setTitle("📄 " + entry.title)
                .setView(scroll)
                .setNeutralButton("Salin", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(entry.title, content));
                    Toast.makeText(context, "Log disalin ke clipboard!", Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton("Bagikan File", (d, w) -> {
                    try {
                        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", entry.file);
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("text/plain");
                        i.putExtra(Intent.EXTRA_STREAM, uri);
                        i.putExtra(Intent.EXTRA_SUBJECT, "ROMP Log - " + entry.file.getName());
                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        context.startActivity(Intent.createChooser(i, "Bagikan Log"));
                    } catch (Exception e) {
                        Toast.makeText(context, "Gagal membagikan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Tutup", null)
                .show();
    }
}

