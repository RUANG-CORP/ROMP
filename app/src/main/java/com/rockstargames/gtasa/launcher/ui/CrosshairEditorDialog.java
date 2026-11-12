package com.rockstargames.gtasa.launcher.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.rockstargames.gtasa.R;
import com.rockstargames.gtasa.launcher.data.SettingsManager;
import com.rockstargames.gtasa.launcher.data.ThemeManager;

import java.io.File;

/**
 * Interactive Visual Crosshair Editor matching ReSA's CrosshairSheet:
 * Style presets, Size, Thickness, Opacity, Outline, Color swatches,
 * Custom PNG picker, and live preview canvas.
 */
public class CrosshairEditorDialog {

    public interface ImagePickDelegate {
        void onRequestCrosshairImagePick();
    }

    public static void show(Context context, SettingsManager settings, ImagePickDelegate pickerDelegate) {
        final int[] style = {settings.getCrosshairStyle()};
        final int[] size = {settings.getCrosshairSize()};
        final int[] thickness = {settings.getCrosshairThickness()};
        final int[] opacity = {settings.getCrosshairOpacity()};
        final int[] outline = {settings.getCrosshairOutline()};
        final int[] color = {settings.getCrosshairColor()};
        final String[] imgPath = {settings.getCrosshairImagePath()};
        final int accent = new ThemeManager(context).getAccentColor();
        final Bitmap[] customBmp = {
                (imgPath[0] != null && !imgPath[0].isEmpty())
                        ? ThemeManager.decodeSampledBitmapFromFile(imgPath[0], 256, 256)
                        : null
        };

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ThemeManager.dp(context, 18), ThemeManager.dp(context, 14),
                ThemeManager.dp(context, 18), ThemeManager.dp(context, 14));
        root.setBackgroundColor(Color.parseColor("#0F172A"));

        View preview = new View(context) {
            final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint chPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Paint olPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                int w = getWidth(), h = getHeight();
                bgPaint.setColor(Color.parseColor("#1E293B"));
                canvas.drawRect(0, 0, w, h, bgPaint);

                // Subtle sky/road horizon grid for contrast reference
                bgPaint.setColor(Color.parseColor("#334155"));
                canvas.drawLine(0, h / 2f, w, h / 2f, bgPaint);
                canvas.drawLine(w / 2f, 0, w / 2f, h, bgPaint);

                int alpha = Math.max(15, Math.min(255, (opacity[0] * 255) / 100));
                if (style[0] == 4 && customBmp[0] != null && !customBmp[0].isRecycled()) {
                    int sz = Math.max(24, size[0] * 3);
                    chPaint.setAlpha(alpha);
                    canvas.drawBitmap(customBmp[0], null, new Rect(w/2 - sz/2, h/2 - sz/2, w/2 + sz/2, h/2 + sz/2), chPaint);
                    return;
                }

                float cx = w / 2f, cy = h / 2f;
                float r = Math.max(8, size[0] * 1.4f);
                float gap = Math.max(3, thickness[0] * 1.5f);

                chPaint.setColor(color[0]);
                chPaint.setAlpha(alpha);
                chPaint.setStrokeWidth(Math.max(1.5f, thickness[0]));
                chPaint.setStyle(Paint.Style.STROKE);

                olPaint.setColor(Color.BLACK);
                olPaint.setAlpha(alpha);
                olPaint.setStrokeWidth(chPaint.getStrokeWidth() + 3f);
                olPaint.setStyle(Paint.Style.STROKE);

                if (style[0] == 3) {
                    // Dot only
                    chPaint.setStyle(Paint.Style.FILL);
                    if (outline[0] == 1) canvas.drawCircle(cx, cy, Math.max(3, thickness[0] + 2), olPaint);
                    canvas.drawCircle(cx, cy, Math.max(2, thickness[0]), chPaint);
                } else if (style[0] == 1) {
                    // Circle + Dot
                    if (outline[0] == 1) canvas.drawCircle(cx, cy, r, olPaint);
                    canvas.drawCircle(cx, cy, r, chPaint);
                    chPaint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx, cy, Math.max(2, thickness[0] * 0.8f), chPaint);
                } else {
                    // Standard 4-arm / T-Cross (style 2 omits top arm)
                    if (outline[0] == 1) drawArms(canvas, cx, cy, r, gap, style[0] == 2, olPaint);
                    drawArms(canvas, cx, cy, r, gap, style[0] == 2, chPaint);
                }
            }

            private void drawArms(Canvas c, float cx, float cy, float r, float gap, boolean tShape, Paint p) {
                c.drawLine(cx - r, cy, cx - gap, cy, p);
                c.drawLine(cx + gap, cy, cx + r, cy, p);
                c.drawLine(cx, cy + gap, cx, cy + r, p);
                if (!tShape) c.drawLine(cx, cy - r, cx, cy - gap, p);
            }
        };
        LinearLayout.LayoutParams prevLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 170);
        prevLp.bottomMargin = 14;
        preview.setLayoutParams(prevLp);
        root.addView(preview);

        // Style row
        LinearLayout styleRow = new LinearLayout(context);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] styleNames = {"Cross", "Circle", "T-Cross", "Dot", "PNG"};
        int pillH = ThemeManager.dp(context, 44);
        int gap4 = ThemeManager.dp(context, 4);
        for (int i = 0; i < styleNames.length; i++) {
            final int idx = i;
            Button b = new Button(context);
            b.setText(styleNames[i]);
            b.setAllCaps(false);
            b.setTextSize(11);
            b.setTypeface(null, Typeface.BOLD);
            b.setTextColor(style[0] == idx ? accent : Color.parseColor("#CBD5E1"));
            b.setBackground(ThemeManager.createInteractivePill(context,
                    style[0] == idx ? ThemeManager.withAlpha(accent, 42) : Color.parseColor("#192438"),
                    style[0] == idx ? accent : Color.parseColor("#263652"),
                    ThemeManager.withAlpha(accent, 50), 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, pillH, 1f);
            lp.setMargins(gap4, 0, gap4, ThemeManager.dp(context, 10));
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                style[0] = idx;
                for (int k = 0; k < styleRow.getChildCount(); k++) {
                    Button kb = (Button) styleRow.getChildAt(k);
                    boolean active = k == idx;
                    kb.setTextColor(active ? accent : Color.parseColor("#CBD5E1"));
                    kb.setBackground(ThemeManager.createInteractivePill(context,
                            active ? ThemeManager.withAlpha(accent, 42) : Color.parseColor("#192438"),
                            active ? accent : Color.parseColor("#263652"),
                            ThemeManager.withAlpha(accent, 50), 10));
                }
                preview.invalidate();
            });
            styleRow.addView(b);
        }
        root.addView(styleRow);

        addSlider(context, root, "Ukuran (Size)", size[0], 6, 50, accent, val -> { size[0] = val; preview.invalidate(); });
        addSlider(context, root, "Ketebalan (Thickness)", thickness[0], 1, 10, accent, val -> { thickness[0] = val; preview.invalidate(); });
        addSlider(context, root, "Opasitas (Opacity %)", opacity[0], 10, 100, accent, val -> { opacity[0] = val; preview.invalidate(); });

        CheckBox cbOutline = new CheckBox(context);
        cbOutline.setText("Outline Hitam");
        cbOutline.setTextColor(Color.parseColor("#F8FAFC"));
        cbOutline.setMinimumHeight(ThemeManager.dp(context, 44));
        cbOutline.setButtonTintList(ColorStateList.valueOf(accent));
        cbOutline.setChecked(outline[0] == 1);
        cbOutline.setOnCheckedChangeListener((b, c) -> { outline[0] = c ? 1 : 0; preview.invalidate(); });
        root.addView(cbOutline);

        TextView tvCol = new TextView(context);
        tvCol.setText("Warna Crosshair:");
        tvCol.setTextColor(Color.parseColor("#CBD5E1"));
        tvCol.setTextSize(12);
        tvCol.setTypeface(null, Typeface.BOLD);
        root.addView(tvCol);

        LinearLayout colorRow = new LinearLayout(context);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setPadding(0, ThemeManager.dp(context, 8), 0, ThemeManager.dp(context, 12));
        int[] palette = {Color.WHITE, Color.parseColor("#38BDF8"), Color.parseColor("#10B981"),
                Color.parseColor("#EF4444"), Color.parseColor("#FACC15"), Color.parseColor("#EC4899")};
        for (int c : palette) {
            View sw = new View(context);
            sw.setBackground(ThemeManager.createInteractivePill(
                    context, c, Color.parseColor("#3B4F73"), 0x40FFFFFF, 24));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ThemeManager.dp(context, 44), ThemeManager.dp(context, 44));
            slp.setMargins(0, 0, ThemeManager.dp(context, 8), 0);
            sw.setLayoutParams(slp);
            sw.setOnClickListener(v -> { color[0] = c; preview.invalidate(); });
            colorRow.addView(sw);
        }
        root.addView(colorRow);

        Button btnPickImg = new Button(context);
        btnPickImg.setText(imgPath[0].isEmpty() ? "Impor Gambar Crosshair PNG..." : "Ganti Gambar (" + new File(imgPath[0]).getName() + ")");
        btnPickImg.setAllCaps(false);
        btnPickImg.setTextColor(Color.parseColor("#F8FAFC"));
        btnPickImg.setTextSize(12);
        btnPickImg.setTypeface(null, Typeface.BOLD);
        btnPickImg.setBackground(ThemeManager.createInteractivePill(
                context, Color.parseColor("#192438"), Color.parseColor("#3B4F73"), 0x3338BDF8, 10));
        btnPickImg.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_romp_image, 0, 0, 0);
        btnPickImg.setCompoundDrawablePadding(ThemeManager.dp(context, 8));
        btnPickImg.setPadding(ThemeManager.dp(context, 14), 0, ThemeManager.dp(context, 14), 0);
        btnPickImg.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ThemeManager.dp(context, 46)));
        btnPickImg.setOnClickListener(v -> {
            if (pickerDelegate != null) pickerDelegate.onRequestCrosshairImagePick();
        });
        root.addView(btnPickImg);

        ScrollView scroll = new ScrollView(context);
        scroll.addView(root);

        new AlertDialog.Builder(context)
                .setTitle("Editor Crosshair ROMP")
                .setView(scroll)
                .setNeutralButton("Reset Default", (d, w) -> {
                    settings.saveCrosshairConfig(0, 22, 3, 100, 1, Color.WHITE, "");
                    Toast.makeText(context, "Crosshair direset ke bawaan.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .setPositiveButton("Simpan Crosshair", (d, w) -> {
                    settings.saveCrosshairConfig(style[0], size[0], thickness[0], opacity[0], outline[0], color[0], imgPath[0]);
                    Toast.makeText(context, "Pengaturan Crosshair disimpan!", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private static void addSlider(Context ctx, LinearLayout parent, String label, int initial, int min, int max, int accent, final OnChange cb) {
        TextView tv = new TextView(ctx);
        tv.setText(label + ": " + initial);
        tv.setTextColor(Color.parseColor("#CBD5E1"));
        tv.setTextSize(12);
        tv.setTypeface(null, Typeface.BOLD);
        parent.addView(tv);

        SeekBar sb = new SeekBar(ctx);
        sb.setMinimumHeight(ThemeManager.dp(ctx, 40));
        sb.setProgressTintList(ColorStateList.valueOf(accent));
        sb.setThumbTintList(ColorStateList.valueOf(accent));
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, initial - min));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int p, boolean fromUser) {
                int v = p + min;
                tv.setText(label + ": " + v);
                cb.onChange(v);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        parent.addView(sb);
    }

    private interface OnChange {
        void onChange(int val);
    }
}

