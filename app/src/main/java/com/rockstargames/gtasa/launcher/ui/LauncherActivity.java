package com.rockstargames.gtasa.launcher.ui;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.rockstargames.gtasa.GTASA;
import com.rockstargames.gtasa.R;
import com.rockstargames.gtasa.launcher.data.AlLoaderManager;
import com.rockstargames.gtasa.launcher.data.CacheDownloader;
import com.rockstargames.gtasa.launcher.data.CacheVerifier;
import com.rockstargames.gtasa.launcher.data.CrashLogHelper;
import com.rockstargames.gtasa.launcher.data.FontManager;
import com.rockstargames.gtasa.launcher.data.ModLoaderManager;
import com.rockstargames.gtasa.launcher.data.ServerItem;
import com.rockstargames.gtasa.launcher.data.ServerRepository;
import com.rockstargames.gtasa.launcher.data.SettingsManager;
import com.rockstargames.gtasa.launcher.data.ThemeManager;
import com.rockstargames.gtasa.launcher.net.MasterlistClient;
import com.rockstargames.gtasa.launcher.net.SampQueryClient;
import com.rockstargames.gtasa.launcher.net.ServerInfo;
import com.rockstargames.gtasa.launcher.service.DownloadService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ROMP by HARFA — Universal Ad-Free SA-MP / open.mp Mobile Launcher.
 * Implements complete feature parity:
 * - Favorites / Hosted (live open.mp masterlist) / Official server tabs + filters + sorting + password prompts
 * - Deep Join Links (romp://connect, resa://connect, https://resa-mp.com/join)
 * - In-app File Manager, Code/INI Editor, and Android SAF DocumentsProvider ("Buka di Files")
 * - Mod Loader (DFF/TXD/PNG/Sound/Modpack ZIP), Game Textures (texdb ZIP), & AL Loader (Lua Scripts + 7 recommended + API Docs)
 * - Full 142+ settings catalogue, Crosshair Editor, 18+ Font Picker, Theme/Hero/Language Customizer, Screenshot Gallery & Logs Viewer
 * - 100% Ad-Free & Tracker-Free experience branded ROMP by HARFA
 */
public class LauncherActivity extends Activity {

    private static final int REQ_PERM_RECORD_AUDIO = 1001;
    private static final int REQ_PERM_NOTIFICATIONS = 1002;

    private static final int PICK_CROSSHAIR_IMAGE = 2001;
    private static final int PICK_CUSTOM_FONT = 2002;
    private static final int PICK_CUSTOM_BG = 2003;
    private static final int PICK_CUSTOM_LOGO = 2004;
    private static final int PICK_GENERIC_FILE = 2005;
    private static final int PICK_MOD_FILE = 2006;
    private static final int PICK_MODPACK_ZIP = 2007;
    private static final int PICK_LUA_SCRIPT = 2008;
    private static final int PICK_TEXDB_ZIP = 2009;

    private ServerRepository mRepo;
    private SettingsManager mSettings;
    private ThemeManager mTheme;
    private FontManager mFontManager;

    private SettingsViewController mSettingsController;
    private FilesWorkspaceController mFilesController;
    private File mPendingImportTargetDir;
    private ServerItem mPendingOnlineLaunchServer;

    // Navigation Rail
    private Button mBtnNavHome;
    private Button mBtnNavServers;
    private Button mBtnNavFiles;
    private Button mBtnNavSettings;

    // Top Brand & Theme
    private ImageView mIvNavEmblem;
    private ImageView mIvHeroBg;
    private TextView mTvBrandTitle;
    private TextView mTvBrandBadge;
    private Button mBtnThemeQuick;

    // View Containers
    private View mLayoutHomeView;
    private View mLayoutServerListContainer;
    private ScrollView mLayoutFilesView;
    private ScrollView mLayoutSettings;
    private LinearLayout mLayoutFilesWorkspaceContainer;
    private LinearLayout mLayoutSettingsCatalogueContainer;

    // Home View Elements
    private LinearLayout mLayoutCrashBanner;
    private Button mBtnViewCrashLogs;
    private Button mBtnDismissCrash;
    private Button mBtnNickHistory;
    private Button mBtnHomeLayoutEditor;
    private Button mBtnHomeLogs;
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
    private Button mBtnPaneCopyAddress;
    private Button mBtnPaneStarFav;
    private Button mBtnPaneEditFav;
    private Button mBtnPaneShareLink;

    private Button mTabFavorites;
    private Button mTabHosted;
    private Button mTabInternet;
    private Button mBtnAddServer;
    private CheckBox mCbFilterOnline;
    private CheckBox mCbFilterNoFull;
    private CheckBox mCbFilterNoPass;
    private Button mBtnSortServers;

    // Settings & Files Elements
    private EditText mEtNickname;
    private EditText mEtSettingsNickname;
    private Spinner mSpinnerFps;
    private CheckBox mCbFastChat;
    private CheckBox mCbVoiceChat;
    private CheckBox mCbCefOverlay;
    private EditText mEtCacheUrl;
    private TextView mTvCacheStatus;
    private TextView mTvFilesBreakdown;
    private Button mBtnSyncResa;
    private Button mBtnDownloadCache;
    private Button mBtnInstallLocalZip;
    private ProgressBar mPbCacheDownload;
    private Button mBtnSaveSettings;
    private Button mBtnRefreshServers;

    private EditText mEtSearch;
    private ServerAdapter mCurrentAdapter;

    private int mServerTabMode = 0; // 0=Favorites, 1=Hosted, 2=Official
    private int mSortMode = ServerAdapter.SORT_DEFAULT;
    private ServerItem mSelectedServer;

    private final ExecutorService mBgExecutor = Executors.newSingleThreadExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mFilterRunnable = this::applyServerFilters;
    private boolean mSettingsBuilt = false;
    private boolean mFilesRendered = false;
    private int mCacheScanGen = 0;
    private String mLoadedHeroKey = "";
    private String mLoadedLogoKey = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);
        requestHighestRefreshRate();
        applySafeAreaInsets();

        mRepo = new ServerRepository(this);
        mSettings = new SettingsManager(this);
        mTheme = new ThemeManager(this);
        mFontManager = new FontManager(this);

        initViews();
        setupNavigationRail();
        setupSubTabs();
        setupSettingsView();
        setupExtendedControllers();
        applyThemeAndHero();
        refreshSettingsValues();
        updateCrashBanner();
        setupDefaultServer();
        showHomeTab();

        if (mSettings.isAlLoaderEnabled()) {
            mBgExecutor.execute(() -> AlLoaderManager.ensureLibrariesExtracted(getApplicationContext()));
        }
        requestNotificationPermissionIfNeeded();
        handleIncomingJoinIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        mMainHandler.removeCallbacksAndMessages(null);
        mBgExecutor.shutdownNow();
        super.onDestroy();
    }

    /** Requests native 90Hz/120Hz display mode on supported phones (MIUI/Android 6+). */
    private void requestHighestRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            Window window = getWindow();
            if (window == null) return;
            Display display = getWindowManager().getDefaultDisplay();
            if (display == null) return;
            Display.Mode current = display.getMode();
            Display.Mode best = current;
            for (Display.Mode mode : display.getSupportedModes()) {
                if (mode.getPhysicalWidth() == current.getPhysicalWidth()
                        && mode.getPhysicalHeight() == current.getPhysicalHeight()
                        && mode.getRefreshRate() > best.getRefreshRate()) {
                    best = mode;
                }
            }
            if (best.getModeId() != current.getModeId()) {
                WindowManager.LayoutParams lp = window.getAttributes();
                lp.preferredDisplayModeId = best.getModeId();
                window.setAttributes(lp);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingJoinIntent(intent);
    }

    private void initViews() {
        mBtnNavHome = findViewById(R.id.btn_nav_home);
        mBtnNavServers = findViewById(R.id.btn_nav_servers);
        mBtnNavFiles = findViewById(R.id.btn_nav_files);
        mBtnNavSettings = findViewById(R.id.btn_nav_settings);

        mIvNavEmblem = findViewById(R.id.iv_nav_emblem);
        mIvHeroBg = findViewById(R.id.iv_home_hero_bg);
        mTvBrandTitle = findViewById(R.id.tv_brand_title);
        mTvBrandBadge = findViewById(R.id.tv_brand_badge);
        mBtnThemeQuick = findViewById(R.id.btn_theme_quick);

        mLayoutHomeView = findViewById(R.id.layout_home_view);
        mLayoutServerListContainer = findViewById(R.id.layout_server_list_container);
        mLayoutFilesView = findViewById(R.id.layout_files_view);
        mLayoutSettings = findViewById(R.id.layout_settings_view);
        mLayoutFilesWorkspaceContainer = findViewById(R.id.layout_files_workspace_container);
        mLayoutSettingsCatalogueContainer = findViewById(R.id.layout_settings_catalogue_container);

        mLayoutCrashBanner = findViewById(R.id.layout_crash_banner);
        mBtnViewCrashLogs = findViewById(R.id.btn_view_crash_logs);
        mBtnDismissCrash = findViewById(R.id.btn_dismiss_crash);
        mBtnNickHistory = findViewById(R.id.btn_nick_history);
        mBtnHomeLayoutEditor = findViewById(R.id.btn_home_layout_editor);
        mBtnHomeLogs = findViewById(R.id.btn_home_logs);
        mTvHomeSelectedServerName = findViewById(R.id.tv_home_selected_server_name);
        mTvHomeSelectedServerAddress = findViewById(R.id.tv_home_selected_server_address);
        mTvHomeSelectedServerStats = findViewById(R.id.tv_home_selected_server_stats);
        mBtnMainOnline = findViewById(R.id.btn_main_online);
        mBtnMainOffline = findViewById(R.id.btn_main_offline);
        mLvPopularServers = findViewById(R.id.lv_popular_servers);

        mListViewServers = findViewById(R.id.lv_servers);
        mTvPaneServerName = findViewById(R.id.tv_pane_server_name);
        mTvPaneServerAddress = findViewById(R.id.tv_pane_server_address);
        mTvPaneServerPing = findViewById(R.id.tv_pane_server_ping);
        mTvPaneServerPlayers = findViewById(R.id.tv_pane_server_players);
        mTvPaneServerGamemode = findViewById(R.id.tv_pane_server_gamemode);
        mTvPaneServerRules = findViewById(R.id.tv_pane_server_rules);
        mTvPaneServerPlayersList = findViewById(R.id.tv_pane_server_players_list);
        mBtnPaneConnect = findViewById(R.id.btn_pane_connect);
        mBtnPaneCopyAddress = findViewById(R.id.btn_pane_copy_address);
        mBtnPaneStarFav = findViewById(R.id.btn_pane_star_fav);
        mBtnPaneEditFav = findViewById(R.id.btn_pane_edit_fav);
        mBtnPaneShareLink = findViewById(R.id.btn_pane_share_link);

        mTabFavorites = findViewById(R.id.tab_favorites);
        mTabHosted = findViewById(R.id.tab_hosted);
        mTabInternet = findViewById(R.id.tab_internet);
        mBtnAddServer = findViewById(R.id.btn_add_server);
        mCbFilterOnline = findViewById(R.id.cb_filter_online);
        mCbFilterNoFull = findViewById(R.id.cb_filter_nofull);
        mCbFilterNoPass = findViewById(R.id.cb_filter_nopass);
        mBtnSortServers = findViewById(R.id.btn_sort_servers);

        mEtNickname = findViewById(R.id.et_nickname);
        mEtSettingsNickname = findViewById(R.id.et_settings_nickname);
        mSpinnerFps = findViewById(R.id.spinner_fps);
        mCbFastChat = findViewById(R.id.cb_fast_chat);
        mCbVoiceChat = findViewById(R.id.cb_voice_chat);
        mCbCefOverlay = findViewById(R.id.cb_cef_overlay);
        mEtCacheUrl = findViewById(R.id.et_cache_url);
        mTvCacheStatus = findViewById(R.id.tv_cache_status);
        mTvFilesBreakdown = findViewById(R.id.tv_files_breakdown);
        mBtnSyncResa = findViewById(R.id.btn_sync_resa);
        mBtnDownloadCache = findViewById(R.id.btn_download_cache);
        mBtnInstallLocalZip = findViewById(R.id.btn_install_local_zip);
        mPbCacheDownload = findViewById(R.id.pb_cache_download);
        mBtnSaveSettings = findViewById(R.id.btn_save_settings);
        mEtSearch = findViewById(R.id.et_search_servers);
        mBtnRefreshServers = findViewById(R.id.btn_refresh_servers);

        if (mBtnThemeQuick != null) {
            mBtnThemeQuick.setOnClickListener(v -> openThemeCustomizer());
        }

        if (mBtnNickHistory != null) {
            mBtnNickHistory.setOnClickListener(v -> showNicknameHistoryDialog());
        }

        if (mBtnViewCrashLogs != null) {
            mBtnViewCrashLogs.setOnClickListener(v -> LogsViewerDialog.show(this));
        }

        if (mBtnDismissCrash != null) {
            mBtnDismissCrash.setOnClickListener(v -> {
                CrashLogHelper.clearCrashFlag(this);
                updateCrashBanner();
            });
        }

        if (mBtnHomeLayoutEditor != null) {
            mBtnHomeLayoutEditor.setOnClickListener(v -> {
                Toast.makeText(this, "Membuka Editor Kontrol & HUD...", Toast.LENGTH_SHORT).show();
                mSettings.launchLayoutEditor(this);
            });
        }

        if (mBtnHomeLogs != null) {
            mBtnHomeLogs.setOnClickListener(v -> LogsViewerDialog.show(this));
        }

        if (mBtnRefreshServers != null) {
            mBtnRefreshServers.setOnClickListener(v -> {
                if (mServerTabMode == 0) loadFavoritesList();
                else if (mServerTabMode == 1) loadHostedList(true);
                else loadInternetList();
                Toast.makeText(this, "Memperbarui status server...", Toast.LENGTH_SHORT).show();
            });
        }

        if (mBtnPaneCopyAddress != null) {
            mBtnPaneCopyAddress.setOnClickListener(v -> {
                if (mSelectedServer != null) {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Server Address", mSelectedServer.getAddress());
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(this, "Alamat " + mSelectedServer.getAddress() + " disalin!", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }

        if (mBtnPaneStarFav != null) {
            mBtnPaneStarFav.setOnClickListener(v -> {
                if (mSelectedServer == null) return;
                if (mRepo.isFavorite(mSelectedServer.getIp(), mSelectedServer.getPort())) {
                    mRepo.removeFavorite(mSelectedServer.getId());
                    Toast.makeText(this, "Dihapus dari Favorit", Toast.LENGTH_SHORT).show();
                } else {
                    mRepo.addFavorite(mSelectedServer);
                    Toast.makeText(this, "Disimpan ke Favorit", Toast.LENGTH_SHORT).show();
                }
                if (mServerTabMode == 0) loadFavoritesList();
                updatePaneFavoriteButtons(mSelectedServer);
            });
        }

        if (mBtnPaneEditFav != null) {
            mBtnPaneEditFav.setOnClickListener(v -> {
                if (mSelectedServer != null) {
                    ServerBrowserController.showEditFavoriteDialog(this, mRepo, mSelectedServer, () -> {
                        if (mServerTabMode == 0) loadFavoritesList();
                        updateServerPane(mSelectedServer);
                    });
                }
            });
        }

        if (mBtnPaneShareLink != null) {
            mBtnPaneShareLink.setOnClickListener(v -> ServerBrowserController.shareJoinLink(this, mSelectedServer));
        }

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

        // Bottom info & utility buttons (preserving legacy view IDs while pointing to clean ROMP features)
        View btnFaq = findViewById(R.id.btn_link_discord);
        if (btnFaq != null) btnFaq.setOnClickListener(v -> HelpFaqChangelogDialogs.showHelpFaq(this));
        View btnChangelog = findViewById(R.id.btn_link_telegram);
        if (btnChangelog != null) btnChangelog.setOnClickListener(v -> HelpFaqChangelogDialogs.showChangelog(this));
        View btnGallery = findViewById(R.id.btn_link_website);
        if (btnGallery != null) btnGallery.setOnClickListener(v -> ScreenshotGalleryDialog.show(this));
        View btnAbout = findViewById(R.id.btn_link_about);
        if (btnAbout != null) btnAbout.setOnClickListener(v -> HelpFaqChangelogDialogs.showAbout(this));

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

        TextWatcher searchWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                mMainHandler.removeCallbacks(mFilterRunnable);
                mMainHandler.postDelayed(mFilterRunnable, 120);
            }
            @Override public void afterTextChanged(Editable s) {}
        };
        if (mEtSearch != null) mEtSearch.addTextChangedListener(searchWatcher);

        View.OnClickListener filterClick = v -> applyServerFilters();
        if (mCbFilterOnline != null) mCbFilterOnline.setOnClickListener(filterClick);
        if (mCbFilterNoFull != null) mCbFilterNoFull.setOnClickListener(filterClick);
        if (mCbFilterNoPass != null) mCbFilterNoPass.setOnClickListener(filterClick);
        if (mBtnSortServers != null) mBtnSortServers.setOnClickListener(v -> showSortPicker());
    }

    private void setupExtendedControllers() {
        mSettingsController = new SettingsViewController(
                this,
                mSettings,
                mTheme,
                mFontManager,
                mLayoutSettingsCatalogueContainer,
                new SettingsViewController.HostActions() {
                    @Override public void onRequestMicPermission() { requestMicrophonePermission(); }
                    @Override public void onRequestCrosshairImage() { openFilePicker("image/*", PICK_CROSSHAIR_IMAGE); }
                    @Override public void onRequestCustomFont() { openFilePicker("*/*", PICK_CUSTOM_FONT); }
                    @Override public void onRequestThemeUpdate() { applyThemeAndHero(); }
                    @Override public void onRequestCustomBg() { openFilePicker("image/*", PICK_CUSTOM_BG); }
                    @Override public void onRequestCustomLogo() { openFilePicker("image/*", PICK_CUSTOM_LOGO); }
                    @Override public void onResetSettingsUi() { refreshSettingsValues(); }
                }
        );

        mFilesController = new FilesWorkspaceController(
                this,
                mSettings,
                mLayoutFilesWorkspaceContainer,
                new FilesWorkspaceController.PickerDelegate() {
                    @Override public void onRequestImportGenericFile(File targetDir) {
                        mPendingImportTargetDir = targetDir;
                        openFilePicker("*/*", PICK_GENERIC_FILE);
                    }
                    @Override public void onRequestImportModFile() { openFilePicker("*/*", PICK_MOD_FILE); }
                    @Override public void onRequestImportModpackZip() { openFilePicker("application/zip", PICK_MODPACK_ZIP); }
                    @Override public void onRequestImportLuaScript() { openFilePicker("*/*", PICK_LUA_SCRIPT); }
                    @Override public void onRequestImportTexdbZip() { openFilePicker("application/zip", PICK_TEXDB_ZIP); }
                }
        );
    }

    private void ensureSettingsBuilt() {
        if (!mSettingsBuilt && mSettingsController != null) {
            mSettingsController.buildUi();
            mSettingsBuilt = true;
        }
    }

    private void ensureFilesRendered() {
        if (mFilesController != null) {
            mFilesController.render();
            mFilesRendered = true;
        }
    }

    private void applySafeAreaInsets() {
        View root = findViewById(R.id.launcher_root);
        if (root == null) return;
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.navigationBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private void applyThemeAndHero() {
        int accent = mTheme.getAccentColor();
        if (mTvBrandBadge != null) {
            mTvBrandBadge.setTextColor(accent);
            ThemeManager.tintCompoundDrawables(mTvBrandBadge, accent);
        }
        if (mBtnRefreshServers != null) {
            mBtnRefreshServers.setTextColor(accent);
            ThemeManager.tintCompoundDrawables(mBtnRefreshServers, accent);
        }
        if (mBtnHomeLayoutEditor != null) {
            mBtnHomeLayoutEditor.setTextColor(accent);
            ThemeManager.tintCompoundDrawables(mBtnHomeLayoutEditor, accent);
        }
        ThemeManager.stylePrimaryCta(this, mBtnMainOnline, accent);
        ThemeManager.stylePrimaryCta(this, mBtnAddServer, accent);
        ThemeManager.stylePrimaryCta(this, mBtnPaneConnect, accent);
        ThemeManager.stylePrimaryCta(this, mBtnSaveSettings, accent);

        ColorStateList accentTint = ColorStateList.valueOf(accent);
        for (CheckBox cb : new CheckBox[]{mCbFastChat, mCbVoiceChat, mCbCefOverlay,
                mCbFilterOnline, mCbFilterNoFull, mCbFilterNoPass}) {
            if (cb != null) cb.setButtonTintList(accentTint);
        }

        TextView tvFooter = findViewById(R.id.tv_home_footer_status);
        if (tvFooter != null) {
            tvFooter.setText("Tema: " + mTheme.getHeroPresetLabel() + " · 100% Ad-Free & Privacy Safe");
            ThemeManager.tintCompoundDrawables(tvFooter, accent);
        }

        highlightNavButton(getActiveNavButton());
        highlightServerSubTab();
        if (mCurrentAdapter != null) {
            mCurrentAdapter.refreshTheme();
        }

        if (mIvHeroBg != null) {
            final String bgId = mTheme.getHeroBackgroundId();
            final String customBgPath = mTheme.getCustomBackgroundPath();
            final String heroKey = bgId + "|" + customBgPath;
            if (!heroKey.equals(mLoadedHeroKey)) {
                mLoadedHeroKey = heroKey;
                mBgExecutor.execute(() -> {
                    Bitmap bmp = ("custom".equals(bgId) && !customBgPath.isEmpty())
                            ? ThemeManager.decodeSampledBitmapFromFile(customBgPath, 1280, 720)
                            : ThemeManager.decodeSampledBitmapFromAsset(getApplicationContext(), "flutter_assets/assets/bg/" + bgId + ".webp", 1280, 720);
                    if (bmp != null) {
                        runOnUiThread(() -> {
                            if (!isFinishing() && heroKey.equals(mLoadedHeroKey) && mIvHeroBg != null) {
                                mIvHeroBg.setImageBitmap(bmp);
                                mIvHeroBg.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                });
            }
        }

        if (mIvNavEmblem != null) {
            final boolean useCustomLogo = mTheme.isCustomLogoEnabled();
            final String customLogoPath = mTheme.getCustomLogoPath();
            final String logoKey = useCustomLogo + "|" + customLogoPath;
            if (!logoKey.equals(mLoadedLogoKey)) {
                mLoadedLogoKey = logoKey;
                if (useCustomLogo && !customLogoPath.isEmpty()) {
                    mBgExecutor.execute(() -> {
                        Bitmap logo = ThemeManager.decodeSampledBitmapFromFile(customLogoPath, 256, 256);
                        runOnUiThread(() -> {
                            if (!isFinishing() && logoKey.equals(mLoadedLogoKey) && mIvNavEmblem != null) {
                                if (logo != null) mIvNavEmblem.setImageBitmap(logo);
                                else mIvNavEmblem.setImageResource(R.drawable.romp_emblem);
                            }
                        });
                    });
                } else {
                    mIvNavEmblem.setImageResource(R.drawable.romp_emblem);
                }
            }
        }
    }

    private Button getActiveNavButton() {
        if (mLayoutServerListContainer != null && mLayoutServerListContainer.getVisibility() == View.VISIBLE) return mBtnNavServers;
        if (mLayoutFilesView != null && mLayoutFilesView.getVisibility() == View.VISIBLE) return mBtnNavFiles;
        if (mLayoutSettings != null && mLayoutSettings.getVisibility() == View.VISIBLE) return mBtnNavSettings;
        return mBtnNavHome;
    }

    private void updateCrashBanner() {
        if (mLayoutCrashBanner == null) return;
        mLayoutCrashBanner.setVisibility(CrashLogHelper.hasCrashFlag(this) ? View.VISIBLE : View.GONE);
    }

    private void openThemeCustomizer() {
        ThemeCustomizerDialog.show(this, mTheme, new ThemeCustomizerDialog.ThemeCallback() {
            @Override public void onThemeUpdated() {
                applyThemeAndHero();
                if (mSettingsBuilt && mSettingsController != null) mSettingsController.refresh();
                if (mFilesRendered && mFilesController != null) mFilesController.render();
            }
            @Override public void onRequestCustomBackgroundPick() { openFilePicker("image/*", PICK_CUSTOM_BG); }
            @Override public void onRequestCustomLogoPick() { openFilePicker("image/*", PICK_CUSTOM_LOGO); }
        });
    }

    private void showNicknameHistoryDialog() {
        List<String> history = mSettings.getNicknameHistory();
        if (history.isEmpty()) {
            Toast.makeText(this, "Belum ada riwayat nickname.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] arr = history.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("Pilih Riwayat Nickname")
                .setItems(arr, (d, which) -> {
                    String chosen = arr[which];
                    mSettings.setNickname(chosen);
                    if (mEtNickname != null) mEtNickname.setText(chosen);
                    if (mEtSettingsNickname != null) mEtSettingsNickname.setText(chosen);
                })
                .show();
    }

    private void setupNavigationRail() {
        if (mBtnNavHome != null) mBtnNavHome.setOnClickListener(v -> showHomeTab());
        if (mBtnNavServers != null) mBtnNavServers.setOnClickListener(v -> showServersTab());
        if (mBtnNavFiles != null) mBtnNavFiles.setOnClickListener(v -> showFilesTab());
        if (mBtnNavSettings != null) mBtnNavSettings.setOnClickListener(v -> showSettingsTab());
    }

    private void setupSubTabs() {
        if (mTabFavorites != null) mTabFavorites.setOnClickListener(v -> showFavoritesTab());
        if (mTabHosted != null) mTabHosted.setOnClickListener(v -> showHostedTab());
        if (mTabInternet != null) mTabInternet.setOnClickListener(v -> showInternetTab());
    }

    private void showHomeTab() {
        switchVisibleTab(mLayoutHomeView, mBtnNavHome);
        updateCrashBanner();
        loadPopularServers();
    }

    private void showServersTab() {
        switchVisibleTab(mLayoutServerListContainer, mBtnNavServers);
        if (mServerTabMode == 0) loadFavoritesList();
        else if (mServerTabMode == 1) loadHostedList(false);
        else loadInternetList();
    }

    private void showFilesTab() {
        switchVisibleTab(mLayoutFilesView, mBtnNavFiles);
        refreshSettingsValues();
        if (!mFilesRendered) {
            ensureFilesRendered();
        }
    }

    private void showSettingsTab() {
        switchVisibleTab(mLayoutSettings, mBtnNavSettings);
        refreshSettingsValues();
        if (!mSettingsBuilt) {
            ensureSettingsBuilt();
        } else if (mSettingsController != null) {
            mSettingsController.refresh();
        }
    }

    private void switchVisibleTab(View target, Button navButton) {
        View[] tabs = {mLayoutHomeView, mLayoutServerListContainer, mLayoutFilesView, mLayoutSettings};
        for (View v : tabs) {
            if (v == null) continue;
            if (v == target) {
                if (v.getVisibility() != View.VISIBLE) {
                    v.setVisibility(View.VISIBLE);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled()) {
                        v.setAlpha(0f);
                        v.animate().alpha(1f).setDuration(130).start();
                    } else {
                        v.setAlpha(1f);
                    }
                }
            } else {
                v.setVisibility(View.GONE);
            }
        }
        highlightNavButton(navButton);
    }

    private void highlightNavButton(Button active) {
        int accent = mTheme != null ? mTheme.getAccentColor() : Color.parseColor("#38BDF8");
        int inactiveFg = Color.parseColor("#CBD5E1");
        Button[] all = {mBtnNavHome, mBtnNavServers, mBtnNavFiles, mBtnNavSettings};
        for (Button b : all) {
            if (b == null) continue;
            if (b == active) {
                b.setBackground(ThemeManager.createInteractivePill(
                        this, ThemeManager.withAlpha(accent, 36), ThemeManager.withAlpha(accent, 110),
                        ThemeManager.withAlpha(accent, 64), 12));
                b.setTextColor(accent);
                ThemeManager.tintCompoundDrawables(b, accent);
            } else {
                b.setBackgroundResource(R.drawable.bg_nav_item_unselected);
                b.setTextColor(inactiveFg);
                ThemeManager.tintCompoundDrawables(b, inactiveFg);
            }
        }
    }

    private void showFavoritesTab() {
        mServerTabMode = 0;
        highlightServerSubTab();
        loadFavoritesList();
    }

    private void showHostedTab() {
        mServerTabMode = 1;
        highlightServerSubTab();
        loadHostedList(false);
    }

    private void showInternetTab() {
        mServerTabMode = 2;
        highlightServerSubTab();
        loadInternetList();
    }

    private void highlightServerSubTab() {
        int accent = mTheme != null ? mTheme.getAccentColor() : Color.parseColor("#38BDF8");
        int inactiveFg = Color.parseColor("#CBD5E1");
        Button[] tabs = {mTabFavorites, mTabHosted, mTabInternet};
        for (int i = 0; i < tabs.length; i++) {
            Button tab = tabs[i];
            if (tab == null) continue;
            boolean active = (i == mServerTabMode);
            int fg = active ? accent : inactiveFg;
            tab.setTextColor(fg);
            ThemeManager.tintCompoundDrawables(tab, fg);
            if (active) {
                tab.setBackground(ThemeManager.createInteractivePill(
                        this, ThemeManager.withAlpha(accent, 38), ThemeManager.withAlpha(accent, 110),
                        ThemeManager.withAlpha(accent, 60), 10));
            } else {
                tab.setBackgroundResource(R.drawable.bg_nav_item_unselected);
            }
        }
    }

    private void setupDefaultServer() {
        List<ServerItem> list = mRepo.getFavorites();
        ServerItem defaultServer = !list.isEmpty() ? list.get(0) : new ServerItem("18.142.244.146", 7777, "RUANG ROLEPLAY (GTA SA)");
        selectServerForPlay(defaultServer);
        updateServerPane(defaultServer);
    }

    private void selectServerForPlay(ServerItem server) {
        if (server == null) return;
        mSelectedServer = server;
        String initialName = server.getName();
        if (initialName == null || initialName.isEmpty() || initialName.equals(server.getAddress())) {
            initialName = "18.142.244.146".equals(server.getIp()) ? "RUANG ROLEPLAY (GTA SA)" : server.getAddress();
        }
        if (mTvHomeSelectedServerName != null) mTvHomeSelectedServerName.setText(initialName);
        if (mTvHomeSelectedServerAddress != null) mTvHomeSelectedServerAddress.setText(server.getAddress() + " · " + initialName);
        if (mTvHomeSelectedServerStats != null) {
            mTvHomeSelectedServerStats.setText("Memeriksa status server...");
            mTvHomeSelectedServerStats.setTextColor(Color.parseColor("#94A3B8"));
        }

        SampQueryClient.queryServer(server.getIp(), server.getPort(), info -> {
            if (info != null && info.isOnline()) {
                server.setPassworded(info.isPassworded());
                if (mTvHomeSelectedServerName != null) {
                    mTvHomeSelectedServerName.setText(info.getHostname());
                    mTvHomeSelectedServerName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            info.isPassworded() ? R.drawable.ic_romp_lock : 0, 0, 0, 0);
                    mTvHomeSelectedServerName.setCompoundDrawablePadding(ThemeManager.dp(this, 6));
                }
                if (mTvHomeSelectedServerStats != null) {
                    String lock = info.isPassworded() ? "Terkunci · " : "";
                    mTvHomeSelectedServerStats.setText("ONLINE · " + lock + info.getPingMs() + " ms · " + info.getPlayers() + " / " + info.getMaxPlayers() + " Pemain");
                    mTvHomeSelectedServerStats.setTextColor(Color.parseColor("#22C55E"));
                }
            } else {
                if (mTvHomeSelectedServerStats != null) {
                    mTvHomeSelectedServerStats.setText("OFFLINE · Tidak merespons UDP");
                    mTvHomeSelectedServerStats.setTextColor(Color.parseColor("#F87171"));
                }
            }
        });
    }

    private void updateServerPane(ServerItem server) {
        if (server == null) return;
        mSelectedServer = server;
        if (mTvPaneServerName != null) mTvPaneServerName.setText(server.getName() != null ? server.getName() : server.getAddress());
        if (mTvPaneServerAddress != null) mTvPaneServerAddress.setText("Alamat IP: " + server.getAddress());
        if (mTvPaneServerPing != null) mTvPaneServerPing.setText("Ping: ...");
        if (mTvPaneServerPlayers != null) mTvPaneServerPlayers.setText("Pemain: - / -");
        if (mTvPaneServerGamemode != null) mTvPaneServerGamemode.setText("Mode: ...");
        if (mTvPaneServerRules != null) mTvPaneServerRules.setText("Menghubungi server via UDP...");
        if (mTvPaneServerPlayersList != null) mTvPaneServerPlayersList.setText("Memuat daftar pemain...");
        updatePaneFavoriteButtons(server);

        if (mBtnPaneConnect != null) {
            mBtnPaneConnect.setOnClickListener(v -> launchGameWithServer(server));
            mBtnPaneConnect.setOnLongClickListener(v -> {
                showConnectionMethodDialog(server, mSettings.getNickname());
                return true;
            });
        }

        SampQueryClient.queryServerDetails(server.getIp(), server.getPort(), info -> {
            if (info != null && info.isOnline()) {
                server.setPassworded(info.isPassworded());
                if (mTvPaneServerName != null) {
                    mTvPaneServerName.setText(info.getHostname());
                    mTvPaneServerName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            info.isPassworded() ? R.drawable.ic_romp_lock : 0, 0, 0, 0);
                    mTvPaneServerName.setCompoundDrawablePadding(ThemeManager.dp(this, 6));
                }
                if (mTvPaneServerPing != null) mTvPaneServerPing.setText("Ping: " + info.getPingMs() + "ms");
                if (mTvPaneServerPlayers != null) mTvPaneServerPlayers.setText("Pemain: " + info.getPlayers() + "/" + info.getMaxPlayers());
                if (mTvPaneServerGamemode != null) mTvPaneServerGamemode.setText("Mode: " + info.getGamemode());
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
                if (mTvPaneServerPing != null) mTvPaneServerPing.setText("Ping: Offline");
                if (mTvPaneServerPlayers != null) mTvPaneServerPlayers.setText("Pemain: 0/0");
                if (mTvPaneServerGamemode != null) mTvPaneServerGamemode.setText("Mode: -");
                if (mTvPaneServerRules != null) mTvPaneServerRules.setText("Server tidak merespons query UDP.");
                if (mTvPaneServerPlayersList != null) mTvPaneServerPlayersList.setText("Server offline.");
            }
        });
    }

    private void updatePaneFavoriteButtons(ServerItem server) {
        if (mBtnPaneStarFav == null || server == null) return;
        boolean fav = mRepo.isFavorite(server.getIp(), server.getPort());
        mBtnPaneStarFav.setText(fav ? "Tersimpan" : "Simpan");
        if (mBtnPaneEditFav != null) mBtnPaneEditFav.setVisibility(fav ? View.VISIBLE : View.GONE);
    }

    private void loadPopularServers() {
        if (mLvPopularServers == null) return;
        List<ServerItem> publicServers = mRepo.getPublicServers();
        ServerAdapter adapter = new ServerAdapter(this, publicServers, false, new ServerAdapter.OnServerActionListener() {
            @Override public void onConnect(ServerItem server) {
                selectServerForPlay(server);
                launchGameWithServer(server);
            }
            @Override public void onDelete(ServerItem server) {}
            @Override public void onInspect(ServerItem server) {
                selectServerForPlay(server);
                Toast.makeText(LauncherActivity.this, "Server terpilih: " + server.getAddress(), Toast.LENGTH_SHORT).show();
            }
        });
        mLvPopularServers.setAdapter(adapter);
    }

    private void loadFavoritesList() {
        List<ServerItem> favorites = mRepo.getFavorites();
        bindServerListAdapter(favorites, true);
    }

    private void loadInternetList() {
        List<ServerItem> officialServers = mRepo.getOfficialServers();
        bindServerListAdapter(officialServers, false);
    }

    private void loadHostedList(boolean forceRefresh) {
        if (mTvPaneServerRules != null) {
            mTvPaneServerRules.setText("Mengambil daftar server Hosted (api.open.mp/servers)...");
        }
        MasterlistClient.fetchHostedServers(forceRefresh, (servers, fromCache, error) -> {
            if (mServerTabMode != 1) return;
            if (servers.isEmpty() && error != null) {
                Toast.makeText(this, "Daftar Hosted gagal dimuat: " + error, Toast.LENGTH_SHORT).show();
            }
            bindServerListAdapter(servers, false);
        });
    }

    private void bindServerListAdapter(List<ServerItem> items, boolean isFavorites) {
        ServerAdapter adapter = new ServerAdapter(this, items, isFavorites, new ServerAdapter.OnServerActionListener() {
            @Override public void onConnect(ServerItem server) { launchGameWithServer(server); }
            @Override public void onDelete(ServerItem server) {
                mRepo.removeFavorite(server.getId());
                loadFavoritesList();
                Toast.makeText(LauncherActivity.this, "Server dihapus dari Favorit", Toast.LENGTH_SHORT).show();
            }
            @Override public void onInspect(ServerItem server) {
                updateServerPane(server);
                showServerDetailsDialog(server);
            }
        });
        mCurrentAdapter = adapter;
        mListViewServers.setAdapter(adapter);
        applyServerFilters();
    }

    private void applyServerFilters() {
        if (mCurrentAdapter == null) return;
        String q = mEtSearch != null ? mEtSearch.getText().toString() : "";
        boolean onlineOnly = mCbFilterOnline != null && mCbFilterOnline.isChecked();
        boolean hideFull = mCbFilterNoFull != null && mCbFilterNoFull.isChecked();
        boolean noPass = mCbFilterNoPass != null && mCbFilterNoPass.isChecked();
        mCurrentAdapter.setFilterOptions(q, onlineOnly, hideFull, noPass, mSortMode);
    }

    private void showSortPicker() {
        String[] opts = {"Default", "Pemain Terbanyak", "Ping Terendah", "Nama A-Z"};
        new AlertDialog.Builder(this)
                .setTitle("Urutkan Server")
                .setItems(opts, (d, which) -> {
                    mSortMode = which;
                    if (mBtnSortServers != null) mBtnSortServers.setText(opts[which] + " ▾");
                    applyServerFilters();
                })
                .show();
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

            ServerItem created = new ServerItem(ip, port, name.isEmpty() ? null : name);
            mRepo.addFavorite(created);
            selectServerForPlay(created);
            dialog.dismiss();
            if (mServerTabMode == 0) loadFavoritesList();
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
        viewStatus.setBackgroundResource(R.drawable.dot_pending);
        tvGamemode.setText("Gamemode: Memuat...");
        tvRules.setText("Rules: Menghubungi server via UDP...");
        tvPlayersTitle.setText("Pemain Online (Memuat...)");
        tvPlayersList.setText("Mengambil daftar pemain...");

        if (mTheme != null) ThemeManager.stylePrimaryCta(this, btnConnect, mTheme.getAccentColor());
        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnConnect.setOnClickListener(v -> {
            dialog.dismiss();
            launchGameWithServer(server);
        });
        dialog.show();

        SampQueryClient.queryServerDetails(server.getIp(), server.getPort(), info -> {
            if (!dialog.isShowing()) return;
            if (info != null && info.isOnline()) {
                server.setPassworded(info.isPassworded());
                tvName.setText(info.getHostname());
                tvName.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        info.isPassworded() ? R.drawable.ic_romp_lock : 0, 0, 0, 0);
                tvName.setCompoundDrawablePadding(ThemeManager.dp(this, 6));
                tvPing.setText(info.getPingMs() + " ms");
                viewStatus.setBackgroundResource(R.drawable.dot_online);
                tvGamemode.setText("Gamemode: " + info.getGamemode() + " | Sandi: " + (info.isPassworded() ? "Ya" : "Tidak"));

                if (!info.getRules().isEmpty()) {
                    StringBuilder sbRules = new StringBuilder();
                    for (Map.Entry<String, String> entry : info.getRules().entrySet()) {
                        sbRules.append(entry.getKey()).append("=").append(entry.getValue()).append("  |  ");
                    }
                    tvRules.setText(sbRules.toString());
                } else {
                    tvRules.setText("Rules: Tidak ada metadata rules.");
                }

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
                viewStatus.setBackgroundResource(R.drawable.dot_offline);
                tvGamemode.setText("Gamemode: -");
                tvRules.setText("Server tidak merespons paket query UDP.");
                tvPlayersTitle.setText("Pemain Online (0 / 0)");
                tvPlayersList.setText("Server sedang offline atau tidak dapat dijangkau.");
            }
        });
    }

    private void setupSettingsView() {
        String[] fpsOptions = {"30 FPS", "60 FPS", "90 FPS", "120 FPS"};
        ArrayAdapter<String> fpsAdapter = new ArrayAdapter<>(this, R.layout.item_spinner_romp, fpsOptions);
        fpsAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown_romp);
        mSpinnerFps.setAdapter(fpsAdapter);
        mBtnSaveSettings.setOnClickListener(v -> saveSettingsValues());
    }

    private void refreshSettingsValues() {
        if (mEtNickname != null) mEtNickname.setText(mSettings.getNickname());
        if (mEtCacheUrl != null) mEtCacheUrl.setText(mSettings.getCacheUrl());

        int currentFps = mSettings.getFpsLimit();
        if (currentFps == 30) mSpinnerFps.setSelection(0);
        else if (currentFps == 60) mSpinnerFps.setSelection(1);
        else if (currentFps == 90) mSpinnerFps.setSelection(2);
        else if (currentFps == 120) mSpinnerFps.setSelection(3);
        else mSpinnerFps.setSelection(2);

        if (mCbFastChat != null) mCbFastChat.setChecked(mSettings.isFastChat());
        if (mCbVoiceChat != null) mCbVoiceChat.setChecked(mSettings.isVoiceChat());
        if (mCbCefOverlay != null) mCbCefOverlay.setChecked(mSettings.isCefOverlayEnabled());
        if (mEtSettingsNickname != null) mEtSettingsNickname.setText(mSettings.getNickname());

        final int scanGen = ++mCacheScanGen;
        mBgExecutor.execute(() -> {
            CacheVerifier.CacheStatus status = CacheVerifier.checkGameData(getApplicationContext());
            File resaDir = CacheVerifier.findResaCacheDir();
            List<CacheVerifier.FolderInfo> folders = mTvFilesBreakdown != null
                    ? CacheVerifier.getFolderBreakdown(status.getBaseDir())
                    : null;
            runOnUiThread(() -> {
                if (isFinishing() || scanGen != mCacheScanGen) return;
                if (status.isComplete()) {
                    mTvCacheStatus.setText("Status: Lengkap (Game siap dimainkan)\n" + status.getMissingSummary() + "\nLokasi: " + status.getBaseDir().getAbsolutePath());
                    mTvCacheStatus.setTextColor(Color.parseColor("#22C55E"));
                    mBtnDownloadCache.setText("Verifikasi Ulang Data CDN");
                    mBtnDownloadCache.setBackgroundResource(R.drawable.bg_btn_secondary);
                    if (mBtnSyncResa != null) {
                        if (resaDir != null) {
                            mBtnSyncResa.setVisibility(View.VISIBLE);
                            mBtnSyncResa.setText("Sinkronkan Ulang Data Lokal");
                            mBtnSyncResa.setBackgroundResource(R.drawable.bg_btn_secondary);
                            mBtnSyncResa.setTextColor(Color.parseColor("#F8FAFC"));
                        } else {
                            mBtnSyncResa.setVisibility(View.GONE);
                        }
                    }
                } else {
                    mTvCacheStatus.setText("Status: Cache belum lengkap\n" + status.getMissingSummary() + "\nLokasi target: " + status.getBaseDir().getAbsolutePath());
                    mTvCacheStatus.setTextColor(Color.parseColor("#FBBF24"));
                    mBtnDownloadCache.setText("Unduh Data Gim CDN (766 MB)");
                    ThemeManager.stylePrimaryCta(LauncherActivity.this, mBtnDownloadCache, mTheme.getAccentColor());
                    if (mBtnSyncResa != null) {
                        if (resaDir != null) {
                            mBtnSyncResa.setVisibility(View.VISIBLE);
                            mBtnSyncResa.setText("Sinkronkan Data Gim Lokal (Instan)");
                            ThemeManager.stylePrimaryCta(LauncherActivity.this, mBtnSyncResa, Color.parseColor("#22C55E"));
                        } else {
                            mBtnSyncResa.setVisibility(View.GONE);
                        }
                    }
                }

                if (mTvFilesBreakdown != null && folders != null) {
                    if (folders.isEmpty()) {
                        mTvFilesBreakdown.setText("Belum ada berkas gim di folder target.");
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (CacheVerifier.FolderInfo info : folders) {
                            long mb = info.totalBytes / (1024 * 1024);
                            String badge = info.isComplete() ? "[Lengkap]" : (info.actualFiles > 0 ? "[Sebagian]" : "[Kosong]");
                            sb.append("• ").append(info.name).append("/ : ")
                              .append(info.actualFiles).append("/").append(info.expectedFiles).append(" berkas (")
                              .append(mb).append(" MB) · ").append(badge).append("\n");
                        }
                        mTvFilesBreakdown.setText(sb.toString().trim());
                    }
                }
            });
        });
    }

    private void startResaLocalSync() {
        if (CacheDownloader.isDownloading()) {
            Toast.makeText(this, "Operasi download/sinkronisasi sedang berjalan...", Toast.LENGTH_SHORT).show();
            return;
        }
        File resaDir = CacheVerifier.findResaCacheDir();
        if (resaDir == null) {
            Toast.makeText(this, "Folder cache lokal tidak ditemukan di penyimpanan perangkat.", Toast.LENGTH_LONG).show();
            return;
        }

        mPbCacheDownload.setVisibility(View.VISIBLE);
        mPbCacheDownload.setProgress(0);
        if (mBtnSyncResa != null) {
            mBtnSyncResa.setEnabled(false);
            mBtnSyncResa.setText("Menyalin berkas lokal...");
        }
        mBtnDownloadCache.setEnabled(false);
        DownloadService.startDownloadNotification(this, "Sinkronisasi Data ROMP");

        CacheDownloader.syncFromLocalDirectory(resaDir, getExternalFilesDir(null), new CacheDownloader.DownloadCallback() {
            @Override public void onProgress(int percent, long currentBytes, long totalBytes) {
                mPbCacheDownload.setProgress(percent);
                String detail = percent + "% (" + (currentBytes / (1024 * 1024)) + " MB / " + (totalBytes / (1024 * 1024)) + " MB)";
                mTvCacheStatus.setText("Menyalin Berkas Lokal: " + detail);
                DownloadService.updateDownloadNotification(LauncherActivity.this, "Sinkronisasi Data ROMP", detail, percent);
            }
            @Override public void onExtracting(String entryName) { mTvCacheStatus.setText("Menyalin: " + entryName); }
            @Override public void onComplete(File destinationDir) {
                DownloadService.stopDownloadNotification(LauncherActivity.this);
                mPbCacheDownload.setVisibility(View.GONE);
                if (mBtnSyncResa != null) mBtnSyncResa.setEnabled(true);
                mBtnDownloadCache.setEnabled(true);
                Toast.makeText(LauncherActivity.this, "Data gim berhasil disinkronkan!", Toast.LENGTH_LONG).show();
                refreshSettingsValues();
            }
            @Override public void onError(String errorMessage) {
                DownloadService.stopDownloadNotification(LauncherActivity.this);
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
        DownloadService.startDownloadNotification(this, "Mengunduh Data Gim ROMP");

        String cacheUrl = (mEtCacheUrl != null && mEtCacheUrl.getText().length() > 0)
                ? mEtCacheUrl.getText().toString().trim()
                : mSettings.getCacheUrl();
        if (cacheUrl == null || cacheUrl.trim().isEmpty()) {
            cacheUrl = SettingsManager.OFFICIAL_RESA_CDN_MANIFEST;
        }

        CacheDownloader.downloadFromCdnOrZip(cacheUrl, getExternalFilesDir(null), new CacheDownloader.DownloadCallback() {
            @Override public void onProgress(int percent, long downloadedBytes, long totalBytes) {
                mPbCacheDownload.setProgress(percent);
                String detail = percent + "% (" + (downloadedBytes / (1024 * 1024)) + " MB / " + (totalBytes / (1024 * 1024)) + " MB)";
                mTvCacheStatus.setText("Mengunduh Cache CDN: " + detail);
                DownloadService.updateDownloadNotification(LauncherActivity.this, "Mengunduh Data Gim ROMP", detail, percent);
            }
            @Override public void onExtracting(String entryName) { mTvCacheStatus.setText("Mengunduh: " + entryName); }
            @Override public void onComplete(File destinationDir) {
                DownloadService.stopDownloadNotification(LauncherActivity.this);
                mPbCacheDownload.setVisibility(View.GONE);
                mBtnDownloadCache.setEnabled(true);
                Toast.makeText(LauncherActivity.this, "Data gim berhasil diunduh dari CDN!", Toast.LENGTH_LONG).show();
                refreshSettingsValues();
            }
            @Override public void onError(String errorMessage) {
                DownloadService.stopDownloadNotification(LauncherActivity.this);
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
            File file = new File(path);
            if (!file.exists()) {
                Toast.makeText(this, "File tidak ditemukan: " + path, Toast.LENGTH_LONG).show();
                return;
            }
            mPbCacheDownload.setVisibility(View.VISIBLE);
            mTvCacheStatus.setText("Mengekstrak file ZIP lokal...");
            mBtnInstallLocalZip.setEnabled(false);

            CacheDownloader.extractLocalZip(file, getExternalFilesDir(null), new CacheDownloader.DownloadCallback() {
                @Override public void onProgress(int percent, long current, long total) {}
                @Override public void onExtracting(String entryName) { mTvCacheStatus.setText("Mengekstrak: " + entryName); }
                @Override public void onComplete(File destinationDir) {
                    mPbCacheDownload.setVisibility(View.GONE);
                    mBtnInstallLocalZip.setEnabled(true);
                    Toast.makeText(LauncherActivity.this, "Ekstraksi selesai!", Toast.LENGTH_LONG).show();
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
        if (mEtSettingsNickname != null && mEtSettingsNickname.getText().length() > 0 && mLayoutSettings.getVisibility() == View.VISIBLE) {
            nickname = mEtSettingsNickname.getText().toString().trim();
        }
        if (!SettingsManager.isValidNickname(nickname)) {
            Toast.makeText(this, "Nickname tidak valid (3-20 karakter: huruf, angka, _, [], $, @)", Toast.LENGTH_SHORT).show();
            return;
        }
        mSettings.setNickname(nickname);
        if (mEtNickname != null) mEtNickname.setText(nickname);
        if (mEtSettingsNickname != null) mEtSettingsNickname.setText(nickname);

        int idx = mSpinnerFps.getSelectedItemPosition();
        int fps = idx == 0 ? 30 : (idx == 1 ? 60 : (idx == 3 ? 120 : 90));
        mSettings.setFpsLimit(fps);

        if (mCbFastChat != null) mSettings.setFastChat(mCbFastChat.isChecked());
        if (mCbVoiceChat != null) mSettings.setVoiceChat(mCbVoiceChat.isChecked());
        if (mCbCefOverlay != null) mSettings.setCefOverlayEnabled(mCbCefOverlay.isChecked());

        if (mEtCacheUrl != null) {
            String url = mEtCacheUrl.getText().toString().trim();
            if (!url.isEmpty()) mSettings.setCacheUrl(url);
        }

        mSettings.syncSharedPreferences(this, mSelectedServer, false);
        Toast.makeText(this, "Seluruh setelan ROMP by HARFA disimpan!", Toast.LENGTH_SHORT).show();
    }

    private void launchOfflineGame() {
        new AlertDialog.Builder(this)
                .setTitle("Pilih Mode Single Player")
                .setItems(new CharSequence[]{
                        "🚀 Mesin 3D ROMP (Single Player Offline)\n   Jalankan GTA San Andreas Story Mode 3D",
                        "🎮 ROMP Client CEF (Offline Test)"
                }, (dialog, which) -> {
                    if (which == 0) {
                        if (ensureGameDataReadyOrPrompt()) {
                            mSettings.launchEmbedded3dEngineOffline(this);
                        }
                    } else {
                        if (mSelectedServer != null) {
                            startGameActivity(mSelectedServer, mSettings.getNickname());
                        } else {
                            Intent intent = new Intent(this, GTASA.class);
                            intent.putExtra("player_name", mSettings.getNickname());
                            startActivity(intent);
                        }
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void launchGameWithServer(ServerItem server) {
        if (mEtNickname != null && mEtNickname.getText().length() > 0) {
            String inputName = mEtNickname.getText().toString().trim();
            if (SettingsManager.isValidNickname(inputName)) mSettings.setNickname(inputName);
        }
        String nickname = mSettings.getNickname();
        if (!SettingsManager.isValidNickname(nickname)) {
            Toast.makeText(this, "Silakan atur Nickname yang valid terlebih dahulu!", Toast.LENGTH_LONG).show();
            showSettingsTab();
            return;
        }
        if (mSettings.isVoiceChat() && !hasMicPermission()) {
            mPendingOnlineLaunchServer = server;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERM_RECORD_AUDIO);
            return;
        }

        continueOnlineLaunchAfterMicCheck(server);
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void continueOnlineLaunchAfterMicCheck(ServerItem server) {
        String nickname = mSettings.getNickname();
        ServerBrowserController.promptPasswordIfNeeded(this, mRepo, server, updated -> {
            mSettings.syncToNativeSettings(updated, getExternalFilesDir(null));
            if (!ensureGameDataReadyOrPrompt()) {
                return;
            }
            handleConnectionEngineChoice(updated, nickname);
        });
    }

    /**
     * Prevents launching native libalyn_resa.so (SDL_main SIGABRT) when core GTA:SA files are missing.
     * Shows missing asset details and redirects the user to the BERKAS tab to download or import cache.
     */
    private boolean ensureGameDataReadyOrPrompt() {
        CacheVerifier.CacheStatus cacheStatus = CacheVerifier.checkGameData(this);
        if (cacheStatus.isComplete()) {
            return true;
        }
        StringBuilder details = new StringBuilder();
        details.append("Mesin 3D ROMP membutuhkan berkas data GTA San Andreas yang lengkap agar tidak Force Close saat masuk ke gim.\n\n");
        List<String> missing = cacheStatus.getMissingFiles();
        if (missing != null && !missing.isEmpty()) {
            details.append("Berkas yang belum ditemukan:\n");
            int limit = Math.min(missing.size(), 5);
            for (int i = 0; i < limit; i++) {
                details.append("• ").append(missing.get(i)).append("\n");
            }
            if (missing.size() > limit) {
                details.append("• ...dan ").append(missing.size() - limit).append(" berkas lainnya\n");
            }
            details.append("\n");
        }
        details.append("Buka tab BERKAS lalu pilih \"Unduh Cache Resmi\" atau \"Impor Data ZIP / Folder\".");
        if (Build.VERSION.SDK_INT >= 30) {
            details.append("\n\nCatatan Android 11+ (MIUI): Sistem membatasi akses langsung ke Android/data aplikasi lain. Gunakan Unduh Resmi atau Impor ZIP di tab BERKAS.");
        }
        new AlertDialog.Builder(this)
                .setTitle("Data Game Belum Lengkap")
                .setMessage(details.toString())
                .setPositiveButton("Buka Tab BERKAS", (d, w) -> showFilesTab())
                .setNegativeButton("Tutup", null)
                .show();
        return false;
    }

    private void handleConnectionEngineChoice(ServerItem server, String nickname) {
        String serverName = server.getName() != null ? server.getName() : server.getAddress();
        Toast.makeText(this, "Menghubungkan ke " + serverName + " via ROMP by HARFA...", Toast.LENGTH_SHORT).show();
        mSettings.launchEmbedded3dEngine(this, server);
    }

    private void showConnectionMethodDialog(ServerItem server, String nickname) {
        String serverName = server.getName() != null ? server.getName() : server.getAddress();
        new AlertDialog.Builder(this)
                .setTitle("Pilih Mode Gameplay · " + serverName)
                .setItems(new CharSequence[]{
                        "Mesin 3D ROMP (Gameplay 3D Mandiri)",
                        "ROMP Client CEF (In-Game Overlay)"
                }, (dialog, which) -> {
                    if (which == 0) {
                        mSettings.launchEmbedded3dEngine(this, server);
                    } else {
                        startGameActivity(server, nickname);
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void startGameActivity(ServerItem server, String nickname) {
        Intent intent = new Intent(this, GTASA.class);
        intent.putExtra("server_ip", server.getIp());
        intent.putExtra("server_port", server.getPort());
        intent.putExtra("player_name", nickname);
        intent.putExtra("server_name", server.getName() != null ? server.getName() : server.getAddress());
        startActivity(intent);
    }

    // =========================================================================
    // Deep Link Join Handling, Runtime Permissions, & Storage Pickers
    // =========================================================================

    private void handleIncomingJoinIntent(Intent intent) {
        if (intent == null || !intent.hasExtra(JoinLinkActivity.EXTRA_JOIN_HOST)) return;
        String host = intent.getStringExtra(JoinLinkActivity.EXTRA_JOIN_HOST);
        int port = intent.getIntExtra(JoinLinkActivity.EXTRA_JOIN_PORT, 7777);
        String name = intent.getStringExtra(JoinLinkActivity.EXTRA_JOIN_NAME);
        String password = intent.getStringExtra(JoinLinkActivity.EXTRA_JOIN_PASSWORD);
        intent.removeExtra(JoinLinkActivity.EXTRA_JOIN_HOST);
        if (host == null || host.isEmpty()) return;

        ServerItem linked = new ServerItem(host, port, name != null ? name : host + ":" + port);
        if (password != null && !password.isEmpty()) linked.setPassword(password);

        new AlertDialog.Builder(this)
                .setTitle("Tautan Gabung Server")
                .setMessage("Pilih dan hubungkan ke server ini?\n\n" + linked.getName() + "\n" + linked.getAddress())
                .setPositiveButton("Simpan & Main", (d, w) -> {
                    mRepo.addFavorite(linked);
                    selectServerForPlay(linked);
                    launchGameWithServer(linked);
                })
                .setNeutralButton("Simpan Saja", (d, w) -> {
                    mRepo.addFavorite(linked);
                    selectServerForPlay(linked);
                    showHomeTab();
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_PERM_NOTIFICATIONS);
            }
        }
    }

    private void requestMicrophonePermission() {
        if (hasMicPermission()) {
            Toast.makeText(this, "Izin Mikrofon Voice Chat sudah aktif!", Toast.LENGTH_SHORT).show();
            return;
        }
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_PERM_RECORD_AUDIO);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM_RECORD_AUDIO) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Toast.makeText(this, granted ? "Mikrofon diizinkan untuk Voice Chat!" : "Izin mikrofon ditolak.", Toast.LENGTH_SHORT).show();
            ServerItem pending = mPendingOnlineLaunchServer;
            mPendingOnlineLaunchServer = null;
            if (pending != null) {
                continueOnlineLaunchAfterMicCheck(pending);
            }
        }
    }

    private void openFilePicker(String mimeType, int requestCode) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mimeType);
        startActivityForResult(Intent.createChooser(i, "Pilih Berkas"), requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String name = resolveUriDisplayName(uri);

        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return;
            if (requestCode == PICK_CROSSHAIR_IMAGE) {
                File out = new File(mTheme.getThemeStorageDir(), "crosshair_custom.png");
                copyStream(in, out);
                mSettings.saveCrosshairConfig(4, mSettings.getCrosshairSize(), mSettings.getCrosshairThickness(),
                        mSettings.getCrosshairOpacity(), mSettings.getCrosshairOutline(), mSettings.getCrosshairColor(), out.getAbsolutePath());
                Toast.makeText(this, "Gambar Crosshair kustom diterapkan!", Toast.LENGTH_SHORT).show();
            } else if (requestCode == PICK_CUSTOM_FONT) {
                boolean ok = mFontManager.importCustomFont(in, name);
                Toast.makeText(this, ok ? "Font kustom diaktifkan: " + name : "Harap pilih file .ttf atau .otf", Toast.LENGTH_SHORT).show();
            } else if (requestCode == PICK_CUSTOM_BG) {
                File out = new File(mTheme.getThemeStorageDir(), "hero_custom_bg.png");
                copyStream(in, out);
                mTheme.setCustomBackgroundPath(out.getAbsolutePath());
                mLoadedHeroKey = "";
                applyThemeAndHero();
                Toast.makeText(this, "Wallpaper kustom diterapkan!", Toast.LENGTH_SHORT).show();
            } else if (requestCode == PICK_CUSTOM_LOGO) {
                File out = new File(mTheme.getThemeStorageDir(), "custom_logo.png");
                copyStream(in, out);
                mTheme.setCustomLogoPath(out.getAbsolutePath());
                mLoadedLogoKey = "";
                applyThemeAndHero();
                Toast.makeText(this, "Logo kustom diterapkan!", Toast.LENGTH_SHORT).show();
            } else if (requestCode == PICK_GENERIC_FILE && mPendingImportTargetDir != null) {
                File out = new File(mPendingImportTargetDir, name);
                copyStream(in, out);
                if (mFilesRendered && mFilesController != null) mFilesController.render();
                Toast.makeText(this, "Berkas diimpor: " + name, Toast.LENGTH_SHORT).show();
            } else if (requestCode == PICK_MOD_FILE || requestCode == PICK_MODPACK_ZIP) {
                ModLoaderManager.ImportReport rep = ModLoaderManager.importModFile(this, in, name, mSettings.isModLoaderEnabled());
                if (mFilesRendered && mFilesController != null) mFilesController.render();
                String summary = "Diterapkan: " + rep.appliedCount + " berkas";
                if (!rep.skippedUnsupported.isEmpty()) summary += "\nTidak diterapkan (" + rep.skippedUnsupported.size() + "): " + rep.skippedUnsupported.get(0);
                new AlertDialog.Builder(this).setTitle("Hasil Impor Mod").setMessage(summary).setPositiveButton("OK", null).show();
            } else if (requestCode == PICK_LUA_SCRIPT) {
                boolean ok = AlLoaderManager.importLuaScript(this, in, name);
                if (mFilesRendered && mFilesController != null) mFilesController.render();
                Toast.makeText(this, ok ? "Skrip Lua dipasang: " + name : "Gagal mengimpor skrip", Toast.LENGTH_SHORT).show();
            } else if (requestCode == PICK_TEXDB_ZIP) {
                int cnt = ModLoaderManager.importTexdbZip(this, in);
                Toast.makeText(this, cnt + " berkas tekstur dipasang ke texdb/custom/", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Gagal memproses berkas: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String resolveUriDisplayName(Uri uri) {
        String fallback = "imported_" + System.currentTimeMillis();
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String n = c.getString(idx);
                    if (n != null && !n.isEmpty()) return new File(n).getName();
                }
            }
        } catch (Exception ignored) {}
        String path = uri.getLastPathSegment();
        return path != null && !path.isEmpty() ? new File(path).getName() : fallback;
    }

    private static void copyStream(InputStream in, File dst) throws Exception {
        try (FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
        }
    }
}
