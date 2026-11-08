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
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;

import com.rockstargames.gtasa.GTASA;
import com.rockstargames.gtasa.R;
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
    private TextView mTvCacheStatus;
    private Button mBtnSaveSettings;

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
        mTvCacheStatus = findViewById(R.id.tv_cache_status);
        mBtnSaveSettings = findViewById(R.id.btn_save_settings);

        mBtnAddServer.setOnClickListener(v -> showAddServerDialog());
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
        mListViewServers.setAdapter(adapter);
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
        mListViewServers.setAdapter(adapter);
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
        } else {
            mTvCacheStatus.setText("Status: Cache belum lengkap\n" + status.getMissingSummary() + "\nLokasi target: " + status.getBaseDir().getAbsolutePath());
            mTvCacheStatus.setTextColor(Color.parseColor("#F59E0B"));
        }
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
            Toast.makeText(this, "Peringatan: Data game GTA SA belum lengkap di Android/data", Toast.LENGTH_SHORT).show();
        }

        // Launch Game Activity
        Intent intent = new Intent(this, GTASA.class);
        intent.putExtra("server_ip", server.getIp());
        intent.putExtra("server_port", server.getPort());
        intent.putExtra("player_name", nickname);
        startActivity(intent);
    }
}
