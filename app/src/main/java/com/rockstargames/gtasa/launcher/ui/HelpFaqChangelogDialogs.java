package com.rockstargames.gtasa.launcher.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.rockstargames.gtasa.R;
import com.rockstargames.gtasa.launcher.data.ThemeManager;

/**
 * Provides searchable Help / FAQ, Changelog, and About dialogs for ROMP by HARFA.
 */
public class HelpFaqChangelogDialogs {

    private static final String[][] FAQ_ITEMS = {
            {"Bagaimana cara bergabung ke server?",
             "1. Pilih tab SERVER -> jelajahi tab Hosted (live masterlist), Resmi (RUANG ROLEPLAY), atau Favorit.\n"
             + "2. Gunakan '+ TAMBAH SERVER' untuk memasukkan IP & Port manual.\n"
             + "3. Isi Nickname valid (3-20 karakter), lalu tekan MAIN ONLINE."},
            {"Apa itu Join Link (romp:// & resa://)?",
             "Tautan seperti romp://connect?host=18.142.244.146&port=7777 atau resa://connect?... akan otomatis membuka ROMP by HARFA dan menanyakan apakah kamu ingin menyimpan atau langsung bermain di server tersebut."},
            {"Bagaimana cara memakai Mod Loader (DFF/TXD/Sound/Modpack)?",
             "Buka tab BERKAS -> sub-tab Mod Loader.\n"
             + "• Pastikan sakelar 'Gunakan Mod' aktif.\n"
             + "• Impor file tunggal (.dff, .txd, .png, .wav, .mp3) atau arsip modpack .zip.\n"
             + "• Skrip PC (.cs, .asi, CLEO) tidak didukung di engine native Android dan akan dilaporkan di bagian Tidak Diterapkan."},
            {"Bagaimana cara memasang Lua Script (AL Loader)?",
             "Buka tab BERKAS -> sub-tab AL Loader (Lua).\n"
             + "Aktifkan AL Loader, lalu pasang skrip bawaan seperti Chat Log (/chatlog), Script Manager (/scripts), Command Buttons (/cmdbtn), Quick Commands (/qc), Info HUD (/infohud), InfoWindow (/adjinfow), dan FreeCam (/fcam), atau impor file .lua kamu sendiri ke mods/AL Loader/scripts/."},
            {"Bagaimana cara memakai Voice Chat?",
             "Masuk ke tab SETELAN -> Obrolan Suara, aktifkan 'Voice chat' dan berikan izin Mikrofon (RECORD_AUDIO). Kamu juga bisa memilih mode tahan-untuk-bicara (hold-to-talk) atau sakelar (toggle)."},
            {"Bagaimana cara mengubah Crosshair, Font Kustom, dan Tata Letak Kontrol?",
             "Di tab SETELAN:\n"
             + "• Editor Crosshair: atur gaya, ukuran, ketebalan, warna, atau unggah gambar PNG.\n"
             + "• Font Kustom: pilih salah satu dari 18+ font bawaan atau impor .ttf/.otf.\n"
             + "• Atur Kontrol & HUD: luncurkan mode editor tata letak langsung di layar permainan."},
            {"Bagaimana cara mengambil & melihat Screenshot?",
             "Saat bermain, gunakan tombol kamera, ketik /ss, atau tekan F8. Semua foto tersimpan di SAMP/screenshots/ dan bisa dilihat, disimpan ke Galeri, atau dibagikan lewat SETELAN -> Galeri Screenshot."}
    };

    public static void showHelpFaq(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0F172A"));
        root.setPadding(20, 16, 20, 16);

        EditText etSearch = new EditText(context);
        etSearch.setHint("Cari topik bantuan (contoh: modpack, lua, voice, link)...");
        etSearch.setTextColor(Color.parseColor("#F8FAFC"));
        etSearch.setHintTextColor(Color.parseColor("#94A3B8"));
        etSearch.setBackgroundResource(R.drawable.bg_search_bar);
        etSearch.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_romp_search, 0, 0, 0);
        etSearch.setCompoundDrawablePadding(ThemeManager.dp(context, 10));
        etSearch.setPadding(ThemeManager.dp(context, 14), 0, ThemeManager.dp(context, 14), 0);
        etSearch.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, ThemeManager.dp(context, 48)));
        root.addView(etSearch);

        LinearLayout listHolder = new LinearLayout(context);
        listHolder.setOrientation(LinearLayout.VERTICAL);
        listHolder.setPadding(0, ThemeManager.dp(context, 12), 0, ThemeManager.dp(context, 8));

        ScrollView scroll = new ScrollView(context);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ThemeManager.dp(context, 280)));
        scroll.addView(listHolder);
        root.addView(scroll);

        int accent = new ThemeManager(context).getAccentColor();
        Runnable render = () -> {
            String q = etSearch.getText().toString().trim().toLowerCase();
            listHolder.removeAllViews();
            for (String[] item : FAQ_ITEMS) {
                if (!q.isEmpty() && !item[0].toLowerCase().contains(q) && !item[1].toLowerCase().contains(q)) continue;
                TextView tq = new TextView(context);
                tq.setText(item[0]);
                tq.setTextColor(accent);
                tq.setTextSize(13);
                tq.setTypeface(null, android.graphics.Typeface.BOLD);
                tq.setPadding(4, ThemeManager.dp(context, 10), 4, ThemeManager.dp(context, 4));
                listHolder.addView(tq);

                TextView ta = new TextView(context);
                ta.setText(item[1]);
                ta.setTextColor(Color.parseColor("#CBD5E1"));
                ta.setTextSize(12);
                ta.setPadding(8, 0, 8, ThemeManager.dp(context, 10));
                listHolder.addView(ta);
            }
        };
        render.run();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { render.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        new AlertDialog.Builder(context)
                .setTitle("Bantuan & FAQ ROMP by HARFA")
                .setView(root)
                .setPositiveButton("Tutup", null)
                .show();
    }

    public static void showChangelog(Context context) {
        String msg = "ROMP by HARFA · v2.0 Full Parity Edition\n\n"
                + "• Dukungan Engine 3D Penuh (arm64-v8a, armeabi-v7a, x86_64) dengan koneksi online langsung\n"
                + "• Tab Server Lengkap: Hosted (Masterlist Live), Resmi, & Favorit + Filter & Password Prompt\n"
                + "• Dukungan Penuh AL Loader (Lua) + InputSystem Soft Keyboard + 7 Script Bawaan & API Reference\n"
                + "• Mod Loader (DFF/TXD/PNG/Sound/Modpack ZIP) + Tekstur Game (texdb ZIP)\n"
                + "• Manajer Berkas In-App + Editor Kode/INI + Akses Android Files (SAF DocumentsProvider)\n"
                + "• 142 Pengaturan Lengkap (Grafis, Kontrol Touch & Gyroscope, 47 Keybind PC, Voice Chat, SA-MP 0.3.7/R5/DL)\n"
                + "• Editor Crosshair Visual, Font Kustom (18+ Bundled + TTF/OTF), Kustomisasi Tema & Galeri Screenshot\n"
                + "• Deep Link (romp://, resa://), Foreground Download Notification & Discord Rich Presence\n"
                + "• 100% Bebas Iklan & Bebas Tracker";

        new AlertDialog.Builder(context)
                .setTitle("Catatan Pembaruan (Changelog)")
                .setMessage(msg)
                .setPositiveButton("Mengerti", null)
                .show();
    }

    public static void showAbout(Context context) {
        String msg = "ROMP by HARFA\n"
                + "SA-MP & open.mp Mobile Client · 100% Ad-Free Edition\n\n"
                + "Fitur inti:\n"
                + "• Multi-Architecture 3D RenderWare & SA-MP Engine\n"
                + "• Hosted / Official / Favorites Server Directory\n"
                + "• AL Loader (Lua) & Modpack Workspace\n"
                + "• Voice Chat, CEF Overlay, Discord RPC & Custom HUD Tools\n\n"
                + "Dibuat & disesuaikan untuk pengalaman bermain GTA SA-MP Mobile terbaik oleh HARFA.";

        new AlertDialog.Builder(context)
                .setTitle("Tentang ROMP by HARFA")
                .setMessage(msg)
                .setPositiveButton("Tutup", null)
                .show();
    }
}

