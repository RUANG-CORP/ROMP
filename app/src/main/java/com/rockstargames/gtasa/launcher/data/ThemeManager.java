package com.rockstargames.gtasa.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.core.widget.TextViewCompat;

import java.io.File;
import java.io.InputStream;

/**
 * Manages ROMP by HARFA visual theme, accent colors, hero background presets,
 * custom logo/background overrides, launcher language (ID/EN), and text scale.
 */
public class ThemeManager {
    private static final String PREF_NAME = "romp_theme_pref";
    private static final String KEY_ACCENT_RGBA = "accent_rgba";
    private static final String KEY_ACCENT_PRESET = "accent_preset";
    private static final String KEY_HERO_BG = "hero_background";
    private static final String KEY_CUSTOM_BG_PATH = "custom_bg_path";
    private static final String KEY_CUSTOM_LOGO_PATH = "custom_logo_path";
    private static final String KEY_USE_CUSTOM_LOGO = "custom_logo";
    private static final String KEY_LANGUAGE = "launcher_language";
    private static final String KEY_TEXT_SCALE = "launcher_text_scale";

    public static final int DEFAULT_ACCENT = Color.parseColor("#38BDF8");

    public static final class AccentPreset {
        public final String name;
        public final int color;
        public AccentPreset(String name, int color) {
            this.name = name;
            this.color = color;
        }
    }

    public static final AccentPreset[] PRESETS = {
            new AccentPreset("Harfa Cyan", Color.parseColor("#38BDF8")),
            new AccentPreset("Crimson Red", Color.parseColor("#EF4444")),
            new AccentPreset("Emerald Green", Color.parseColor("#10B981")),
            new AccentPreset("Gold Amber", Color.parseColor("#F59E0B")),
            new AccentPreset("Royal Violet", Color.parseColor("#8B5CF6")),
            new AccentPreset("Neon Rose", Color.parseColor("#EC4899")),
            new AccentPreset("Silver Frost", Color.parseColor("#CBD5E1"))
    };

    public static final String[][] BG_PRESETS = {
            {"home_calm", "Calm Dusk", "assets/bg/home_calm.webp"},
            {"home_harbor", "Los Santos Harbor", "assets/bg/home_harbor.webp"},
            {"home_hood", "Grove Neighborhood", "assets/bg/home_hood.webp"},
            {"splash_lowrider", "Lowrider Meet", "assets/bg/splash_lowrider.webp"},
            {"splash_neon", "Neon Night", "assets/bg/splash_neon.webp"},
            {"custom", "Custom Gallery Image", ""}
    };

    private final Context mContext;
    private final SharedPreferences mPrefs;

    public ThemeManager(Context context) {
        mContext = context.getApplicationContext();
        mPrefs = mContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public int getAccentColor() {
        return mPrefs.getInt(KEY_ACCENT_RGBA, DEFAULT_ACCENT);
    }

    public void setAccentColor(int rgba) {
        mPrefs.edit().putInt(KEY_ACCENT_RGBA, rgba).apply();
        syncThemeToGamePrefs();
    }

    public String getHeroBackgroundId() {
        return mPrefs.getString(KEY_HERO_BG, "home_calm");
    }

    public void setHeroBackgroundId(String id) {
        mPrefs.edit().putString(KEY_HERO_BG, id).apply();
    }

    public String getCustomBackgroundPath() {
        return mPrefs.getString(KEY_CUSTOM_BG_PATH, "");
    }

    public void setCustomBackgroundPath(String path) {
        mPrefs.edit().putString(KEY_CUSTOM_BG_PATH, path != null ? path : "").apply();
        if (path != null && !path.isEmpty()) {
            setHeroBackgroundId("custom");
        }
    }

    public String getCustomLogoPath() {
        return mPrefs.getString(KEY_CUSTOM_LOGO_PATH, "");
    }

    public void setCustomLogoPath(String path) {
        mPrefs.edit()
                .putString(KEY_CUSTOM_LOGO_PATH, path != null ? path : "")
                .putBoolean(KEY_USE_CUSTOM_LOGO, path != null && !path.isEmpty())
                .apply();
    }

    public boolean isCustomLogoEnabled() {
        return mPrefs.getBoolean(KEY_USE_CUSTOM_LOGO, false);
    }

    public String getLanguage() {
        return mPrefs.getString(KEY_LANGUAGE, "id");
    }

    public void setLanguage(String lang) {
        String clean = "en".equalsIgnoreCase(lang) ? "en" : "id";
        mPrefs.edit().putString(KEY_LANGUAGE, clean).apply();
        syncThemeToGamePrefs();
    }

    public float getTextScale() {
        return mPrefs.getFloat(KEY_TEXT_SCALE, 1.0f);
    }

    public void setTextScale(float scale) {
        float clamped = Math.max(0.85f, Math.min(1.25f, scale));
        mPrefs.edit().putFloat(KEY_TEXT_SCALE, clamped).apply();
    }

    public File getThemeStorageDir() {
        File base = mContext.getExternalFilesDir(null);
        if (base == null) base = mContext.getFilesDir();
        File dir = new File(base, "romp_theme");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public void syncThemeToGamePrefs() {
        int rgba = getAccentColor();
        String lang = getLanguage();
        SharedPreferences resa = mContext.getSharedPreferences("resa_samp", Context.MODE_PRIVATE);
        SharedPreferences client = mContext.getSharedPreferences("client_settings", Context.MODE_PRIVATE);
        resa.edit()
                .putInt("accent_rgba", rgba)
                .putString("launcher_language", lang)
                .apply();
        client.edit()
                .putInt("accent_rgba", rgba)
                .putString("launcher_language", lang)
                .apply();
    }

    public boolean isIndonesian() {
        return !"en".equalsIgnoreCase(getLanguage());
    }

    public String t(String idText, String enText) {
        return isIndonesian() ? idText : enText;
    }

    public String getHeroPresetLabel() {
        String id = getHeroBackgroundId();
        for (String[] row : BG_PRESETS) {
            if (row[0].equals(id)) return row[1];
        }
        return "Calm Dusk";
    }

    /** Returns accessible black/slate or crisp white foreground based on WCAG relative luminance. */
    public static int getContrastTextColor(int backgroundColor) {
        double r = Color.red(backgroundColor) / 255.0;
        double g = Color.green(backgroundColor) / 255.0;
        double b = Color.blue(backgroundColor) / 255.0;
        r = (r <= 0.03928) ? r / 12.92 : Math.pow((r + 0.055) / 1.055, 2.4);
        g = (g <= 0.03928) ? g / 12.92 : Math.pow((g + 0.055) / 1.055, 2.4);
        b = (b <= 0.03928) ? b / 12.92 : Math.pow((b + 0.055) / 1.055, 2.4);
        double lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        return lum > 0.36 ? Color.parseColor("#07111F") : Color.parseColor("#FFFFFF");
    }

    public static int darkenColor(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.max(0, Math.round(Color.red(color) * factor));
        int g = Math.max(0, Math.round(Color.green(color) * factor));
        int b = Math.max(0, Math.round(Color.blue(color) * factor));
        return Color.argb(a, r, g, b);
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    public static Drawable createCardDrawable(Context ctx, int fillColor, int strokeColor, int strokeDp, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setColor(fillColor);
        if (strokeDp > 0) {
            gd.setStroke(dp(ctx, strokeDp), strokeColor);
        }
        gd.setCornerRadius(dpF(ctx, radiusDp));
        return gd;
    }

    public static Drawable createInteractivePill(Context ctx, int fillColor, int strokeColor, int rippleColor, float radiusDp) {
        GradientDrawable content = new GradientDrawable();
        content.setShape(GradientDrawable.RECTANGLE);
        content.setColor(fillColor);
        if (strokeColor != Color.TRANSPARENT) {
            content.setStroke(dp(ctx, 1), strokeColor);
        }
        content.setCornerRadius(dpF(ctx, radiusDp));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
        }
        return content;
    }

    public static void stylePrimaryCta(Context ctx, TextView view, int accentColor) {
        if (view == null || ctx == null) return;
        int start = darkenColor(accentColor, 0.82f);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, accentColor});
        bg.setCornerRadius(dpF(ctx, 10));
        int fg = getContrastTextColor(start);
        view.setTextColor(fg);
        tintCompoundDrawables(view, fg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int ripple = fg == Color.WHITE ? 0x40FFFFFF : 0x30000000;
            view.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), bg, null));
        } else {
            view.setBackground(bg);
        }
    }

    public static void tintCompoundDrawables(TextView tv, int color) {
        if (tv == null) return;
        TextViewCompat.setCompoundDrawableTintList(tv, ColorStateList.valueOf(color));
    }

    public static int dp(Context ctx, float dp) {
        return Math.round(dpF(ctx, dp));
    }

    public static float dpF(Context ctx, float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics());
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /** Decodes an image file downsampled to fit reqWidth x reqHeight to prevent UI jank and OOM on phones. */
    public static Bitmap decodeSampledBitmapFromFile(String path, int reqWidth, int reqHeight) {
        if (path == null || path.isEmpty()) return null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, opts);
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;
            opts.inSampleSize = calculateInSampleSize(opts, reqWidth, reqHeight);
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(path, opts);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Decodes an asset image downsampled to fit reqWidth x reqHeight. */
    public static Bitmap decodeSampledBitmapFromAsset(Context ctx, String assetPath, int reqWidth, int reqHeight) {
        if (ctx == null || assetPath == null || assetPath.isEmpty()) return null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            try (InputStream is1 = ctx.getAssets().open(assetPath)) {
                BitmapFactory.decodeStream(is1, null, opts);
            }
            if (opts.outWidth <= 0 || opts.outHeight <= 0) return null;
            opts.inSampleSize = calculateInSampleSize(opts, reqWidth, reqHeight);
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            try (InputStream is2 = ctx.getAssets().open(assetPath)) {
                return BitmapFactory.decodeStream(is2, null, opts);
            }
        } catch (Throwable ignored) {
            return null;
        }
    }
}

