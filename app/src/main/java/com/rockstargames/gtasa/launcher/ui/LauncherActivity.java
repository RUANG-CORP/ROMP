package com.rockstargames.gtasa.launcher.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
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
import com.rockstargames.gtasa.launcher.net.SampQueryClient;
import com.rockstargames.gtasa.launcher.net.ServerInfo;

import java.util.List;
import java.util.Map;

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

    // Navigation Rail
    private Button mBtnNavHome;
    private Button mBtnNavServers;
    private Button mBtnNavFiles;
    private Button mBtnNavSettings;

    // View Containers
    private View mLayoutHomeView;
    private View mLayoutServerListContainer;
    private ScrollView mLayoutFilesView;
    private ScrollView mLayoutSettings;

    // Home View Elements
    private TextView mTvHomeSelectedServerName;
    private TextView mTvHomeSelectedServerAddress;
    private TextView mTvHomeSelectedServerStats;
    private Button mBtnMainOnline;
    private Button mBtnMainOffline;
    private ListView mLvPopularServers;

    // Servers Master-Detail Pane Elements
    private ListView mListViewServers;
    private TextView mTvPaneServerName;
    private TextView mTvPaneServerAddress;
    private TextView mTvPaneServerPing;
    private TextView mTvPaneServerPlayers;
    private TextView mTvPaneServerGamemode;
    private TextView mTvPaneServerRules;
    private TextView mTvPaneServerPlayersList;
    private Button mBtnPaneConnect;

    private Button mTabFavorites;
    private Button mTabInternet;
    private Button mBtnAddServer;

    // Settings UI elements
    private EditText mEtNickname;
    private Spinner mSpinnerFps;
    private CheckBox mCbFastChat;
    private CheckBox mCbVoiceChat;
    private EditText mEtCacheUrl;
    private TextView mTvCacheStatus;
    private Button mBtnSyncResa;
    private Button mBtnDownloadCache;
    private Button mBtnInstallLocalZip;
    private ProgressBar mPbCacheDownload;
    private Button mBtnSaveSettings;

    private EditText mEtSearch;
    private ServerAdapter mCurrentAdapter;

    private boolean mCurrentIsFavorites = true;
    private ServerItem mSelectedServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        mRepo = new ServerRepository(this);
        mSettings = new SettingsManager(this);

        initViews();
        setupNavigationRail();
        setupSubTabs();
        setupSettingsView();
        setupDefaultServer();
        showHomeTab();
    }

    private void initViews() {
        // Navigation Rail
        mBtnNavHome = findViewById(R.id.btn_nav_home);
        mBtnNavServers = findViewById(R.id.btn_nav_servers);
        mBtnNavFiles = findViewById(R.id.btn_nav_files);
        mBtnNavSettings = findViewById(R.id.btn_nav_settings);

        // View Containers
        mLayoutHomeView = findViewById(R.id.layout_home_view);
        mLayoutServerListContainer = findViewById(R.id.layout_server_list_container);
        mLayoutFilesView = findViewById(R.id.layout_files_view);
        mLayoutSettings = findViewById(R.id.layout_settings_view);

        // Home View elements
        mTvHomeSelectedServerName = findViewById(R.id.tv_home_selected_server_name);
        mTvHomeSelectedServerAddress = findViewById(R.id.tv_home_selected_server_address);
        mTvHomeSelectedServerStats = findViewById(R.id.tv_home_selected_server_stats);
        mBtnMainOnline = findViewById(R.id.btn_main_online);
        mBtnMainOffline = findViewById(R.id.btn_main_offline);
        mLvPopularServers = findViewById(R.id.lv_popular_servers);

        // Servers Master-Detail Pane elements
        mListViewServers = findViewById(R.id.lv_servers);
        mTvPaneServerName = findViewById(R.id.tv_pane_server_name);
        mTvPaneServerAddress = findViewById(R.id.tv_pane_server_address);
        mTvPaneServerPing = findViewById(R.id.tv_pane_server_ping);
        mTvPaneServerPlayers = findViewById(R.id.tv_pane_server_players);
        mTvPaneServerGamemode = findViewById(R.id.tv_pane_server_gamemode);
        mTvPaneServerRules = findViewById(R.id.tv_pane_server_rules);
        mTvPaneServerPlayersList = findViewById(R.id.tv_pane_server_players_list);
        mBtnPaneConnect = findViewById(R.id.btn_pane_connect);

        mTabFavorites = findViewById(R.id.tab_favorites);
        mTabInternet = findViewById(R.id.tab_internet);
        mBtnAddServer = findViewById(R.id.btn_add_server);

        mEtNickname = findViewById(R.id.et_nickname);
        mSpinnerFps = findViewById(R.id.spinner_fps);
        mCbFastChat = findViewById(R.id.cb_fast_chat);
        mCbVoiceChat = findViewById(R.id.cb_voice_chat);
        mEtCacheUrl = findViewById(R.id.et_cache_url);
        mTvCacheStatus = findViewById(R.id.tv_cache_status);
        mBtnSyncResa = findViewById(R.id.btn_sync_resa);
        mBtnDownloadCache = findViewById(R.id.btn_download_cache);
        mBtnInstallLocalZip = findViewById(R.id.btn_install_local_zip);
        mPbCacheDownload = findViewById(R.id.pb_cache_download);
        mBtnSaveSettings = findViewById(R.id.btn_save_settings);
        mEtSearch = findViewById(R.id.et_search_servers);

        if (mBtnSyncResa != null) mBtnSyncResa.setOnClickListener(v -> startResaLocalSync());
        if (mBtnDownloadCache != null) mBtnDownloadCache.setOnClickListener(v -> startCacheDownload());
        if (mBtnInstallLocalZip != null) mBtnInstallLocalZip.setOnClickListener(v -> promptInstallLocalZip());
        if (mBtnAddServer != null) mBtnAddServer.setOnClickListener(v -> showAddServerDialog());

        if (mBtnMainOnline != null) {
            mBtnMainOnline.setOnClickListener(v -> {
                if (mSelectedServer != null) {
                    launchGameWithServer(mSelectedServer);
                } else {
                    Toast.makeText(this, "Pilih server di sebelah kanan untuk bermain!", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (mBtnMainOffline != null) {
            mBtnMainOffline.setOnClickListener(v -> launchOfflineGame());
        }

        // Social links
        View btnDiscord = findViewById(R.id.btn_link_discord);
        if (btnDiscord != null) btnDiscord.setOnClickListener(v -> Toast.makeText(this, "Komunitas Discord ReSA:MP", Toast.LENGTH_SHORT).show());
        View btnTelegram = findViewById(R.id.btn_link_telegram);
        if (btnTelegram != null) btnTelegram.setOnClickListener(v -> Toast.makeText(this, "Saluran Telegram ReSA:MP", Toast.LENGTH_SHORT).show());
        View btnWebsite = findViewById(R.id.btn_link_website);
        if (btnWebsite != null) btnWebsite.setOnClickListener(v -> Toast.makeText(this, "Situs Resmi ReSA Multiplayer: resa-mp.com", Toast.LENGTH_SHORT).show());
        View btnAbout = findViewById(R.id.btn_link_about);
        if (btnAbout != null) btnAbout.setOnClickListener(v -> Toast.makeText(this, "ReSA Multiplayer Mobile Client v1.1.6 (Ad-Free Edition)", Toast.LENGTH_LONG).show());

        Button btnMockCache = findViewById(R.id.btn_mock_cache);
        if (btnMockCache != null) {
            btnMockCache.setOnClickListener(v -> {
                boolean success = CacheDownloader.createMockCache(getExternalFilesDir(null));
                if (success) {
                    Toast.makeText(this, "Data game uji coba (offline) berhasil dibuat!", Toast.LENGTH_SHORT).show();
                    refreshSettingsValues();
                } else {
                    Toast.makeText(this, "Gagal membuat data uji coba.", Toast.LENGTH_SHORT).show();
                }
            });
        }

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

    private void setupNavigationRail() {
        if (mBtnNavHome != null) mBtnNavHome.setOnClickListener(v -> showHomeTab());
        if (mBtnNavServers != null) mBtnNavServers.setOnClickListener(v -> showServersTab());
        if (mBtnNavFiles != null) mBtnNavFiles.setOnClickListener(v -> showFilesTab());
        if (mBtnNavSettings != null) mBtnNavSettings.setOnClickListener(v -> showSettingsTab());
    }

    private void setupSubTabs() {
        if (mTabFavorites != null) mTabFavorites.setOnClickListener(v -> showFavoritesTab());
        if (mTabInternet != null) mTabInternet.setOnClickListener(v -> showInternetTab());
    }

    private void showHomeTab() {
        if (mLayoutHomeView != null) mLayoutHomeView.setVisibility(View.VISIBLE);
        if (mLayoutServerListContainer != null) mLayoutServerListContainer.setVisibility(View.GONE);
        if (mLayoutFilesView != null) mLayoutFilesView.setVisibility(View.GONE);
        if (mLayoutSettings != null) mLayoutSettings.setVisibility(View.GONE);
        highlightNavButton(mBtnNavHome);
        loadPopularServers();
    }

    private void showServersTab() {
        if (mLayoutHomeView != null) mLayoutHomeView.setVisibility(View.GONE);
        if (mLayoutServerListContainer != null) mLayoutServerListContainer.setVisibility(View.VISIBLE);
        if (mLayoutFilesView != null) mLayoutFilesView.setVisibility(View.GONE);
        if (mLayoutSettings != null) mLayoutSettings.setVisibility(View.GONE);
        highlightNavButton(mBtnNavServers);
        if (mCurrentIsFavorites) {
            loadFavoritesList();
        } else {
            loadInternetList();
        }
    }

    private void showFilesTab() {
        if (mLayoutHomeView != null) mLayoutHomeView.setVisibility(View.GONE);
        if (mLayoutServerListContainer != null) mLayoutServerListContainer.setVisibility(View.GONE);
        if (mLayoutFilesView != null) mLayoutFilesView.setVisibility(View.VISIBLE);
        if (mLayoutSettings != null) mLayoutSettings.setVisibility(View.GONE);
        highlightNavButton(mBtnNavFiles);
        refreshSettingsValues();
    }

    private void showSettingsTab() {
        if (mLayoutHomeView != null) mLayoutHomeView.setVisibility(View.GONE);
        if (mLayoutServerListContainer != null) mLayoutServerListContainer.setVisibility(View.GONE);
        if (mLayoutFilesView != null) mLayoutFilesView.setVisibility(View.GONE);
        if (mLayoutSettings != null) mLayoutSettings.setVisibility(View.VISIBLE);
        highlightNavButton(mBtnNavSettings);
        refreshSettingsValues();
    }

    private void highlightNavButton(Button active) {
        Button[] all = {mBtnNavHome, mBtnNavServers, mBtnNavFiles, mBtnNavSettings};
        for (Button b : all) {
            if (b == null) continue;
            if (b == active) {
                b.setBackgroundResource(R.drawable.bg_nav_item_selected);
                b.setTextColor(Color.parseColor("#38BDF8"));
            } else {
                b.setBackgroundColor(Color.TRANSPARENT);
                b.setTextColor(Color.parseColor("#94A3B8"));
            }
        }
    }

    private void showFavoritesTab() {
        mCurrentIsFavorites = true;
        if (mTabFavorites != null && mTabInternet != null) {
            mTabFavorites.setTextColor(Color.parseColor("#38BDF8"));
            mTabInternet.setTextColor(Color.parseColor("#94A3B8"));
        }
        loadFavoritesList();
    }

    private void showInternetTab() {
        mCurrentIsFavorites = false;
        if (mTabFavorites != null && mTabInternet != null) {
            mTabFavorites.setTextColor(Color.parseColor("#94A3B8"));
            mTabInternet.setTextColor(Color.parseColor("#38BDF8"));
        }
        loadInternetList();
    }

    private void setupDefaultServer() {
        List<ServerItem> list = mRepo.getFavorites();
        if (!list.isEmpty()) {
            selectServerForPlay(list.get(0));
            updateServerPane(list.get(0));
        } else {
            List<ServerItem> pub = mRepo.getPublicServers();
            if (!pub.isEmpty()) {
                selectServerForPlay(pub.get(0));
                updateServerPane(pub.get(0));
            }
        }
    }

    private void selectServerForPlay(ServerItem server) {
        if (server == null) return;
        mSelectedServer = server;
        if (mTvHomeSelectedServerName != null) {
            mTvHomeSelectedServerName.setText(server.getName() != null ? server.getName() : server.getAddress());
        }
        if (mTvHomeSelectedServerAddress != null) {
            mTvHomeSelectedServerAddress.setText(server.getAddress() + " · " + (server.getName() != null ? server.getName() : "SA-MP Server"));
        }
        if (mTvHomeSelectedServerStats != null) {
            mTvHomeSelectedServerStats.setText("Memeriksa status server...");
        }

        SampQueryClient.queryServer(server.getIp(), server.getPort(), info -> {
            if (info != null && info.isOnline()) {
                if (mTvHomeSelectedServerName != null) {
                    mTvHomeSelectedServerName.setText(info.getHostname());
                }
                if (mTvHomeSelectedServerStats != null) {
                    mTvHomeSelectedServerStats.setText("⚡ " + info.getPingMs() + " ms  ·  👥 " + info.getPlayers() + " / " + info.getMaxPlayers() + " Pemain");
                    mTvHomeSelectedServerStats.setTextColor(Color.parseColor("#10B981"));
                }
            } else {
                if (mTvHomeSelectedServerStats != null) {
                    mTvHomeSelectedServerStats.setText("Server Offline");
                    mTvHomeSelectedServerStats.setTextColor(Color.parseColor("#EF4444"));
                }
            }
        });
    }

    private void updateServerPane(ServerItem server) {
        if (server == null) return;
        mSelectedServer = server;
        if (mTvPaneServerName != null) {
            mTvPaneServerName.setText(server.getName() != null ? server.getName() : server.getAddress());
        }
        if (mTvPaneServerAddress != null) {
            mTvPaneServerAddress.setText("Alamat IP: " + server.getAddress());
        }
        if (mTvPaneServerPing != null) {
            mTvPaneServerPing.setText("Ping: ...");
        }
        if (mTvPaneServerPlayers != null) {
            mTvPaneServerPlayers.setText("Pemain: - / -");
        }
        if (mTvPaneServerGamemode != null) {
            mTvPaneServerGamemode.setText("Mode: ...");
        }
        if (mTvPaneServerRules != null) {
            mTvPaneServerRules.setText("Menghubungi server via UDP...");
        }
        if (mTvPaneServerPlayersList != null) {
            mTvPaneServerPlayersList.setText("Memuat daftar pemain...");
        }
        if (mBtnPaneConnect != null) {
            mBtnPaneConnect.setOnClickListener(v -> launchGameWithServer(server));
        }

        SampQueryClient.queryServerDetails(server.getIp(), server.getPort(), info -> {
            if (info != null && info.isOnline()) {
                if (mTvPaneServerName != null) {
                    mTvPaneServerName.setText(info.getHostname());
                }
                if (mTvPaneServerPing != null) {
                    mTvPaneServerPing.setText("Ping: " + info.getPingMs() + "ms");
                }
                if (mTvPaneServerPlayers != null) {
                    mTvPaneServerPlayers.setText("Pemain: " + info.getPlayers() + "/" + info.getMaxPlayers());
                }
                if (mTvPaneServerGamemode != null) {
                    mTvPaneServerGamemode.setText("Mode: " + info.getGamemode());
                }
                if (mTvPaneServerRules != null) {
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, String> e : info.getRules().entrySet()) {
                        sb.append(e.getKey()).append("=").append(e.getValue()).append("  |  ");
                    }
                    mTvPaneServerRules.setText(sb.length() > 0 ? sb.toString() : "Tidak ada rules tambahan.");
                }
                if (mTvPaneServerPlayersList != null) {
                    if (!info.getPlayerList().isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (ServerInfo.PlayerEntry p : info.getPlayerList()) {
                            sb.append("• ").append(p.getName()).append(" (Skor: ").append(p.getScore()).append(" | Ping: ").append(p.getPing()).append("ms)\n");
                        }
                        mTvPaneServerPlayersList.setText(sb.toString().trim());
                    } else if (info.getPlayers() == 0) {
                        mTvPaneServerPlayersList.setText("Tidak ada pemain di server saat ini.");
                    } else {
                        mTvPaneServerPlayersList.setText("Server membatasi query daftar nama pemain.");
                    }
                }
            } else {
                if (mTvPaneServerPing != null) {
                    mTvPaneServerPing.setText("Ping: Offline");
                }
                if (mTvPaneServerPlayers != null) {
                    mTvPaneServerPlayers.setText("Pemain: 0/0");
                }
                if (mTvPaneServerGamemode != null) {
                    mTvPaneServerGamemode.setText("Mode: -");
                }
                if (mTvPaneServerRules != null) {
                    mTvPaneServerRules.setText("Server tidak merespons query UDP.");
                }
                if (mTvPaneServerPlayersList != null) {
                    mTvPaneServerPlayersList.setText("Server offline.");
                }
            }
        });
    }

    private void loadPopularServers() {
        if (mLvPopularServers == null) return;
        List<ServerItem> publicServers = mRepo.getPublicServers();
        ServerAdapter adapter = new ServerAdapter(this, publicServers, false, new ServerAdapter.OnServerActionListener() {
            @Override
            public void onConnect(ServerItem server) {
                selectServerForPlay(server);
                launchGameWithServer(server);
            }

            @Override
            public void onDelete(ServerItem server) {}

            @Override
            public void onInspect(ServerItem server) {
                selectServerForPlay(server);
                Toast.makeText(LauncherActivity.this, "Server terpilih: " + server.getAddress(), Toast.LENGTH_SHORT).show();
            }
        });
        mLvPopularServers.setAdapter(adapter);
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

            @Override
            public void onInspect(ServerItem server) {
                updateServerPane(server);
                showServerDetailsDialog(server);
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

            @Override
            public void onInspect(ServerItem server) {
                updateServerPane(server);
                showServerDetailsDialog(server);
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

    private void showServerDetailsDialog(ServerItem server) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_server_details, null);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        TextView tvName = dialogView.findViewById(R.id.tv_details_name);
        TextView tvAddress = dialogView.findViewById(R.id.tv_details_address);
        TextView tvPing = dialogView.findViewById(R.id.tv_details_ping);
        View viewStatus = dialogView.findViewById(R.id.view_details_status);
        TextView tvGamemode = dialogView.findViewById(R.id.tv_details_gamemode);
        TextView tvRules = dialogView.findViewById(R.id.tv_details_rules);
        TextView tvPlayersTitle = dialogView.findViewById(R.id.tv_details_players_title);
        TextView tvPlayersList = dialogView.findViewById(R.id.tv_details_players_list);
        Button btnClose = dialogView.findViewById(R.id.btn_details_close);
        Button btnConnect = dialogView.findViewById(R.id.btn_details_connect);

        tvName.setText(server.getName() != null ? server.getName() : server.getAddress());
        tvAddress.setText(server.getAddress());
        tvPing.setText("Mengambil ping...");
        viewStatus.setBackgroundColor(Color.parseColor("#94A3B8"));
        tvGamemode.setText("Gamemode: Memuat...");
        tvRules.setText("Rules: Menghubungi server via UDP...");
        tvPlayersTitle.setText("Pemain Online (Memuat...)");
        tvPlayersList.setText("Mengambil daftar pemain...");

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnConnect.setOnClickListener(v -> {
            dialog.dismiss();
            launchGameWithServer(server);
        });

        dialog.show();

        // Query full UDP opcodes ('i', 'r', 'd')
        SampQueryClient.queryServerDetails(server.getIp(), server.getPort(), info -> {
            if (!dialog.isShowing()) return;
            if (info != null && info.isOnline()) {
                tvName.setText(info.getHostname());
                tvPing.setText(info.getPingMs() + "ms");
                viewStatus.setBackgroundColor(Color.parseColor("#10B981"));
                tvGamemode.setText("Gamemode: " + info.getGamemode() + " | Sandi: " + (info.isPassworded() ? "Ya" : "Tidak"));

                // Format rules
                if (!info.getRules().isEmpty()) {
                    StringBuilder sbRules = new StringBuilder();
                    for (Map.Entry<String, String> entry : info.getRules().entrySet()) {
                        sbRules.append(entry.getKey()).append("=").append(entry.getValue()).append("  |  ");
                    }
                    tvRules.setText(sbRules.toString());
                } else {
                    tvRules.setText("Rules: Tidak ada metadata rules.");
                }

                // Format players
                tvPlayersTitle.setText("Pemain Online (" + info.getPlayers() + " / " + info.getMaxPlayers() + ")");
                if (!info.getPlayerList().isEmpty()) {
                    StringBuilder sbPlayers = new StringBuilder();
                    for (ServerInfo.PlayerEntry p : info.getPlayerList()) {
                        sbPlayers.append("• ").append(p.getName())
                                 .append(" (Skor: ").append(p.getScore())
                                 .append(" | Ping: ").append(p.getPing()).append("ms)\n");
                    }
                    tvPlayersList.setText(sbPlayers.toString().trim());
                } else if (info.getPlayers() == 0) {
                    tvPlayersList.setText("Tidak ada pemain di server saat ini.");
                } else {
                    tvPlayersList.setText("Server membatasi query daftar nama pemain.");
                }
            } else {
                tvPing.setText("Offline");
                viewStatus.setBackgroundColor(Color.parseColor("#EF4444"));
                tvGamemode.setText("Gamemode: -");
                tvRules.setText("Server tidak merespons paket query UDP.");
                tvPlayersTitle.setText("Pemain Online (0 / 0)");
                tvPlayersList.setText("Server sedang offline atau tidak dapat dijangkau.");
            }
        });
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

        if (mCbFastChat != null) {
            mCbFastChat.setChecked(mSettings.isFastChat());
        }
        if (mCbVoiceChat != null) {
            mCbVoiceChat.setChecked(mSettings.isVoiceChat());
        }

        // Check Cache
        CacheVerifier.CacheStatus status = CacheVerifier.checkGameData(this);
        java.io.File resaDir = CacheVerifier.findResaCacheDir();
        if (status.isComplete()) {
            mTvCacheStatus.setText("Status: Lengkap (Game siap dimainkan)\n" + status.getMissingSummary() + "\nLokasi: " + status.getBaseDir().getAbsolutePath());
            mTvCacheStatus.setTextColor(Color.parseColor("#10B981"));
            mBtnDownloadCache.setText("GAME DATA LENGKAP (VERIFIKASI ULANG)");
            mBtnDownloadCache.setBackgroundColor(Color.parseColor("#334155"));
            if (mBtnSyncResa != null) {
                mBtnSyncResa.setText("TERPASANG (SINKRONKAN ULANG DARI RESA)");
                mBtnSyncResa.setBackgroundColor(Color.parseColor("#334155"));
            }
        } else {
            mTvCacheStatus.setText("Status: Cache belum lengkap\n" + status.getMissingSummary() + "\nLokasi target: " + status.getBaseDir().getAbsolutePath());
            mTvCacheStatus.setTextColor(Color.parseColor("#F59E0B"));
            mBtnDownloadCache.setText("🌐 UNDUH DARI CDN RESA:MP (766 MB)");
            mBtnDownloadCache.setBackgroundColor(Color.parseColor("#0EA5E9"));
            if (mBtnSyncResa != null) {
                if (resaDir != null) {
                    mBtnSyncResa.setVisibility(View.VISIBLE);
                    mBtnSyncResa.setText("⚡ SINKRONKAN DARI RESA MULTIPLAYER (INSTAN LOKAL)");
                    mBtnSyncResa.setBackgroundColor(Color.parseColor("#10B981"));
                } else {
                    mBtnSyncResa.setVisibility(View.GONE);
                }
            }
        }
    }

    private void startResaLocalSync() {
        if (CacheDownloader.isDownloading()) {
            Toast.makeText(this, "Operasi download/sinkronisasi sedang berjalan...", Toast.LENGTH_SHORT).show();
            return;
        }

        java.io.File resaDir = CacheVerifier.findResaCacheDir();
        if (resaDir == null) {
            Toast.makeText(this, "Folder ReSA Multiplayer tidak ditemukan di penyimpanan perangkat.", Toast.LENGTH_LONG).show();
            return;
        }

        mPbCacheDownload.setVisibility(View.VISIBLE);
        mPbCacheDownload.setProgress(0);
        if (mBtnSyncResa != null) {
            mBtnSyncResa.setEnabled(false);
            mBtnSyncResa.setText("Menyalin berkas lokal...");
        }
        mBtnDownloadCache.setEnabled(false);

        CacheDownloader.syncFromLocalDirectory(resaDir, getExternalFilesDir(null), new CacheDownloader.DownloadCallback() {
            @Override
            public void onProgress(int percent, long currentBytes, long totalBytes) {
                mPbCacheDownload.setProgress(percent);
                long mbCur = currentBytes / (1024 * 1024);
                long mbTot = totalBytes / (1024 * 1024);
                mTvCacheStatus.setText("Menyalin Berkas Lokal: " + percent + "% (" + mbCur + " MB / " + mbTot + " MB)");
            }

            @Override
            public void onExtracting(String entryName) {
                mTvCacheStatus.setText("Menyalin: " + entryName);
            }

            @Override
            public void onComplete(java.io.File destinationDir) {
                mPbCacheDownload.setVisibility(View.GONE);
                if (mBtnSyncResa != null) mBtnSyncResa.setEnabled(true);
                mBtnDownloadCache.setEnabled(true);
                Toast.makeText(LauncherActivity.this, "Game data berhasil disinkronkan dari ReSA Multiplayer!", Toast.LENGTH_LONG).show();
                refreshSettingsValues();
            }

            @Override
            public void onError(String errorMessage) {
                mPbCacheDownload.setVisibility(View.GONE);
                if (mBtnSyncResa != null) mBtnSyncResa.setEnabled(true);
                mBtnDownloadCache.setEnabled(true);
                Toast.makeText(LauncherActivity.this, "Gagal sinkronisasi: " + errorMessage, Toast.LENGTH_LONG).show();
                refreshSettingsValues();
            }
        });
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

        // CDN endpoint from saved settings or input field
        String cacheUrl = (mEtCacheUrl != null && mEtCacheUrl.getText().length() > 0)
                ? mEtCacheUrl.getText().toString().trim()
                : mSettings.getCacheUrl();

        if (cacheUrl == null || cacheUrl.trim().isEmpty()) {
            cacheUrl = SettingsManager.OFFICIAL_RESA_CDN_MANIFEST;
        }

        CacheDownloader.downloadFromCdnOrZip(cacheUrl, getExternalFilesDir(null), new CacheDownloader.DownloadCallback() {
            @Override
            public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                mPbCacheDownload.setProgress(percent);
                long mbDownloaded = downloadedBytes / (1024 * 1024);
                long mbTotal = totalBytes / (1024 * 1024);
                mTvCacheStatus.setText("Mengunduh Cache CDN: " + percent + "% (" + mbDownloaded + " MB / " + mbTotal + " MB)");
            }

            @Override
            public void onExtracting(String entryName) {
                mTvCacheStatus.setText("Mengunduh: " + entryName);
            }

            @Override
            public void onComplete(java.io.File destinationDir) {
                mPbCacheDownload.setVisibility(View.GONE);
                mBtnDownloadCache.setEnabled(true);
                Toast.makeText(LauncherActivity.this, "Game data berhasil diunduh dari CDN!", Toast.LENGTH_LONG).show();
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

        if (mCbFastChat != null) {
            mSettings.setFastChat(mCbFastChat.isChecked());
        }
        if (mCbVoiceChat != null) {
            mSettings.setVoiceChat(mCbVoiceChat.isChecked());
        }

        if (mEtCacheUrl != null) {
            String url = mEtCacheUrl.getText().toString().trim();
            if (!url.isEmpty()) {
                mSettings.setCacheUrl(url);
            }
        }

        Toast.makeText(this, "Pengaturan berhasil disimpan!", Toast.LENGTH_SHORT).show();
    }

    private void launchOfflineGame() {
        Intent intent = new Intent(this, GTASA.class);
        startActivity(intent);
    }

    private void launchGameWithServer(ServerItem server) {
        if (mEtNickname != null && mEtNickname.getText().length() > 0) {
            String inputName = mEtNickname.getText().toString().trim();
            if (SettingsManager.isValidNickname(inputName)) {
                mSettings.setNickname(inputName);
            }
        }
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
