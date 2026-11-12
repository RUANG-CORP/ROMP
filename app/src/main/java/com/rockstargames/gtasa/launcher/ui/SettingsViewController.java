package com.rockstargames.gtasa.launcher.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.rockstargames.gtasa.R;
import com.rockstargames.gtasa.launcher.data.FontManager;
import com.rockstargames.gtasa.launcher.data.SettingsCatalogue;
import com.rockstargames.gtasa.launcher.data.SettingsManager;
import com.rockstargames.gtasa.launcher.data.ThemeManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dynamically renders the searchable 142+ settings catalogue (Display, Game, Audio,
 * Controls/Gyro/47 Keybinds, HUD/Crosshair/Font, SA-MP, Voice Chat, and Launcher Tools).
 */
public class SettingsViewController {

    public interface HostActions {
        void onRequestMicPermission();
        void onRequestCrosshairImage();
        void onRequestCustomFont();
        void onRequestThemeUpdate();
        void onRequestCustomBg();
        void onRequestCustomLogo();
        void onResetSettingsUi();
    }

    private final Context mContext;
    private final SettingsManager mSettings;
    private final ThemeManager mTheme;
    private final FontManager mFontManager;
    private final LinearLayout mContainer;
    private final HostActions mActions;
    private EditText mSearchBox;
    private LinearLayout mListHolder;
    private String mQuery = "";
    private List<SettingsCatalogue.Item> mCachedItems;
    private final Handler mSearchHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRenderRunnable = this::renderList;

    public SettingsViewController(Context context, SettingsManager settings, ThemeManager theme,
                                  FontManager fontManager, LinearLayout container, HostActions actions) {
        mContext = context;
        mSettings = settings;
        mTheme = theme;
        mFontManager = fontManager;
        mContainer = container;
        mActions = actions;
    }

    public void buildUi() {
        if (mContainer == null) return;
        mContainer.removeAllViews();

        boolean id = mTheme.isIndonesian();
        int pad14 = ThemeManager.dp(mContext, 14);
        int gap8 = ThemeManager.dp(mContext, 8);
        int touch48 = ThemeManager.dp(mContext, 48);
        int accent = mTheme.getAccentColor();

        // Top Search & Quick Tools Row
        LinearLayout topBar = new LinearLayout(mContext);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = ThemeManager.dp(mContext, 12);
        topBar.setLayoutParams(tlp);

        mSearchBox = new EditText(mContext);
        mSearchBox.setHint(id ? "Cari setelan (fps, gyro, samp, keybind, voice)..." : "Search settings (fps, gyro, samp, keybind, voice)...");
        mSearchBox.setTextColor(Color.parseColor("#F8FAFC"));
        mSearchBox.setHintTextColor(Color.parseColor("#94A3B8"));
        mSearchBox.setTextSize(13);
        mSearchBox.setBackgroundResource(R.drawable.bg_search_bar);
        mSearchBox.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_romp_search, 0, 0, 0);
        mSearchBox.setCompoundDrawablePadding(ThemeManager.dp(mContext, 10));
        mSearchBox.setPadding(pad14, 0, pad14, 0);
        mSearchBox.setSingleLine(true);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, touch48, 1f);
        slp.rightMargin = gap8;
        mSearchBox.setLayoutParams(slp);
        topBar.addView(mSearchBox);

        Button btnMic = createPillButton(id ? "Izin Mic" : "Mic Perm",
                ThemeManager.withAlpha(accent, 42), accent, R.drawable.ic_romp_mic,
                v -> { if (mActions != null) mActions.onRequestMicPermission(); });
        ThemeManager.tintCompoundDrawables(btnMic, accent);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, touch48);
        mlp.rightMargin = gap8;
        btnMic.setLayoutParams(mlp);
        topBar.addView(btnMic);

        Button btnRestore = createPillButton(id ? "Reset Semua" : "Reset All",
                Color.parseColor("#3B141A"), Color.parseColor("#F87171"), 0,
                v -> confirmResetDefaults());
        btnRestore.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, touch48));
        topBar.addView(btnRestore);

        mContainer.addView(topBar);

        mListHolder = new LinearLayout(mContext);
        mListHolder.setOrientation(LinearLayout.VERTICAL);
        mContainer.addView(mListHolder);

        mSearchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                mQuery = s != null ? s.toString().trim().toLowerCase(Locale.US) : "";
                mSearchHandler.removeCallbacks(mRenderRunnable);
                mSearchHandler.postDelayed(mRenderRunnable, 120);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        renderList();
    }

    public void refresh() {
        renderList();
    }

    private void renderList() {
        if (mListHolder == null) return;
        mListHolder.removeAllViews();

        if (mCachedItems == null) {
            mCachedItems = SettingsCatalogue.load(mContext);
        }
        List<SettingsCatalogue.Item> items = mCachedItems;
        boolean id = mTheme.isIndonesian();
        int accent = mTheme.getAccentColor();
        Map<Integer, List<SettingsCatalogue.Item>> grouped = new LinkedHashMap<>();

        for (SettingsCatalogue.Item item : items) {
            if (!mQuery.isEmpty()) {
                boolean match = item.title.toLowerCase(Locale.US).contains(mQuery)
                        || item.key.toLowerCase(Locale.US).contains(mQuery)
                        || item.description.toLowerCase(Locale.US).contains(mQuery);
                if (!match) continue;
            }
            List<SettingsCatalogue.Item> bucket = grouped.get(item.category);
            if (bucket == null) {
                bucket = new ArrayList<>();
                grouped.put(item.category, bucket);
            }
            bucket.add(item);
        }

        if (grouped.isEmpty()) {
            TextView empty = new TextView(mContext);
            empty.setText(id ? "Tidak ada setelan yang cocok." : "No matching settings.");
            empty.setTextColor(Color.parseColor("#94A3B8"));
            empty.setTextSize(13);
            empty.setPadding(8, 24, 8, 24);
            mListHolder.addView(empty);
            return;
        }

        int[] orderedCats = {
                SettingsCatalogue.CAT_SAMP,
                SettingsCatalogue.CAT_DISPLAY,
                SettingsCatalogue.CAT_HUD,
                SettingsCatalogue.CAT_VOICE,
                SettingsCatalogue.CAT_CONTROLS,
                SettingsCatalogue.CAT_AUDIO,
                SettingsCatalogue.CAT_GAME,
                SettingsCatalogue.CAT_LAUNCHER
        };

        for (int cat : orderedCats) {
            List<SettingsCatalogue.Item> catItems = grouped.get(cat);
            if (catItems == null || catItems.isEmpty()) continue;

            LinearLayout card = new LinearLayout(mContext);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_card_section);
            int p16 = ThemeManager.dp(mContext, 16);
            card.setPadding(p16, p16, p16, ThemeManager.dp(mContext, 10));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = ThemeManager.dp(mContext, 12);
            card.setLayoutParams(clp);

            TextView header = new TextView(mContext);
            header.setText(SettingsCatalogue.categoryTitle(cat, id) + " (" + catItems.size() + ")");
            header.setTextColor(accent);
            header.setTextSize(14);
            header.setTypeface(null, Typeface.BOLD);
            header.setCompoundDrawablesRelativeWithIntrinsicBounds(categoryIconRes(cat), 0, 0, 0);
            header.setCompoundDrawablePadding(ThemeManager.dp(mContext, 8));
            ThemeManager.tintCompoundDrawables(header, accent);
            header.setPadding(0, 0, 0, ThemeManager.dp(mContext, 10));
            card.addView(header);

            for (int i = 0; i < catItems.size(); i++) {
                addItemWidget(card, catItems.get(i), accent);
                if (i < catItems.size() - 1) {
                    View div = new View(mContext);
                    div.setBackgroundColor(Color.parseColor("#1B283E"));
                    LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, ThemeManager.dp(mContext, 1));
                    dlp.topMargin = ThemeManager.dp(mContext, 6);
                    dlp.bottomMargin = ThemeManager.dp(mContext, 6);
                    div.setLayoutParams(dlp);
                    card.addView(div);
                }
            }

            mListHolder.addView(card);
        }
    }

    private void addItemWidget(LinearLayout parent, SettingsCatalogue.Item item, int accent) {
        LinearLayout row = new LinearLayout(mContext);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, ThemeManager.dp(mContext, 4), 0, ThemeManager.dp(mContext, 4));
        int touch44 = ThemeManager.dp(mContext, 44);

        if (item.type == SettingsCatalogue.TYPE_BOOL) {
            CheckBox cb = new CheckBox(mContext);
            cb.setText(item.title);
            cb.setTextColor(Color.parseColor("#F8FAFC"));
            cb.setTextSize(13);
            cb.setMinimumHeight(touch44);
            cb.setChecked(mSettings.getCatalogueBool(item.key, item.defaultRaw != 0));
            cb.setButtonTintList(ColorStateList.valueOf(accent));
            cb.setOnCheckedChangeListener((b, checked) -> {
                mSettings.setCatalogueBool(item.key, checked);
                if ("voice_chat".equals(item.key) && checked && mActions != null) {
                    mActions.onRequestMicPermission();
                }
            });
            row.addView(cb);
        } else if (item.type == SettingsCatalogue.TYPE_INT_SLIDER) {
            int min = Math.round(item.min);
            int max = Math.max(min + 1, Math.round(item.max));
            int cur = mSettings.getCatalogueInt(item.key, item.defaultRaw);
            if (cur < min) cur = min;
            if (cur > max) cur = max;

            TextView tv = new TextView(mContext);
            tv.setText(item.title + ": " + formatSliderValue(item.key, cur));
            tv.setTextColor(Color.parseColor("#F8FAFC"));
            tv.setTextSize(13);
            tv.setTypeface(null, Typeface.BOLD);
            row.addView(tv);

            SeekBar sb = createStyledSeekBar(accent);
            sb.setMax(max - min);
            sb.setProgress(cur - min);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                    int val = p + min;
                    tv.setText(item.title + ": " + formatSliderValue(item.key, val));
                    if (fromUser) mSettings.setCatalogueInt(item.key, val);
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            row.addView(sb);
        } else if (item.type == SettingsCatalogue.TYPE_FLOAT_SLIDER) {
            int minI = Math.round(item.min * 100f);
            int maxI = Math.max(minI + 1, Math.round(item.max * 100f));
            float defF = Float.intBitsToFloat(item.defaultRaw);
            if (Float.isNaN(defF) || defF < item.min || defF > item.max) defF = item.min;
            float curF = mSettings.getCatalogueFloat(item.key, defF);
            int curI = Math.max(minI, Math.min(maxI, Math.round(curF * 100f)));

            TextView tv = new TextView(mContext);
            tv.setText(String.format(Locale.US, "%s: %.2f", item.title, curI / 100f));
            tv.setTextColor(Color.parseColor("#F8FAFC"));
            tv.setTextSize(13);
            tv.setTypeface(null, Typeface.BOLD);
            row.addView(tv);

            SeekBar sb = createStyledSeekBar(accent);
            sb.setMax(maxI - minI);
            sb.setProgress(curI - minI);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                    float val = (p + minI) / 100f;
                    tv.setText(String.format(Locale.US, "%s: %.2f", item.title, val));
                    if (fromUser) mSettings.setCatalogueFloat(item.key, val);
                }
                @Override public void onStartTrackingTouch(SeekBar s) {}
                @Override public void onStopTrackingTouch(SeekBar s) {}
            });
            row.addView(sb);
        } else if (item.type == SettingsCatalogue.TYPE_CHOICE) {
            int curVal = mSettings.getCatalogueInt(item.key, item.defaultRaw);
            String curLabel = findOptionLabel(item, curVal);

            LinearLayout h = new LinearLayout(mContext);
            h.setOrientation(LinearLayout.HORIZONTAL);
            h.setGravity(Gravity.CENTER_VERTICAL);

            TextView tv = new TextView(mContext);
            tv.setText(item.title);
            tv.setTextColor(Color.parseColor("#F8FAFC"));
            tv.setTextSize(13);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            h.addView(tv);

            Button btnChoice = createPillButton(curLabel,
                    Color.parseColor("#192438"), Color.parseColor("#F8FAFC"), 0, null);
            btnChoice.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, touch44));
            btnChoice.setOnClickListener(v -> showChoiceDialog(item, btnChoice::setText));
            h.addView(btnChoice);
            row.addView(h);
        } else if (item.type == SettingsCatalogue.TYPE_KEY_BIND) {
            String defBind = !item.options.isEmpty() ? item.options.get(0).label : "NONE";
            String curBind = mSettings.getKeyBindingString(item.key, defBind);

            LinearLayout h = new LinearLayout(mContext);
            h.setOrientation(LinearLayout.HORIZONTAL);
            h.setGravity(Gravity.CENTER_VERTICAL);

            TextView tv = new TextView(mContext);
            tv.setText(item.description + " · " + item.title);
            tv.setTextColor(Color.parseColor("#CBD5E1"));
            tv.setTextSize(12);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            h.addView(tv);

            Button btnKey = createPillButton(curBind, Color.parseColor("#192438"), accent, 0, null);
            btnKey.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, touch44));
            btnKey.setOnClickListener(v -> promptKeyBind(item, btnKey));
            h.addView(btnKey);
            row.addView(h);
        } else if (item.type == SettingsCatalogue.TYPE_ACTION) {
            Button btnAct = new Button(mContext);
            btnAct.setText(item.title);
            btnAct.setAllCaps(false);
            btnAct.setTextSize(13);
            btnAct.setTypeface(null, Typeface.BOLD);
            btnAct.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            btnAct.setPadding(ThemeManager.dp(mContext, 16), 0, ThemeManager.dp(mContext, 16), 0);
            btnAct.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ThemeManager.dp(mContext, 46)));
            ThemeManager.stylePrimaryCta(mContext, btnAct, accent);
            btnAct.setOnClickListener(v -> handleAction(item.key));
            row.addView(btnAct);
        }

        if (item.description != null && !item.description.isEmpty() && item.type != SettingsCatalogue.TYPE_KEY_BIND) {
            TextView tvDesc = new TextView(mContext);
            tvDesc.setText(item.description);
            tvDesc.setTextColor(Color.parseColor("#94A3B8"));
            tvDesc.setTextSize(11);
            tvDesc.setPadding(ThemeManager.dp(mContext, 4), ThemeManager.dp(mContext, 4), 0, 0);
            row.addView(tvDesc);
        }

        parent.addView(row);
    }

    private SeekBar createStyledSeekBar(int accent) {
        SeekBar sb = new SeekBar(mContext);
        sb.setMinimumHeight(ThemeManager.dp(mContext, 40));
        sb.setProgressTintList(ColorStateList.valueOf(accent));
        sb.setThumbTintList(ColorStateList.valueOf(accent));
        return sb;
    }

    private int categoryIconRes(int cat) {
        switch (cat) {
            case SettingsCatalogue.CAT_SAMP: return R.drawable.ic_romp_globe;
            case SettingsCatalogue.CAT_DISPLAY: return R.drawable.ic_romp_image;
            case SettingsCatalogue.CAT_HUD: return R.drawable.ic_romp_sliders;
            case SettingsCatalogue.CAT_VOICE: return R.drawable.ic_romp_mic;
            case SettingsCatalogue.CAT_CONTROLS: return R.drawable.ic_romp_gamepad;
            case SettingsCatalogue.CAT_GAME: return R.drawable.ic_romp_play;
            default: return R.drawable.ic_romp_settings;
        }
    }

    private interface LabelCallback {
        void onPicked(String label);
    }

    private void showChoiceDialog(SettingsCatalogue.Item item, LabelCallback cb) {
        if (item.options.isEmpty()) return;
        String[] labels = new String[item.options.size()];
        for (int i = 0; i < item.options.size(); i++) labels[i] = item.options.get(i).label;
        new AlertDialog.Builder(mContext)
                .setTitle(item.title)
                .setItems(labels, (d, which) -> {
                    SettingsCatalogue.Option chosen = item.options.get(which);
                    mSettings.setCatalogueInt(item.key, chosen.value);
                    if (cb != null) cb.onPicked(chosen.label);
                })
                .show();
    }

    private void promptKeyBind(SettingsCatalogue.Item item, Button btnTarget) {
        String[] commonKeys = {"W", "A", "S", "D", "SPACE", "LSHIFT", "LCTRL", "RCTRL", "LALT",
                "E", "Q", "F", "G", "H", "C", "V", "X", "Y", "N", "TAB", "RETURN",
                "LMB", "RMB", "MMB", "UP", "DOWN", "LEFT", "RIGHT", "NUM0", "NUM2", "NUM4", "NUM6", "NUM8"};
        new AlertDialog.Builder(mContext)
                .setTitle("Bind: " + item.title)
                .setItems(commonKeys, (d, which) -> {
                    String chosen = commonKeys[which];
                    mSettings.setKeyBindingString(item.key, chosen);
                    btnTarget.setText(chosen);
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void handleAction(String key) {
        switch (key) {
            case "open_crosshair_editor":
                CrosshairEditorDialog.show(mContext, mSettings, () -> {
                    if (mActions != null) mActions.onRequestCrosshairImage();
                });
                break;
            case "open_font_picker":
                FontPickerDialog.show(mContext, mFontManager, () -> {
                    if (mActions != null) mActions.onRequestCustomFont();
                });
                break;
            case "open_theme_customizer":
                ThemeCustomizerDialog.show(mContext, mTheme, new ThemeCustomizerDialog.ThemeCallback() {
                    @Override public void onThemeUpdated() { if (mActions != null) mActions.onRequestThemeUpdate(); }
                    @Override public void onRequestCustomBackgroundPick() { if (mActions != null) mActions.onRequestCustomBg(); }
                    @Override public void onRequestCustomLogoPick() { if (mActions != null) mActions.onRequestCustomLogo(); }
                });
                break;
            case "open_screenshots":
                ScreenshotGalleryDialog.show(mContext);
                break;
            case "open_logs":
                LogsViewerDialog.show(mContext);
                break;
            case "adjust_controls_hud":
            case "adjust_widgets":
                Toast.makeText(mContext, "Membuka Editor Tata Letak Kontrol & HUD...", Toast.LENGTH_SHORT).show();
                mSettings.launchLayoutEditor(mContext);
                break;
            case "scan_user_tracks":
                Toast.makeText(mContext, "Pemindaian User Tracks akan berjalan di sesi berikutnya.", Toast.LENGTH_SHORT).show();
                mSettings.setCatalogueBool("user_track_auto_scan", true);
                break;
            case "bind_restore_defaults":
                confirmResetDefaults();
                break;
        }
    }

    private void confirmResetDefaults() {
        new AlertDialog.Builder(mContext)
                .setTitle("Reset Semua Setelan?")
                .setMessage("Kembalikan 142 setelan gim, grafis, suara, gyro, dan keybind ke nilai standar?")
                .setPositiveButton("Ya, Reset", (d, w) -> {
                    mSettings.resetCatalogueDefaults(mContext);
                    renderList();
                    if (mActions != null) mActions.onResetSettingsUi();
                    Toast.makeText(mContext, "Semua setelan dikembalikan ke default.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private static String findOptionLabel(SettingsCatalogue.Item item, int val) {
        for (SettingsCatalogue.Option o : item.options) {
            if (o.value == val) return o.label;
        }
        return !item.options.isEmpty() ? item.options.get(0).label : String.valueOf(val);
    }

    private static String formatSliderValue(String key, int val) {
        if ("fps_limit".equals(key) && val > 240) return "Unlimited";
        return String.valueOf(val);
    }

    private Button createPillButton(String text, int fillColor, int fgColor, int iconRes,
                                    View.OnClickListener listener) {
        Button b = new Button(mContext);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(fgColor);
        b.setTextSize(12);
        b.setTypeface(null, Typeface.BOLD);
        int border = Color.parseColor("#3B4F73");
        b.setBackground(ThemeManager.createInteractivePill(mContext, fillColor, border,
                ThemeManager.withAlpha(fgColor, 50), 10));
        if (iconRes != 0) {
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
            b.setCompoundDrawablePadding(ThemeManager.dp(mContext, 6));
        }
        int p14 = ThemeManager.dp(mContext, 14);
        b.setPadding(p14, 0, p14, 0);
        if (listener != null) b.setOnClickListener(listener);
        return b;
    }
}
