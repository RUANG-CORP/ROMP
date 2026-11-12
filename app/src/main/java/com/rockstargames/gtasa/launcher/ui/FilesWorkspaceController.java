package com.rockstargames.gtasa.launcher.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.rockstargames.gtasa.R;
import com.rockstargames.gtasa.launcher.data.AlLoaderManager;
import com.rockstargames.gtasa.launcher.data.ModLoaderManager;
import com.rockstargames.gtasa.launcher.data.SettingsManager;
import com.rockstargames.gtasa.launcher.data.ThemeManager;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Manages the extended BERKAS workspace sub-tabs:
 * - File Manager + Code Editor + System Files (SAF DocumentsProvider)
 * - Mod Loader (DFF/TXD/PNG/Sound/Modpack ZIP)
 * - AL Loader (Lua script manager, 7 recommended scripts, API reference, alloader.log)
 * - Game Textures (texdb ZIP installer & cleanup) + Screenshots / Logs quick shortcuts
 */
public class FilesWorkspaceController {

    public interface PickerDelegate {
        void onRequestImportGenericFile(File targetDir);
        void onRequestImportModFile();
        void onRequestImportModpackZip();
        void onRequestImportLuaScript();
        void onRequestImportTexdbZip();
    }

    private final Context mContext;
    private final SettingsManager mSettings;
    private final ThemeManager mTheme;
    private final LinearLayout mWorkspaceContainer;
    private final PickerDelegate mPickers;

    private File mCurrentBrowseDir;
    private int mActiveSubTab = 0; // 0=FileManager, 1=ModLoader, 2=LuaScripts, 3=TexturesTools

    public FilesWorkspaceController(Context context, SettingsManager settings,
                                    LinearLayout workspaceContainer, PickerDelegate pickers) {
        mContext = context;
        mSettings = settings;
        mTheme = new ThemeManager(context);
        mWorkspaceContainer = workspaceContainer;
        mPickers = pickers;
        File base = context.getExternalFilesDir(null);
        mCurrentBrowseDir = base != null ? base : context.getFilesDir();
        if (!mCurrentBrowseDir.exists()) mCurrentBrowseDir.mkdirs();
    }

    public void render() {
        if (mWorkspaceContainer == null) return;
        mWorkspaceContainer.removeAllViews();

        int accent = mTheme.getAccentColor();
        int pad4 = ThemeManager.dp(mContext, 4);
        int tabH = ThemeManager.dp(mContext, 46);

        // Segmented glass sub-tab strip
        LinearLayout tabs = new LinearLayout(mContext);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundResource(R.drawable.bg_segmented_track);
        tabs.setPadding(pad4, pad4, pad4, pad4);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = ThemeManager.dp(mContext, 10);
        tabs.setLayoutParams(tlp);

        String[] names = {"Manajer Berkas", "Mod Loader", "AL Loader (Lua)", "Tekstur & Alat"};
        int[] icons = {R.drawable.ic_romp_folder, R.drawable.ic_romp_mod, R.drawable.ic_romp_code, R.drawable.ic_romp_image};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            boolean active = (mActiveSubTab == idx);
            Button b = new Button(mContext);
            b.setText(names[i]);
            b.setAllCaps(false);
            b.setTextSize(12);
            b.setTypeface(null, Typeface.BOLD);
            int fg = active ? accent : Color.parseColor("#CBD5E1");
            b.setTextColor(fg);
            if (active) {
                b.setBackground(ThemeManager.createInteractivePill(
                        mContext, ThemeManager.withAlpha(accent, 38), ThemeManager.withAlpha(accent, 110),
                        ThemeManager.withAlpha(accent, 60), 10));
            } else {
                b.setBackgroundResource(R.drawable.bg_nav_item_unselected);
            }
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(icons[i], 0, 0, 0);
            b.setCompoundDrawablePadding(ThemeManager.dp(mContext, 6));
            ThemeManager.tintCompoundDrawables(b, fg);
            b.setPadding(ThemeManager.dp(mContext, 10), 0, ThemeManager.dp(mContext, 10), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, tabH, 1f);
            lp.setMargins(pad4, 0, pad4, 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                mActiveSubTab = idx;
                render();
            });
            tabs.addView(b);
        }
        mWorkspaceContainer.addView(tabs);

        LinearLayout body = new LinearLayout(mContext);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setBackgroundResource(R.drawable.bg_card_section);
        int p16 = ThemeManager.dp(mContext, 16);
        body.setPadding(p16, p16, p16, p16);
        mWorkspaceContainer.addView(body);

        if (mActiveSubTab == 0) renderFileManager(body);
        else if (mActiveSubTab == 1) renderModLoader(body);
        else if (mActiveSubTab == 2) renderAlLoader(body);
        else renderTexturesAndTools(body);
    }

    // =========================================================================
    // Sub-tab 0: In-App File Manager + Code Editor + System Files App
    // =========================================================================
    private void renderFileManager(LinearLayout body) {
        File base = mContext.getExternalFilesDir(null);
        if (base == null) base = mContext.getFilesDir();
        int accent = mTheme.getAccentColor();

        TextView tvPath = new TextView(mContext);
        tvPath.setText("Lokasi: " + mCurrentBrowseDir.getAbsolutePath());
        tvPath.setTextColor(accent);
        tvPath.setTextSize(12);
        tvPath.setTypeface(null, Typeface.BOLD);
        tvPath.setPadding(0, 0, 0, ThemeManager.dp(mContext, 10));
        body.addView(tvPath);

        HorizontalScrollView scrollActions = new HorizontalScrollView(mContext);
        scrollActions.setHorizontalScrollBarEnabled(false);
        LinearLayout actions = new LinearLayout(mContext);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, 0, 0, ThemeManager.dp(mContext, 12));
        if (!mCurrentBrowseDir.equals(base) && mCurrentBrowseDir.getParentFile() != null) {
            actions.addView(secondaryBtn("Naik", 0, v -> {
                mCurrentBrowseDir = mCurrentBrowseDir.getParentFile();
                render();
            }));
        }
        actions.addView(secondaryBtn("Folder Baru", R.drawable.ic_romp_plus, v -> promptCreateEntry(true)));
        actions.addView(secondaryBtn("Berkas Baru", R.drawable.ic_romp_plus, v -> promptCreateEntry(false)));
        actions.addView(accentBtn("Impor Berkas", R.drawable.ic_romp_folder, accent, v -> {
            if (mPickers != null) mPickers.onRequestImportGenericFile(mCurrentBrowseDir);
        }));
        actions.addView(pillBtn("Buka di Files", Color.parseColor("#19382E"), Color.parseColor("#22C55E"),
                Color.parseColor("#34D399"), R.drawable.ic_romp_share, v -> openInSystemFilesApp()));
        scrollActions.addView(actions);
        body.addView(scrollActions);

        File[] files = mCurrentBrowseDir.listFiles();
        if (files == null || files.length == 0) {
            TextView empty = new TextView(mContext);
            empty.setText("(Folder kosong)");
            empty.setTextColor(Color.parseColor("#94A3B8"));
            empty.setTextSize(13);
            empty.setPadding(ThemeManager.dp(mContext, 8), ThemeManager.dp(mContext, 16),
                    ThemeManager.dp(mContext, 8), ThemeManager.dp(mContext, 16));
            body.addView(empty);
            return;
        }
        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        int touch44 = ThemeManager.dp(mContext, 44);
        int pad12 = ThemeManager.dp(mContext, 12);
        for (File f : files) {
            LinearLayout row = new LinearLayout(mContext);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(pad12, ThemeManager.dp(mContext, 4), ThemeManager.dp(mContext, 6), ThemeManager.dp(mContext, 4));
            row.setMinimumHeight(touch44);
            row.setBackground(ThemeManager.createInteractivePill(
                    mContext, Color.parseColor("#0B1322"), Color.parseColor("#1E2C44"),
                    ThemeManager.withAlpha(accent, 40), 10));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = ThemeManager.dp(mContext, 8);
            row.setLayoutParams(rlp);

            TextView tv = new TextView(mContext);
            String label = f.isDirectory()
                    ? f.getName() + "/"
                    : f.getName() + " (" + Math.max(1, f.length() / 1024) + " KB)";
            tv.setText(label);
            int fg = f.isDirectory() ? accent : Color.parseColor("#F8FAFC");
            tv.setTextColor(fg);
            tv.setTextSize(13);
            if (f.isDirectory()) tv.setTypeface(null, Typeface.BOLD);
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    f.isDirectory() ? R.drawable.ic_romp_folder : R.drawable.ic_romp_changelog, 0, 0, 0);
            tv.setCompoundDrawablePadding(ThemeManager.dp(mContext, 10));
            ThemeManager.tintCompoundDrawables(tv, fg);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setMinimumHeight(touch44);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tv.setOnClickListener(v -> {
                if (f.isDirectory()) {
                    mCurrentBrowseDir = f;
                    render();
                } else if (isEditableText(f.getName())) {
                    CodeEditorDialog.open(mContext, f, saved -> render());
                } else if (f.getName().toLowerCase(Locale.US).endsWith(".dff") || f.getName().toLowerCase(Locale.US).endsWith(".txd")) {
                    new AlertDialog.Builder(mContext)
                            .setTitle("Inspeksi Model / TXD")
                            .setMessage(f.getName() + "\n\n" + ModLoaderManager.inspectRenderWareHeader(f))
                            .setPositiveButton("Tutup", null)
                            .show();
                } else {
                    showFileOptions(f);
                }
            });
            row.addView(tv);

            Button btnOpt = secondaryBtn("Opsi", 0, v -> showFileOptions(f));
            LinearLayout.LayoutParams olp = (LinearLayout.LayoutParams) btnOpt.getLayoutParams();
            olp.rightMargin = 0;
            btnOpt.setLayoutParams(olp);
            row.addView(btnOpt);
            body.addView(row);
        }
    }

    private void showFileOptions(File f) {
        String[] opts = f.isDirectory()
                ? new String[]{"Buka Folder", "Ubah Nama", "Hapus Folder"}
                : new String[]{"Edit Teks / Kode", "Bagikan", "Ubah Nama", "Hapus Berkas"};
        new AlertDialog.Builder(mContext)
                .setTitle(f.getName())
                .setItems(opts, (d, which) -> {
                    String action = opts[which];
                    if (action.startsWith("Buka")) {
                        mCurrentBrowseDir = f;
                        render();
                    } else if (action.startsWith("Edit")) {
                        CodeEditorDialog.open(mContext, f, saved -> render());
                    } else if ("Bagikan".equals(action)) {
                        try {
                            Uri uri = FileProvider.getUriForFile(mContext, mContext.getPackageName() + ".fileprovider", f);
                            Intent i = new Intent(Intent.ACTION_SEND);
                            i.setType("*/*");
                            i.putExtra(Intent.EXTRA_STREAM, uri);
                            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            mContext.startActivity(Intent.createChooser(i, "Bagikan " + f.getName()));
                        } catch (Exception e) {
                            Toast.makeText(mContext, "Gagal bagikan: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else if (action.startsWith("Ubah Nama")) {
                        EditText et = new EditText(mContext);
                        et.setText(f.getName());
                        et.setTextColor(Color.WHITE);
                        new AlertDialog.Builder(mContext)
                                .setTitle("Ubah Nama")
                                .setView(et)
                                .setPositiveButton("Simpan", (d2, w2) -> {
                                    String name = et.getText().toString().trim();
                                    if (!name.isEmpty() && f.renameTo(new File(f.getParentFile(), name))) render();
                                })
                                .setNegativeButton("Batal", null)
                                .show();
                    } else if (action.startsWith("Hapus")) {
                        deleteRecursive(f);
                        render();
                    }
                })
                .show();
    }

    private void promptCreateEntry(boolean isDir) {
        EditText et = new EditText(mContext);
        et.setHint(isDir ? "Nama folder baru" : "Nama berkas (mis. script.lua / config.ini)");
        et.setTextColor(Color.WHITE);
        et.setHintTextColor(Color.parseColor("#94A3B8"));
        new AlertDialog.Builder(mContext)
                .setTitle(isDir ? "Buat Folder Baru" : "Buat Berkas Baru")
                .setView(et)
                .setPositiveButton("Buat", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty() || name.contains("/") || name.contains("..")) return;
                    File target = new File(mCurrentBrowseDir, name);
                    try {
                        if (isDir) target.mkdirs();
                        else target.createNewFile();
                        render();
                    } catch (Exception e) {
                        Toast.makeText(mContext, "Gagal: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void openInSystemFilesApp() {
        try {
            Uri rootUri = DocumentsContract.buildRootUri(mContext.getPackageName() + ".documents", "romp_files_root");
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(rootUri, "vnd.android.document/root");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            mContext.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(mContext, "Buka Android Files dan pilih 'ROMP by HARFA' di bilah samping!", Toast.LENGTH_LONG).show();
        }
    }

    // =========================================================================
    // Sub-tab 1: Mod Loader (DFF/TXD/PNG/Sounds/Modpack ZIP)
    // =========================================================================
    private void renderModLoader(LinearLayout body) {
        int accent = mTheme.getAccentColor();
        CheckBox cbMaster = new CheckBox(mContext);
        cbMaster.setText("Gunakan Mod (Mod Loader Aktif)");
        cbMaster.setTextColor(Color.parseColor("#F8FAFC"));
        cbMaster.setTextSize(13);
        cbMaster.setMinimumHeight(ThemeManager.dp(mContext, 48));
        cbMaster.setButtonTintList(ColorStateList.valueOf(accent));
        cbMaster.setChecked(mSettings.isModLoaderEnabled());
        cbMaster.setOnCheckedChangeListener((b, checked) -> {
            mSettings.setModLoaderEnabled(checked);
            render();
        });
        body.addView(cbMaster);

        TextView tvHint = new TextView(mContext);
        tvHint.setText("Dukungan penuh: .dff, .txd, .png, .wav, .mp3, timecyc.dat, txdassign.txt, dan arsip Modpack (.zip). Skrip PC (.cs/.asi/CLEO) tidak dijalankan demi stabilitas engine.");
        tvHint.setTextColor(Color.parseColor("#94A3B8"));
        tvHint.setTextSize(12);
        tvHint.setPadding(0, ThemeManager.dp(mContext, 4), 0, ThemeManager.dp(mContext, 12));
        body.addView(tvHint);

        HorizontalScrollView scroll = new HorizontalScrollView(mContext);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout btns = new LinearLayout(mContext);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, 0, 0, ThemeManager.dp(mContext, 12));
        btns.addView(accentBtn("Tambah File Mod", R.drawable.ic_romp_plus, accent, v -> {
            if (mPickers != null) mPickers.onRequestImportModFile();
        }));
        btns.addView(pillBtn("Impor Modpack (.zip)", Color.parseColor("#19382E"), Color.parseColor("#22C55E"),
                Color.parseColor("#34D399"), R.drawable.ic_romp_mod, v -> {
            if (mPickers != null) mPickers.onRequestImportModpackZip();
        }));
        scroll.addView(btns);
        body.addView(scroll);

        List<ModLoaderManager.ModEntry> mods = ModLoaderManager.listMods(mContext);
        if (mods.isEmpty()) {
            TextView tvEmpty = new TextView(mContext);
            tvEmpty.setText("Belum ada mod terpasang di mods/modpack/.");
            tvEmpty.setTextColor(Color.parseColor("#94A3B8"));
            tvEmpty.setTextSize(12);
            body.addView(tvEmpty);
            return;
        }

        for (ModLoaderManager.ModEntry mod : mods) {
            LinearLayout row = new LinearLayout(mContext);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(ThemeManager.createCardDrawable(
                    mContext, Color.parseColor("#0B1322"), Color.parseColor("#1E2C44"), 1, 10));
            row.setPadding(ThemeManager.dp(mContext, 12), ThemeManager.dp(mContext, 10),
                    ThemeManager.dp(mContext, 10), ThemeManager.dp(mContext, 10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = ThemeManager.dp(mContext, 8);
            row.setLayoutParams(lp);

            TextView tv = new TextView(mContext);
            tv.setText("[" + mod.category + "] " + mod.relativePath + "\n" + Math.max(1, mod.sizeBytes / 1024) + " KB · " + (mod.enabled ? "Aktif" : "Nonaktif"));
            tv.setTextColor(mod.enabled ? Color.parseColor("#F8FAFC") : Color.parseColor("#94A3B8"));
            tv.setTextSize(12);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            tv.setOnClickListener(v -> {
                if (mod.category.contains("Model") || mod.category.contains("TXD")) {
                    new AlertDialog.Builder(mContext)
                            .setTitle("Inspeksi Aset 3D")
                            .setMessage(mod.relativePath + "\n\n" + ModLoaderManager.inspectRenderWareHeader(mod.file))
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
            row.addView(tv);

            row.addView(secondaryBtn(mod.enabled ? "Matikan" : "Nyalakan", 0, v -> {
                ModLoaderManager.toggleModEntry(mod);
                render();
            }));
            Button delBtn = dangerBtn("Hapus", v -> {
                mod.file.delete();
                render();
            });
            LinearLayout.LayoutParams dlp = (LinearLayout.LayoutParams) delBtn.getLayoutParams();
            dlp.rightMargin = 0;
            delBtn.setLayoutParams(dlp);
            row.addView(delBtn);
            body.addView(row);
        }
    }

    // =========================================================================
    // Sub-tab 2: AL Loader (Lua Scripts, 7 Built-in Recommended, API Docs, Log)
    // =========================================================================
    private void renderAlLoader(LinearLayout body) {
        int accent = mTheme.getAccentColor();
        CheckBox cbAl = new CheckBox(mContext);
        cbAl.setText("Aktifkan AL Loader (Lua Scripts & mimgui Overlays)");
        cbAl.setTextColor(Color.parseColor("#F8FAFC"));
        cbAl.setTextSize(13);
        cbAl.setMinimumHeight(ThemeManager.dp(mContext, 48));
        cbAl.setButtonTintList(ColorStateList.valueOf(accent));
        cbAl.setChecked(mSettings.isAlLoaderEnabled());
        cbAl.setOnCheckedChangeListener((b, c) -> mSettings.setAlLoaderEnabled(c));
        body.addView(cbAl);

        HorizontalScrollView scroll = new HorizontalScrollView(mContext);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout toolsRow = new LinearLayout(mContext);
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        toolsRow.setPadding(0, ThemeManager.dp(mContext, 6), 0, ThemeManager.dp(mContext, 12));
        toolsRow.addView(accentBtn("Impor .lua", R.drawable.ic_romp_plus, accent, v -> {
            if (mPickers != null) mPickers.onRequestImportLuaScript();
        }));
        toolsRow.addView(secondaryBtn("Lihat alloader.log", R.drawable.ic_romp_terminal, v -> showAlLoaderLog()));
        toolsRow.addView(secondaryBtn("Referensi API Lua", R.drawable.ic_romp_code, v -> showApiDocsDialog()));
        scroll.addView(toolsRow);
        body.addView(scroll);

        TextView tvInst = new TextView(mContext);
        tvInst.setText("SKRIP TERPASANG (mods/AL Loader/scripts/)");
        tvInst.setTextColor(accent);
        tvInst.setTextSize(12);
        tvInst.setTypeface(null, Typeface.BOLD);
        tvInst.setPadding(0, ThemeManager.dp(mContext, 4), 0, ThemeManager.dp(mContext, 8));
        body.addView(tvInst);

        List<AlLoaderManager.InstalledScript> installed = AlLoaderManager.listInstalledScripts(mContext);
        if (installed.isEmpty()) {
            TextView tvEmpty = new TextView(mContext);
            tvEmpty.setText("Belum ada skrip Lua terpasang. Pasang skrip rekomendasi di bawah.");
            tvEmpty.setTextColor(Color.parseColor("#94A3B8"));
            tvEmpty.setTextSize(12);
            tvEmpty.setPadding(0, 0, 0, ThemeManager.dp(mContext, 10));
            body.addView(tvEmpty);
        } else {
            for (AlLoaderManager.InstalledScript s : installed) {
                LinearLayout row = new LinearLayout(mContext);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackground(ThemeManager.createCardDrawable(
                        mContext, Color.parseColor("#0B1322"), Color.parseColor("#1E2C44"), 1, 10));
                row.setPadding(ThemeManager.dp(mContext, 12), ThemeManager.dp(mContext, 8),
                        ThemeManager.dp(mContext, 10), ThemeManager.dp(mContext, 8));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = ThemeManager.dp(mContext, 8);
                row.setLayoutParams(lp);

                TextView tv = new TextView(mContext);
                tv.setText(s.getDisplayName() + " (" + (s.enabled ? "Aktif" : "Nonaktif") + ")");
                tv.setTextColor(s.enabled ? Color.parseColor("#F8FAFC") : Color.parseColor("#94A3B8"));
                tv.setTextSize(13);
                tv.setTypeface(null, Typeface.BOLD);
                tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(tv);

                row.addView(secondaryBtn("Edit", R.drawable.ic_romp_edit, v -> CodeEditorDialog.open(mContext, s.file, saved -> render())));
                row.addView(secondaryBtn(s.enabled ? "Pause" : "Aktifkan", 0, v -> {
                    AlLoaderManager.toggleScript(s);
                    render();
                }));
                Button del = dangerBtn("Hapus", v -> {
                    s.file.delete();
                    render();
                });
                LinearLayout.LayoutParams dlp = (LinearLayout.LayoutParams) del.getLayoutParams();
                dlp.rightMargin = 0;
                del.setLayoutParams(dlp);
                row.addView(del);
                body.addView(row);
            }
        }

        TextView tvRec = new TextView(mContext);
        tvRec.setText("SKRIP BAWAAN REKOMENDASI");
        tvRec.setTextColor(Color.parseColor("#22C55E"));
        tvRec.setTextSize(12);
        tvRec.setTypeface(null, Typeface.BOLD);
        tvRec.setPadding(0, ThemeManager.dp(mContext, 12), 0, ThemeManager.dp(mContext, 8));
        body.addView(tvRec);

        for (AlLoaderManager.RecommendedScript rec : AlLoaderManager.getRecommendedScripts(mContext)) {
            boolean already = AlLoaderManager.isRecommendedInstalled(mContext, rec.file);
            LinearLayout card = new LinearLayout(mContext);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(ThemeManager.createCardDrawable(
                    mContext, Color.parseColor("#0B1322"), Color.parseColor("#1E2C44"), 1, 10));
            card.setPadding(ThemeManager.dp(mContext, 14), ThemeManager.dp(mContext, 12),
                    ThemeManager.dp(mContext, 14), ThemeManager.dp(mContext, 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = ThemeManager.dp(mContext, 8);
            card.setLayoutParams(lp);

            TextView tTitle = new TextView(mContext);
            tTitle.setText(rec.name + " v" + rec.version + " · " + rec.commands);
            tTitle.setTextColor(Color.parseColor("#F8FAFC"));
            tTitle.setTextSize(13);
            tTitle.setTypeface(null, Typeface.BOLD);
            card.addView(tTitle);

            TextView tSum = new TextView(mContext);
            tSum.setText(rec.summary);
            tSum.setTextColor(Color.parseColor("#94A3B8"));
            tSum.setTextSize(12);
            tSum.setPadding(0, ThemeManager.dp(mContext, 4), 0, ThemeManager.dp(mContext, 8));
            card.addView(tSum);

            Button b = already
                    ? dangerBtn("Hapus Script", v -> { AlLoaderManager.uninstallScript(mContext, rec.file); render(); })
                    : pillBtn("Pasang Script", Color.parseColor("#19382E"), Color.parseColor("#22C55E"),
                            Color.parseColor("#34D399"), R.drawable.ic_romp_plus,
                            v -> { AlLoaderManager.installRecommendedScript(mContext, rec.file); render(); });
            card.addView(b);
            body.addView(card);
        }
    }

    // =========================================================================
    // Sub-tab 3: Game Textures (texdb) & Utilities (Screenshots / Logs)
    // =========================================================================
    private void renderTexturesAndTools(LinearLayout body) {
        int accent = mTheme.getAccentColor();

        TextView tvTex = new TextView(mContext);
        tvTex.setText("TEKSTUR GAME (UPLOAD TEXDB .ZIP)");
        tvTex.setTextColor(accent);
        tvTex.setTextSize(13);
        tvTex.setTypeface(null, Typeface.BOLD);
        body.addView(tvTex);

        TextView tvDesc = new TextView(mContext);
        tvDesc.setText("Pasang modpack tekstur seluler / texdb .zip langsung ke folder texdb/custom/ atau bersihkan kapan saja.");
        tvDesc.setTextColor(Color.parseColor("#94A3B8"));
        tvDesc.setTextSize(12);
        tvDesc.setPadding(0, ThemeManager.dp(mContext, 6), 0, ThemeManager.dp(mContext, 10));
        body.addView(tvDesc);

        HorizontalScrollView scroll1 = new HorizontalScrollView(mContext);
        scroll1.setHorizontalScrollBarEnabled(false);
        LinearLayout texRow = new LinearLayout(mContext);
        texRow.setOrientation(LinearLayout.HORIZONTAL);
        texRow.addView(accentBtn("Upload texdb ZIP", R.drawable.ic_romp_image, accent, v -> {
            if (mPickers != null) mPickers.onRequestImportTexdbZip();
        }));
        texRow.addView(dangerBtn("Hapus Tekstur Kustom", v -> {
            int deleted = ModLoaderManager.clearCustomTexdb(mContext);
            Toast.makeText(mContext, deleted + " berkas tekstur kustom dihapus.", Toast.LENGTH_SHORT).show();
        }));
        scroll1.addView(texRow);
        body.addView(scroll1);

        TextView tvTools = new TextView(mContext);
        tvTools.setText("GALERI SCREENSHOT & LOG DIAGNOSTIK");
        tvTools.setTextColor(accent);
        tvTools.setTextSize(13);
        tvTools.setTypeface(null, Typeface.BOLD);
        tvTools.setPadding(0, ThemeManager.dp(mContext, 16), 0, ThemeManager.dp(mContext, 8));
        body.addView(tvTools);

        HorizontalScrollView scroll2 = new HorizontalScrollView(mContext);
        scroll2.setHorizontalScrollBarEnabled(false);
        LinearLayout toolsRow = new LinearLayout(mContext);
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        toolsRow.addView(secondaryBtn("Galeri Screenshot", R.drawable.ic_romp_image, v -> ScreenshotGalleryDialog.show(mContext)));
        toolsRow.addView(secondaryBtn("Log & Crash Viewer", R.drawable.ic_romp_terminal, v -> LogsViewerDialog.show(mContext)));
        scroll2.addView(toolsRow);
        body.addView(scroll2);
    }

    private void showAlLoaderLog() {
        File log = AlLoaderManager.getLogFile(mContext);
        if (!log.exists()) {
            Toast.makeText(mContext, "Belum ada alloader.log (jalankan gim sekali dulu).", Toast.LENGTH_SHORT).show();
            return;
        }
        CodeEditorDialog.open(mContext, log, null);
    }

    private void showApiDocsDialog() {
        LinearLayout root = new LinearLayout(mContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F172A"));
        root.setPadding(ThemeManager.dp(mContext, 16), ThemeManager.dp(mContext, 12),
                ThemeManager.dp(mContext, 16), ThemeManager.dp(mContext, 12));

        EditText et = new EditText(mContext);
        et.setHint("Filter fungsi / event AL Loader...");
        et.setTextColor(Color.parseColor("#F8FAFC"));
        et.setHintTextColor(Color.parseColor("#94A3B8"));
        et.setBackgroundResource(R.drawable.bg_search_bar);
        et.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_romp_search, 0, 0, 0);
        et.setCompoundDrawablePadding(ThemeManager.dp(mContext, 8));
        et.setPadding(ThemeManager.dp(mContext, 12), ThemeManager.dp(mContext, 10),
                ThemeManager.dp(mContext, 12), ThemeManager.dp(mContext, 10));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ThemeManager.dp(mContext, 48));
        elp.bottomMargin = ThemeManager.dp(mContext, 10);
        et.setLayoutParams(elp);
        root.addView(et);

        TextView tv = new TextView(mContext);
        tv.setTextColor(Color.parseColor("#CBD5E1"));
        tv.setTextSize(11);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setText(AlLoaderManager.formatApiReference(mContext, ""));

        ScrollView scroll = new ScrollView(mContext);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ThemeManager.dp(mContext, 280)));
        scroll.addView(tv);
        root.addView(scroll);

        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                tv.setText(AlLoaderManager.formatApiReference(mContext, s != null ? s.toString() : ""));
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        new AlertDialog.Builder(mContext)
                .setTitle("AL Loader Lua API Reference")
                .setView(root)
                .setPositiveButton("Tutup", null)
                .show();
    }

    private Button accentBtn(String text, int iconRes, int accent, View.OnClickListener l) {
        Button b = new Button(mContext);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTypeface(null, Typeface.BOLD);
        b.setPadding(ThemeManager.dp(mContext, 14), 0, ThemeManager.dp(mContext, 14), 0);
        if (iconRes != 0) {
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
            b.setCompoundDrawablePadding(ThemeManager.dp(mContext, 6));
        }
        ThemeManager.stylePrimaryCta(mContext, b, accent);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ThemeManager.dp(mContext, 44));
        lp.setMargins(0, 0, ThemeManager.dp(mContext, 8), 0);
        b.setLayoutParams(lp);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private Button secondaryBtn(String text, int iconRes, View.OnClickListener l) {
        return pillBtn(text, Color.parseColor("#192438"), Color.parseColor("#3B4F73"),
                Color.parseColor("#F8FAFC"), iconRes, l);
    }

    private Button dangerBtn(String text, View.OnClickListener l) {
        return pillBtn(text, Color.parseColor("#3B141A"), Color.parseColor("#7F1D1D"),
                Color.parseColor("#F87171"), R.drawable.ic_romp_trash, l);
    }

    private Button pillBtn(String text, int fillColor, int borderColor, int fgColor, int iconRes, View.OnClickListener l) {
        Button b = new Button(mContext);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextColor(fgColor);
        b.setTextSize(12);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackground(ThemeManager.createInteractivePill(
                mContext, fillColor, borderColor, ThemeManager.withAlpha(fgColor, 48), 10));
        if (iconRes != 0) {
            b.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
            b.setCompoundDrawablePadding(ThemeManager.dp(mContext, 6));
            ThemeManager.tintCompoundDrawables(b, fgColor);
        }
        b.setPadding(ThemeManager.dp(mContext, 14), 0, ThemeManager.dp(mContext, 14), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, ThemeManager.dp(mContext, 44));
        lp.setMargins(0, 0, ThemeManager.dp(mContext, 8), 0);
        b.setLayoutParams(lp);
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private static boolean isEditableText(String name) {
        String l = name.toLowerCase(Locale.US);
        return l.endsWith(".ini") || l.endsWith(".cfg") || l.endsWith(".lua")
                || l.endsWith(".json") || l.endsWith(".txt") || l.endsWith(".log")
                || l.endsWith(".disabled");
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }
}
