package com.rockstargames.gtasa.launcher.data;

import android.content.Context;
import android.util.Log;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses ReSA's native settings catalogue binary (RSC1 v1: catalogue_phone.bin)
 * containing all 142 display, game, audio, touch/gyro/keyboard controls, HUD,
 * SA-MP multiplayer, and voice settings.
 */
public class SettingsCatalogue {
    private static final String TAG = "SettingsCatalogue";
    private static final String ASSET_PATH = "flutter_assets/assets/settings/catalogue_phone.bin";

    public static final int TYPE_BOOL = 0;
    public static final int TYPE_INT_SLIDER = 1;
    public static final int TYPE_FLOAT_SLIDER = 2;
    public static final int TYPE_CHOICE = 3;
    public static final int TYPE_ACTION = 4;
    public static final int TYPE_KEY_BIND = 5;

    public static final int CAT_GAME = 0;
    public static final int CAT_DISPLAY = 1;
    public static final int CAT_AUDIO = 2;
    public static final int CAT_CONTROLS = 3;
    public static final int CAT_HUD = 4;
    public static final int CAT_SAMP = 5;
    public static final int CAT_VOICE = 6;
    public static final int CAT_LAUNCHER = 7;

    public static class Option {
        public final int value;
        public final String label;
        public Option(int value, String label) {
            this.value = value;
            this.label = label;
        }
    }

    public static class Item {
        public final int id;
        public final int category;
        public final int type;
        public final int visibility;
        public final int defaultRaw;
        public final float min;
        public final float max;
        public final float step;
        public final String key;
        public final String title;
        public final String description;
        public final List<Option> options;

        public Item(int id, int category, int type, int visibility, int defaultRaw,
                    float min, float max, float step, String key, String title,
                    String description, List<Option> options) {
            this.id = id;
            this.category = category;
            this.type = type;
            this.visibility = visibility;
            this.defaultRaw = defaultRaw;
            this.min = min;
            this.max = max;
            this.step = step;
            this.key = key;
            this.title = title;
            this.description = description;
            this.options = options != null ? options : Collections.emptyList();
        }
    }

    private static List<Item> sCached;

    public static synchronized List<Item> load(Context context) {
        if (sCached != null) return sCached;
        List<Item> list = new ArrayList<>();
        try (InputStream in = context.getAssets().open(ASSET_PATH)) {
            byte[] data = readAllBytes(in);
            list.addAll(parseBinary(data));
        } catch (Exception e) {
            Log.e(TAG, "Failed to load settings catalogue: " + e.getMessage(), e);
        }
        appendLauncherExtras(list);
        sCached = Collections.unmodifiableList(list);
        return sCached;
    }

    public static List<Item> parseBinary(byte[] data) {
        List<Item> items = new ArrayList<>();
        if (data == null || data.length < 16) return items;
        if (data[0] != 'R' || data[1] != 'S' || data[2] != 'C' || data[3] != '1') return items;

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(4);
        int ver = buf.getInt();
        int unk = buf.getInt();
        int count = buf.getInt();
        if (ver != 1 || count <= 0 || count > 1000) return items;

        for (int i = 0; i < count && buf.remaining() >= 36; i++) {
            int id = buf.getInt();
            int cat = buf.getInt();
            int typ = buf.getInt();
            int vis = buf.getInt();
            int defRaw = buf.getInt();
            float fMin = buf.getFloat();
            float fMax = buf.getFloat();
            float fStep = buf.getFloat();

            String key = readString(buf);
            String title = readString(buf);
            String desc = readString(buf);
            int optCount = buf.remaining() >= 4 ? buf.getInt() : 0;
            List<Option> opts = new ArrayList<>();
            if (optCount > 0 && optCount < 256) {
                for (int o = 0; o < optCount && buf.remaining() >= 8; o++) {
                    int oval = buf.getInt();
                    String olabel = readString(buf);
                    opts.add(new Option(oval, olabel));
                }
            }
            items.add(new Item(id, cat, typ, vis, defRaw, fMin, fMax, fStep, key, title, desc, opts));
        }
        return items;
    }

    private static void appendLauncherExtras(List<Item> list) {
        List<Option> rendererOpts = new ArrayList<>();
        rendererOpts.add(new Option(0, "Auto (Recommended)"));
        rendererOpts.add(new Option(1, "Impeller"));
        rendererOpts.add(new Option(2, "Skia"));
        list.add(new Item(9001, CAT_DISPLAY, TYPE_CHOICE, 1, 0, 0, 2, 1,
                "overlay_renderer", "In-Game Overlay Renderer",
                "Renderer backend used by InGameFlutterHost inside :game.", rendererOpts));

        list.add(new Item(9002, CAT_SAMP, TYPE_BOOL, 1, 1, 0, 1, 1,
                "al_loader", "AL Loader (Lua Scripts)",
                "Load .lua scripts and mimgui overlays from mods/AL Loader/scripts.", null));
        list.add(new Item(9003, CAT_SAMP, TYPE_BOOL, 1, 1, 0, 1, 1,
                "mod_loader", "Mod Loader (Models & Textures)",
                "Apply imported DFF/TXD/Sound/TexDB mods on game launch.", null));
        list.add(new Item(9004, CAT_SAMP, TYPE_BOOL, 1, 1, 0, 1, 1,
                "cef_overlay", "CEF HTML5 Web Overlay",
                "Enable embedded browser dialogs & custom server HUDs.", null));
        list.add(new Item(9005, CAT_SAMP, TYPE_BOOL, 1, 1, 0, 1, 1,
                "discord_rpc", "Discord Rich Presence",
                "Show your active ROMP server on your Discord profile when installed.", null));
        list.add(new Item(9006, CAT_HUD, TYPE_ACTION, 1, 0, 0, 1, 1,
                "open_crosshair_editor", "Crosshair Editor",
                "Customize crosshair style, size, thickness, color, or upload PNG image.", null));
        list.add(new Item(9007, CAT_HUD, TYPE_ACTION, 1, 0, 0, 1, 1,
                "open_font_picker", "Custom Font Manager",
                "Choose from 18 bundled fonts or import a custom .ttf/.otf font.", null));
        list.add(new Item(9008, CAT_LAUNCHER, TYPE_ACTION, 1, 0, 0, 1, 1,
                "open_theme_customizer", "Theme, Background & Language",
                "Customize ROMP by HARFA accent color, hero wallpaper, text scale & ID/EN.", null));
        list.add(new Item(9009, CAT_LAUNCHER, TYPE_ACTION, 1, 0, 0, 1, 1,
                "open_screenshots", "Screenshot Gallery",
                "View, save to gallery, or share SAMP/screenshots.", null));
        list.add(new Item(9010, CAT_LAUNCHER, TYPE_ACTION, 1, 0, 0, 1, 1,
                "open_logs", "Logs & Crash Diagnostics",
                "Inspect client.log, alloader.log, flutter.log, and crash tombstones.", null));
    }

    private static String readString(ByteBuffer buf) {
        if (buf.remaining() < 4) return "";
        int len = buf.getInt();
        if (len <= 0 || len > buf.remaining()) return "";
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] readAllBytes(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    public static String categoryTitle(int cat, boolean id) {
        switch (cat) {
            case CAT_GAME: return id ? "Permainan & Dunia" : "Game & World";
            case CAT_DISPLAY: return id ? "Tampilan & Grafis" : "Display & Graphics";
            case CAT_AUDIO: return id ? "Audio & Radio" : "Audio & Radio";
            case CAT_CONTROLS: return id ? "Kontrol, Gyro & Keybinds" : "Controls, Gyro & Keybinds";
            case CAT_HUD: return id ? "HUD, Crosshair & Font" : "HUD, Crosshair & Font";
            case CAT_SAMP: return id ? "Multipemain SA-MP" : "SA-MP Multiplayer";
            case CAT_VOICE: return id ? "Obrolan Suara (Voice Chat)" : "Voice Chat";
            default: return id ? "Tema, Galeri & Alat" : "Theme, Gallery & Tools";
        }
    }
}

