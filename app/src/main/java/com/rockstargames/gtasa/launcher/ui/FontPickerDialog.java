package com.rockstargames.gtasa.launcher.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.rockstargames.gtasa.launcher.data.FontManager;

import java.io.File;
import java.util.List;

/**
 * Custom Font Selector & Importer matching ReSA's InGameFontSection (18+ bundled fonts + custom .ttf/.otf).
 */
public class FontPickerDialog {

    public interface PickFontCallback {
        void onRequestCustomFontPick();
    }

    public static void show(Context context, FontManager fontManager, PickFontCallback picker) {
        List<FontManager.FontEntry> entries = fontManager.listAvailableFonts();
        String active = fontManager.getActiveFontFileName();

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(Color.parseColor("#0F172A"));
        container.setPadding(16, 12, 16, 8);

        TextView header = new TextView(context);
        header.setText("Font aktif: " + (active.isEmpty() ? "Default Engine" : active));
        header.setTextColor(Color.parseColor("#38BDF8"));
        header.setTextSize(12);
        header.setPadding(8, 8, 8, 12);
        container.addView(header);

        ListView lv = new ListView(context);
        lv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 460));
        ArrayAdapter<FontManager.FontEntry> adapter = new ArrayAdapter<FontManager.FontEntry>(context, android.R.layout.simple_list_item_2, android.R.id.text1, entries) {
            @NonNull
            @Override
            public android.view.View getView(int position, android.view.View convertView, @NonNull ViewGroup parent) {
                android.view.View v = super.getView(position, convertView, parent);
                FontManager.FontEntry item = entries.get(position);
                TextView t1 = v.findViewById(android.R.id.text1);
                TextView t2 = v.findViewById(android.R.id.text2);
                boolean isSelected = active.equals(item.fileName);
                t1.setText((isSelected ? "[Aktif] " : "") + item.displayName);
                t1.setTextColor(isSelected ? Color.parseColor("#22C55E") : Color.parseColor("#F8FAFC"));
                t2.setText("The quick brown fox jumps · 123456 [HARFA]");
                t2.setTextColor(Color.parseColor("#94A3B8"));
                try {
                    if (item.isCustom && !item.assetOrFilePath.isEmpty()) {
                        t2.setTypeface(Typeface.createFromFile(new File(item.assetOrFilePath)));
                    } else if (!item.assetOrFilePath.isEmpty()) {
                        t2.setTypeface(Typeface.createFromAsset(context.getAssets(), item.assetOrFilePath));
                    } else {
                        t2.setTypeface(Typeface.DEFAULT);
                    }
                } catch (Exception ignored) {
                    t2.setTypeface(Typeface.DEFAULT);
                }
                return v;
            }
        };
        lv.setAdapter(adapter);
        container.addView(lv);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("Pilih / Impor Font Kustom")
                .setView(container)
                .setNeutralButton("Impor .TTF/.OTF...", (d, w) -> {
                    if (picker != null) picker.onRequestCustomFontPick();
                })
                .setNegativeButton("Tutup", null)
                .create();

        lv.setOnItemClickListener((parent, view, pos, id) -> {
            FontManager.FontEntry chosen = entries.get(pos);
            if (fontManager.activateFont(chosen)) {
                Toast.makeText(context, "Font diaktifkan: " + chosen.displayName, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(context, "Gagal menerapkan font", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }
}

