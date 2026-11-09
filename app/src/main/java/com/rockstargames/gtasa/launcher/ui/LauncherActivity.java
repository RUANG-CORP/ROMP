package com.rockstargames.gtasa.launcher.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;

import com.rockstargames.gtasa.GTASA;
import com.rockstargames.gtasa.R;
import com.rockstargames.gtasa.launcher.data.CacheDownloader;
import com.rockstargames.gtasa.launcher.data.CacheVerifier;
import com.rockstargames.gtasa.launcher.data.ServerItem;
import com.rockstargames.gtasa.launcher.data.ServerRepository;
import com.rockstargames.gtasa.launcher.data.SettingsManager;

import java.util.List;

/**
 * Universal Ad-Free SA-MP / open.mp Mobile Launcher.
 * Features:
 * - Multi-server directory (Favorites & Internet list)
 * - Live UDP SA-MP query status (Ping, Players, Hostname)
 * - Game cache integrity verification
 * - Dynamic settings synchronization to native settings.ini
 * - 100% Ad-Free experience
 */
public class LauncherActivity extends Activity {

    private ServerRepository mRepo;
    private SettingsManager mSettings;

    private ListView mListViewServers;
    private ScrollView mLayoutSettings;

    private Button mTabFavorites;
    private Button mTabInternet;
    private Button mTabSettings;
    private Button mBtnAddServer;

    // Settings UI elements
    private EditText mEtNickname;
    private Spinner mSpinnerFps;
    private EditText mEtCacheUrl;
    private TextView mTvCacheStatus;
    private Button mBtnDownloadCache;
    private Button mBtnInstallLocalZip;
    private ProgressBar mPbCacheDownload;
    private Button mBtnSaveSettings;

    private EditText mEtSearch;
    private ServerAdapter mCurrentAdapter;

    private boolean mCurrentIsFavorites = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        mRepo = new ServerRepository(this);
        mSettings = new SettingsManager(this);

        initViews();
        setupTabs();
        setupSettingsView();
        showFavoritesTab();
    }

    private void initViews() {
        mListViewServers = findViewById(R.id.lv_servers);
        mLayoutSettings = findViewById(R.id.layout_settings_view);

        mTabFavorites = findViewById(R.id.tab_favorites);
        mTabInternet = findViewById(R.id.tab_internet);
        mTabSettings = findViewById(R.id.tab_settings);
        mBtnAddServer = findViewById(R.id.btn_add_server);

        mEtNickname = findViewById(R.id.et_nickname);
        mSpinnerFps = findViewById(R.id.spinner_fps);
        mEtCacheUrl = findViewById(R.id.et_cache_url);
        mTvCacheStatus = findViewById(R.id.tv_cache_status);
        mBtnDownloadCache = findViewById(R.id.btn_download_cache);
        mBtnInstallLocalZip = findViewById(R.id.btn_install_local_zip);
        mPbCacheDownload = findViewById(R.id.pb_cache_download);
        mBtnSaveSettings = findViewById(R.id.btn_save_settings);
        mEtSearch = findViewById(R.id.et_search_servers);

        mBtnDownloadCache.setOnClickListener(v -> startCacheDownload());
        mBtnInstallLocalZip.setOnClickListener(v -> promptInstallLocalZip());
        mBtnAddServer.setOnClickListener(v -> showAddServerDialog());

        if (mEtSearch != null) {
            mEtSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (mCurrentAdapter != null) {
                        mCurrentAdapter.filter(s != null ? s.toString() : "");
                    }
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        }
    }

    private void setupTabs() {
        mTabFavorites.setOnClickListener(v -> showFavoritesTab());
        mTabInternet.setOnClickListener(v -> showInternetTab());
        mTabSettings.setOnClickListener(v -> showSettingsTab());
    }

    private void showFavoritesTab() {
        mCurrentIsFavorites = true;
        mListViewServers.setVisibility(View.VISIBLE);
        mLayoutSettings.setVisibility(View.GONE);
        mBtnAddServer.setVisibility(View.VISIBLE);

        highlightTab(mTabFavorites, mTabInternet, mTabSettings);
        loadFavoritesList();
    }

    private void showInternetTab() {
        mCurrentIsFavorites = false;
        mListViewServers.setVisibility(View.VISIBLE);
        mLayoutSettings.setVisibility(View.GONE);
        mBtnAddServer.setVisibility(View.GONE);

        highlightTab(mTabInternet, mTabFavorites, mTabSettings);
        loadInternetList();
    }

    private void showSettingsTab() {
        mListViewServers.setVisibility(View.GONE);
        mLayoutSettings.setVisibility(View.VISIBLE);
        mBtnAddServer.setVisibility(View.GONE);

        highlightTab(mTabSettings, mTabFavorites, mTabInternet);
        refreshSettingsValues();
    }

    private void highlightTab(Button active, Button inactive1, Button inactive2) {
        active.setTextColor(Color.parseColor("#38BDF8"));
        inactive1.setTextColor(Color.parseColor("#94A3B8"));
        inactive2.setTextColor(Color.parseColor("#94A3B8"));
    }

    private void loadFavoritesList() {
        List<ServerItem> favorites = mRepo.getFavorites();
        ServerAdapter adapter = new ServerAdapter(this, favorites, true, new ServerAdapter.OnServerActionListener() {
            @Override
            public void onConnect(ServerItem server) {
                launchGameWithServer(server);
            }

            @Override
            public void onDelete(ServerItem server) {
                mRepo.removeFavorite(server.getId());
                loadFavoritesList();
                Toast.makeText(LauncherActivity.this, "Server dihapus dari Favorit", Toast.LENGTH_SHORT).show();
            }
        });
        mCurrentAdapter = adapter;
        mListViewServers.setAdapter(adapter);
        if (mEtSearch != null && mEtSearch.getText().length() > 0) {
            mCurrentAdapter.filter(mEtSearch.getText().toString());
        }
    }

    private void loadInternetList() {
        List<ServerItem> publicServers = mRepo.getPublicServers();
        ServerAdapter adapter = new ServerAdapter(this, publicServers, false, new ServerAdapter.OnServerActionListener() {
            @Override
            public void onConnect(ServerItem server) {
                launchGameWithServer(server);
            }

            @Override
            public void onDelete(ServerItem server) {
                // Not applicable for public tab
            }
        });
        mCurrentAdapter = adapter;
        mListViewServers.setAdapter(adapter);
        if (mEtSearch != null && mEtSearch.getText().length() > 0) {
            mCurrentAdapter.filter(mEtSearch.getText().toString());
        }
    }

    private void showAddServerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_server, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        EditText etName = dialogView.findViewById(R.id.et_server_name);
        EditText etIp = dialogView.findViewById(R.id.et_server_ip);
        EditText etPort = dialogView.findViewById(R.id.et_server_port);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel_add);
        Button btnConfirm = dialogView.findViewById(R.id.btn_confirm_add);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            String ip = etIp.getText().toString().trim();
            String portStr = etPort.getText().toString().trim();
            String name = etName.getText().toString().trim();

            if (ip.isEmpty()) {
                etIp.setError("IP Server tidak boleh kosong!");
                return;
            }

            int port = 7777;
            if (!portStr.isEmpty()) {
                try {
                    port = Integer.parseInt(portStr);
                    if (port <= 0 || port > 65535) {
                        etPort.setError("Port tidak valid (1-65535)");
                        return;
                    }
                } catch (NumberFormatException e) {
                    etPort.setError("Port harus berupa angka");
                    return;
                }
            }

            mRepo.addFavorite(new ServerItem(ip, port, name.isEmpty() ? null : name));
            dialog.dismiss();
            if (mCurrentIsFavorites) {
                loadFavoritesList();
            }
            Toast.makeText(this, "Server berhasil ditambahkan!", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

    private void setupSettingsView() {
        // Setup FPS Limit spinner
        String[] fpsOptions = {"30 FPS", "60 FPS", "90 FPS", "120 FPS"};
        ArrayAdapter<String> fpsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, fpsOptions);
        mSpinnerFps.setAdapter(fpsAdapter);

        mBtnSaveSettings.setOnClickListener(v -> saveSettingsValues());
    }

    private void refreshSettingsValues() {
        mEtNickname.setText(mSettings.getNickname());
        if (mEtCacheUrl != null) {
            mEtCacheUrl.setText(mSettings.getCacheUrl());
        }

        int currentFps = mSettings.getFpsLimit();
        if (currentFps == 30) mSpinnerFps.setSelection(0);
        else if (currentFps == 60) mSpinnerFps.setSelection(1);
        else if (currentFps == 90) mSpinnerFps.setSelection(2);
        else if (currentFps == 120) mSpinnerFps.setSelection(3);
        else mSpinnerFps.setSelection(2); // default 90

        // Check Cache
        CacheVerifier.CacheStatus status = CacheVerifier.checkGameData(this);
        if (status.isComplete()) {
            mTvCacheStatus.setText("Status: Lengkap (Game siap dimainkan)\nLokasi: " + status.getBaseDir().getAbsolutePath());
            mTvCacheStatus.setTextColor(Color.parseColor("#10B981"));
            mBtnDownloadCache.setText("GAME DATA LENGKAP (VERIFIKASI ULANG)");
            mBtnDownloadCache.setBackgroundColor(Color.parseColor("#334155"));
        } else {
            mTvCacheStatus.setText("Status: Cache belum lengkap\n" + status.getMissingSummary() + "\nLokasi target: " + status.getBaseDir().getAbsolutePath());
            mTvCacheStatus.setTextColor(Color.parseColor("#F59E0B"));
            mBtnDownloadCache.setText("DOWNLOAD DARI CDN");
            mBtnDownloadCache.setBackgroundColor(Color.parseColor("#10B981"));
        }
    }

    private void startCacheDownload() {
        if (CacheDownloader.isDownloading()) {
            Toast.makeText(this, "Pengunduhan sedang berjalan...", Toast.LENGTH_SHORT).show();
            return;
        }

        mPbCacheDownload.setVisibility(View.VISIBLE);
        mPbCacheDownload.setProgress(0);
        mBtnDownloadCache.setEnabled(false);
        mBtnDownloadCache.setText("Mengunduh...");

        // CDN endpoint from saved settings
        String cacheUrl = mSettings.getCacheUrl();
        if (cacheUrl == null || cacheUrl.trim().isEmpty()) {
            cacheUrl = "https://archive.org/download/gtasa-android-data/gtasa_cache.zip";
        }

        CacheDownloader.downloadAndExtract(cacheUrl, getExternalFilesDir(null), new CacheDownloader.DownloadCallback() {
            @Override
            public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                mPbCacheDownload.setProgress(percent);
                long mbDownloaded = downloadedBytes / (1024 * 1024);
                long mbTotal = totalBytes / (1024 * 1024);
                mTvCacheStatus.setText("Mengunduh Cache: " + percent + "% (" + mbDownloaded + " MB / " + mbTotal + " MB)");
            }

            @Override
            public void onExtracting(String entryName) {
                mTvCacheStatus.setText("Mengekstrak: " + entryName);
            }

            @Override
            public void onComplete(java.io.File destinationDir) {
                mPbCacheDownload.setVisibility(View.GONE);
                mBtnDownloadCache.setEnabled(true);
                Toast.makeText(LauncherActivity.this, "Game data berhasil diunduh dan dipasang!", Toast.LENGTH_LONG).show();
                refreshSettingsValues();
            }

            @Override
            public void onError(String errorMessage) {
                mPbCacheDownload.setVisibility(View.GONE);
                mBtnDownloadCache.setEnabled(true);
                Toast.makeText(LauncherActivity.this, "Gagal mengunduh: " + errorMessage, Toast.LENGTH_LONG).show();
                refreshSettingsValues();
            }
        });
    }

    private void promptInstallLocalZip() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pasang dari File ZIP Lokal");
        builder.setMessage("Masukkan lokasi path file .zip game data GTA SA di penyimpanan perangkat:");

        final EditText etPath = new EditText(this);
        etPath.setHint("/sdcard/Download/gtasa_cache.zip");
        etPath.setText(android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Download/gtasa_cache.zip");
        etPath.setTextColor(Color.WHITE);
        builder.setView(etPath);

        builder.setPositiveButton("Ekstrak", (dialog, which) -> {
            String path = etPath.getText().toString().trim();
            java.io.File file = new java.io.File(path);
            if (!file.exists()) {
                Toast.makeText(this, "File tidak ditemukan: " + path, Toast.LENGTH_LONG).show();
                return;
            }

            mPbCacheDownload.setVisibility(View.VISIBLE);
            mTvCacheStatus.setText("Mengekstrak file ZIP lokal...");
            mBtnInstallLocalZip.setEnabled(false);

            CacheDownloader.extractLocalZip(file, getExternalFilesDir(null), new CacheDownloader.DownloadCallback() {
                @Override public void onProgress(int percent, long current, long total) {}
                @Override public void onExtracting(String entryName) {
                    mTvCacheStatus.setText("Mengekstrak: " + entryName);
                }
                @Override public void onComplete(java.io.File destinationDir) {
                    mPbCacheDownload.setVisibility(View.GONE);
                    mBtnInstallLocalZip.setEnabled(true);
                    Toast.makeText(LauncherActivity.this, "Ekstraksi game data selesai!", Toast.LENGTH_LONG).show();
                    refreshSettingsValues();
                }
                @Override public void onError(String errorMessage) {
                    mPbCacheDownload.setVisibility(View.GONE);
                    mBtnInstallLocalZip.setEnabled(true);
                    Toast.makeText(LauncherActivity.this, "Gagal: " + errorMessage, Toast.LENGTH_LONG).show();
                    refreshSettingsValues();
                }
            });
        });

        builder.setNegativeButton("Batal", null);
        builder.show();
    }

    private void saveSettingsValues() {
        String nickname = mEtNickname.getText().toString().trim();
        if (!SettingsManager.isValidNickname(nickname)) {
            mEtNickname.setError("Format Nickname tidak valid! (3-20 karakter: huruf, angka, _, [], $, @)");
            return;
        }

        mSettings.setNickname(nickname);

        int selectedFpsIndex = mSpinnerFps.getSelectedItemPosition();
        int fps = 90;
        if (selectedFpsIndex == 0) fps = 30;
        else if (selectedFpsIndex == 1) fps = 60;
        else if (selectedFpsIndex == 2) fps = 90;
        else if (selectedFpsIndex == 3) fps = 120;
        mSettings.setFpsLimit(fps);

        if (mEtCacheUrl != null) {
            String url = mEtCacheUrl.getText().toString().trim();
            if (!url.isEmpty()) {
                mSettings.setCacheUrl(url);
            }
        }

        Toast.makeText(this, "Pengaturan berhasil disimpan!", Toast.LENGTH_SHORT).show();
    }

    private void launchGameWithServer(ServerItem server) {
        String nickname = mSettings.getNickname();
        if (!SettingsManager.isValidNickname(nickname)) {
            Toast.makeText(this, "Silakan atur Nickname yang valid di tab Pengaturan terlebih dahulu!", Toast.LENGTH_LONG).show();
            showSettingsTab();
            return;
        }

        // Sync settings to native GTA SA directory
        boolean synced = mSettings.syncToNativeSettings(server, getExternalFilesDir(null));
        if (!synced) {
            Toast.makeText(this, "Peringatan: Gagal menulis settings.ini, melanjutkan...", Toast.LENGTH_SHORT).show();
        }

        // Check cache status
        CacheVerifier.CacheStatus cacheStatus = CacheVerifier.checkGameData(this);
        if (!cacheStatus.isComplete()) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle("Game Data Belum Lengkap");
            alert.setMessage("File data GTA SA belum lengkap di penyimpanan.\n\nUnduh file data secara otomatis sekarang?");
            alert.setPositiveButton("Download Sekarang", (dialog, which) -> {
                showSettingsTab();
                startCacheDownload();
            });
            alert.setNegativeButton("Lanjut Main", (dialog, which) -> {
                startGameActivity(server, nickname);
            });
            alert.show();
            return;
        }

        startGameActivity(server, nickname);
    }

    private void startGameActivity(ServerItem server, String nickname) {
        Intent intent = new Intent(this, GTASA.class);
        intent.putExtra("server_ip", server.getIp());
        intent.putExtra("server_port", server.getPort());
        intent.putExtra("player_name", nickname);
        startActivity(intent);
    }
}
