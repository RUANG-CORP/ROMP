package com.rockstargames.gtasa.launcher.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.rockstargames.gtasa.R;
import com.rockstargames.gtasa.launcher.data.ThemeManager;

/**
 * Customizes launcher & in-game accent color, hero background wallpaper, custom image,
 * and interface language (Indonesia / English).
 */
public class ThemeCustomizerDialog {

    public interface ThemeCallback {
        void onThemeUpdated();
        void onRequestCustomBackgroundPick();
        void onRequestCustomLogoPick();
    }

    public static void show(Context context, ThemeManager tm, ThemeCallback callback) {
        int accent = tm.getAccentColor();
        int pad16 = ThemeManager.dp(context, 16);
        int gap8 = ThemeManager.dp(context, 8);
        int btnH = ThemeManager.dp(context, 46);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad16, pad16, pad16, pad16);
        root.setBackgroundColor(Color.parseColor("#0F172A"));

        TextView tvAcc = sectionTitle(context, "WARNA AKSEN (ACCENT COLOR)", accent);
        root.addView(tvAcc);

        LinearLayout swatchRow = new LinearLayout(context);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setPadding(0, ThemeManager.dp(context, 8), 0, ThemeManager.dp(context, 12));
        for (ThemeManager.AccentPreset p : ThemeManager.PRESETS) {
            View sw = new View(context);
            boolean selected = p.color == accent;
            sw.setBackground(ThemeManager.createInteractivePill(
                    context, p.color, selected ? Color.WHITE : Color.parseColor("#3B4F73"),
                    0x40FFFFFF, 24));
            sw.setContentDescription("Warna tema " + p.name);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ThemeManager.dp(context, 44), ThemeManager.dp(context, 44));
            lp.setMargins(0, 0, gap8, 0);
            sw.setLayoutParams(lp);
            sw.setOnClickListener(v -> {
                tm.setAccentColor(p.color);
                if (callback != null) callback.onThemeUpdated();
                Toast.makeText(context, "Warna tema: " + p.name, Toast.LENGTH_SHORT).show();
            });
            swatchRow.addView(sw);
        }
        root.addView(swatchRow);

        LinearLayout hexRow = new LinearLayout(context);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText etHex = new EditText(context);
        etHex.setHint("#RRGGBB (contoh: #10B981)");
        etHex.setText(String.format("#%06X", (0xFFFFFF & accent)));
        etHex.setTextColor(Color.parseColor("#F8FAFC"));
        etHex.setHintTextColor(Color.parseColor("#94A3B8"));
        etHex.setBackgroundResource(R.drawable.bg_input_field);
        etHex.setPadding(ThemeManager.dp(context, 14), 0, ThemeManager.dp(context, 14), 0);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(0, ThemeManager.dp(context, 48), 1f);
        hlp.rightMargin = gap8;
        etHex.setLayoutParams(hlp);

        Button btnHex = createOptionBtn(context, "Terapkan Hex",
                Color.parseColor("#192438"), Color.parseColor("#3B4F73"), Color.parseColor("#F8FAFC"),
                ThemeManager.dp(context, 48));
        btnHex.setOnClickListener(v -> {
            try {
                int c = Color.parseColor(etHex.getText().toString().trim());
                tm.setAccentColor(c);
                if (callback != null) callback.onThemeUpdated();
                Toast.makeText(context, "Warna kustom diterapkan!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(context, "Format Hex tidak valid!", Toast.LENGTH_SHORT).show();
            }
        });
        hexRow.addView(etHex);
        hexRow.addView(btnHex);
        root.addView(hexRow);

        TextView tvBg = sectionTitle(context, "WALLPAPER HERO", accent);
        tvBg.setPadding(0, ThemeManager.dp(context, 16), 0, ThemeManager.dp(context, 8));
        root.addView(tvBg);

        for (String[] preset : ThemeManager.BG_PRESETS) {
            if ("custom".equals(preset[0])) continue;
            boolean active = preset[0].equals(tm.getHeroBackgroundId());
            Button b = createOptionBtn(context,
                    active ? preset[1] + " (Aktif)" : preset[1],
                    active ? ThemeManager.withAlpha(accent, 40) : Color.parseColor("#141E30"),
                    active ? accent : Color.parseColor("#263652"),
                    active ? accent : Color.parseColor("#F8FAFC"),
                    btnH);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, btnH);
            blp.bottomMargin = gap8;
            b.setLayoutParams(blp);
            b.setOnClickListener(v -> {
                tm.setHeroBackgroundId(preset[0]);
                if (callback != null) callback.onThemeUpdated();
                Toast.makeText(context, "Wallpaper: " + preset[1], Toast.LENGTH_SHORT).show();
            });
            root.addView(b);
        }

        Button btnPickBg = new Button(context);
        btnPickBg.setText("Pilih Wallpaper Kustom dari Galeri");
        btnPickBg.setAllCaps(false);
        btnPickBg.setTextSize(13);
        btnPickBg.setTypeface(null, Typeface.BOLD);
        btnPickBg.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_romp_image, 0, 0, 0);
        btnPickBg.setCompoundDrawablePadding(ThemeManager.dp(context, 8));
        btnPickBg.setPadding(pad16, 0, pad16, 0);
        ThemeManager.stylePrimaryCta(context, btnPickBg, accent);
        LinearLayout.LayoutParams bgLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, btnH);
        bgLp.bottomMargin = gap8;
        btnPickBg.setLayoutParams(bgLp);
        btnPickBg.setOnClickListener(v -> {
            if (callback != null) callback.onRequestCustomBackgroundPick();
        });
        root.addView(btnPickBg);

        Button btnPickLogo = createOptionBtn(context,
                tm.isCustomLogoEnabled() ? "Ganti / Hapus Logo Kustom" : "Pilih Logo Kustom",
                Color.parseColor("#192438"), Color.parseColor("#3B4F73"), Color.parseColor("#F8FAFC"), btnH);
        btnPickLogo.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_romp_shield, 0, 0, 0);
        btnPickLogo.setCompoundDrawablePadding(ThemeManager.dp(context, 8));
        ThemeManager.tintCompoundDrawables(btnPickLogo, Color.parseColor("#F8FAFC"));
        btnPickLogo.setOnClickListener(v -> {
            if (callback != null) callback.onRequestCustomLogoPick();
        });
        root.addView(btnPickLogo);

        TextView tvLang = sectionTitle(context, "BAHASA LAUNCHER (LANGUAGE)", accent);
        tvLang.setPadding(0, ThemeManager.dp(context, 16), 0, ThemeManager.dp(context, 8));
        root.addView(tvLang);

        LinearLayout langRow = new LinearLayout(context);
        langRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnId = createOptionBtn(context, "Bahasa Indonesia",
                tm.isIndonesian() ? Color.parseColor("#19382E") : Color.parseColor("#141E30"),
                tm.isIndonesian() ? Color.parseColor("#22C55E") : Color.parseColor("#263652"),
                Color.parseColor("#F8FAFC"), btnH);
        LinearLayout.LayoutParams idLp = new LinearLayout.LayoutParams(0, btnH, 1f);
        idLp.rightMargin = gap8;
        btnId.setLayoutParams(idLp);
        btnId.setOnClickListener(v -> {
            tm.setLanguage("id");
            if (callback != null) callback.onThemeUpdated();
            Toast.makeText(context, "Bahasa diatur ke Indonesia", Toast.LENGTH_SHORT).show();
        });

        Button btnEn = createOptionBtn(context, "English",
                !tm.isIndonesian() ? Color.parseColor("#19382E") : Color.parseColor("#141E30"),
                !tm.isIndonesian() ? Color.parseColor("#22C55E") : Color.parseColor("#263652"),
                Color.parseColor("#F8FAFC"), btnH);
        btnEn.setLayoutParams(new LinearLayout.LayoutParams(0, btnH, 1f));
        btnEn.setOnClickListener(v -> {
            tm.setLanguage("en");
            if (callback != null) callback.onThemeUpdated();
            Toast.makeText(context, "Language set to English", Toast.LENGTH_SHORT).show();
        });
        langRow.addView(btnId);
        langRow.addView(btnEn);
        root.addView(langRow);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(root);

        new AlertDialog.Builder(context)
                .setTitle("Kustomisasi Tema & Tampilan ROMP")
                .setView(scroll)
                .setPositiveButton("Selesai", null)
                .show();
    }

    private static TextView sectionTitle(Context ctx, String text, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(12);
        tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private static Button createOptionBtn(Context ctx, String text, int fill, int border, int fg, int h) {
        Button b = new Button(ctx);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(12);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackground(ThemeManager.createInteractivePill(ctx, fill, border, ThemeManager.withAlpha(fg, 48), 10));
        b.setPadding(ThemeManager.dp(ctx, 14), 0, ThemeManager.dp(ctx, 14), 0);
        b.setMinHeight(h);
        return b;
    }
}
