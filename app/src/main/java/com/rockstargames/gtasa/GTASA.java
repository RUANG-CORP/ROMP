package com.rockstargames.gtasa;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.rockstargames.gtasa.core.CefManager;
import com.rockstargames.gtasa.core.GameViewportView;
import com.rockstargames.gtasa.launcher.data.ServerItem;
import com.rockstargames.gtasa.launcher.data.SettingsManager;
import com.rockstargames.gtasa.launcher.net.SampQueryClient;
import com.rockstargames.gtasa.launcher.net.ServerInfo;

public class GTASA extends Activity {

    private FrameLayout mRootLayout;
    private GameViewportView mGameViewport;
    private TextView mTvServerPill;
    private TextView mTvSampConsole;
    private ScrollView mScrollConsole;
    private Button mBtnQuit;
    private Button mBtnSwitchResa;

    // In-game controls
    private Button mBtnSprint;
    private Button mBtnJump;
    private Button mBtnVehicle;
    private Button mBtnScoreboard;
    private Button mBtnChat;

    // Chat
    private LinearLayout mLayoutChatInput;
    private EditText mEtChatInput;
    private Button mBtnSendChat;

    // Scoreboard
    private LinearLayout mLayoutScoreboard;
    private TextView mTvScoreboardTitle;
    private TextView mTvScoreboardContent;
    private Button mBtnCloseScoreboard;

    // Server params
    private String mServerIp = "185.239.237.91";
    private int mServerPort = 7777;
    private String mPlayerName = "Player_Valleta";
    private String mServerName = "Life of Player";

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mPingRunnable;
    private boolean mIsConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_overlay);

        // Read extras
        Intent intent = getIntent();
        if (intent != null) {
            if (intent.hasExtra("server_ip")) {
                mServerIp = intent.getStringExtra("server_ip");
            }
            if (intent.hasExtra("server_port")) {
                mServerPort = intent.getIntExtra("server_port", 7777);
            }
            if (intent.hasExtra("player_name")) {
                mPlayerName = intent.getStringExtra("player_name");
            }
            if (intent.hasExtra("server_name")) {
                mServerName = intent.getStringExtra("server_name");
            }
        }

        mRootLayout = findViewById(R.id.game_root_layout);
        mGameViewport = findViewById(R.id.game_viewport);
        mTvServerPill = findViewById(R.id.tv_game_server_pill);
        mTvSampConsole = findViewById(R.id.tv_samp_console);
        mScrollConsole = findViewById(R.id.scroll_samp_console);
        mBtnQuit = findViewById(R.id.btn_game_quit);
        mBtnSwitchResa = findViewById(R.id.btn_switch_to_resa);

        mBtnSprint = findViewById(R.id.btn_action_sprint);
        mBtnJump = findViewById(R.id.btn_action_jump);
        mBtnVehicle = findViewById(R.id.btn_action_vehicle);
        mBtnScoreboard = findViewById(R.id.btn_action_scoreboard);
        mBtnChat = findViewById(R.id.btn_action_chat);

        mLayoutChatInput = findViewById(R.id.layout_chat_input);
        mEtChatInput = findViewById(R.id.et_chat_input);
        mBtnSendChat = findViewById(R.id.btn_send_chat);

        mLayoutScoreboard = findViewById(R.id.layout_scoreboard);
        mTvScoreboardTitle = findViewById(R.id.tv_scoreboard_title);
        mTvScoreboardContent = findViewById(R.id.tv_scoreboard_content);
        mBtnCloseScoreboard = findViewById(R.id.btn_close_scoreboard);

        setupListeners();

        // Check if ReSA is installed for one-click handoff button
        if (SettingsManager.isResaInstalled(this)) {
            mBtnSwitchResa.setVisibility(View.VISIBLE);
            mBtnSwitchResa.setOnClickListener(v -> {
                ServerItem server = new ServerItem(mServerIp, mServerPort, mServerName);
                SettingsManager.launchResaGame(this, server);
                finish();
            });
        }

        // Initialize CEF Overlay
        CefManager.getInstance().init(this, mRootLayout);
        CefManager.getInstance().loadUrl("package://cef/index.html", false);

        // Start live server connect routine
        connectToServer();
    }

    private void setupListeners() {
        mBtnQuit.setOnClickListener(v -> finish());

        mGameViewport.setSpeedListener(kmh -> {
            // Forward speed changes to CEF Speedometer HUD
            mHandler.post(() -> {
                CefManager.getInstance().emitClient("speed:update", "{\"speed\": " + kmh + "}");
            });
        });

        mBtnSprint.setOnClickListener(v -> appendConsole("[GAME]: CJ Sprinting..."));
        mBtnJump.setOnClickListener(v -> appendConsole("[GAME]: CJ Jumped."));

        mBtnVehicle.setOnClickListener(v -> {
            boolean inCar = !mGameViewport.isInVehicle();
            mGameViewport.setInVehicle(inCar);
            if (inCar) {
                mBtnVehicle.setText("🚶");
                appendConsole("[GAME]: Entering Vehicle (Infernus)...");
                CefManager.getInstance().emitClient("vehicle:enter", "{\"model\": \"Infernus\", \"speed\": 0}");
            } else {
                mBtnVehicle.setText("🚗");
                appendConsole("[GAME]: Exited Vehicle.");
                CefManager.getInstance().emitClient("vehicle:exit", "{}");
            }
        });

        mBtnChat.setOnClickListener(v -> {
            if (mLayoutChatInput.getVisibility() == View.VISIBLE) {
                mLayoutChatInput.setVisibility(View.GONE);
            } else {
                mLayoutChatInput.setVisibility(View.VISIBLE);
                mEtChatInput.requestFocus();
            }
        });

        mBtnSendChat.setOnClickListener(v -> sendChatMessage());
        mEtChatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendChatMessage();
                return true;
            }
            return false;
        });

        mBtnScoreboard.setOnClickListener(v -> toggleScoreboard());
        mBtnCloseScoreboard.setOnClickListener(v -> mLayoutScoreboard.setVisibility(View.GONE));
    }

    private void sendChatMessage() {
        String msg = mEtChatInput.getText().toString().trim();
        if (!msg.isEmpty()) {
            appendConsole(mPlayerName + ": " + msg);
            mEtChatInput.setText("");
            mLayoutChatInput.setVisibility(View.GONE);
        }
    }

    private void toggleScoreboard() {
        if (mLayoutScoreboard.getVisibility() == View.VISIBLE) {
            mLayoutScoreboard.setVisibility(View.GONE);
        } else {
            mLayoutScoreboard.setVisibility(View.VISIBLE);
            fetchScoreboard();
        }
    }

    private void fetchScoreboard() {
        mTvScoreboardContent.setText("Mengambil data pemain via UDP...");
        SampQueryClient.queryServerDetails(mServerIp, mServerPort, info -> {
            if (info != null && info.isOnline()) {
                mTvScoreboardTitle.setText("Papan Skor (" + info.getPlayers() + " / " + info.getMaxPlayers() + ")");
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("%-4s %-20s %-8s %-6s\n", "ID", "NAMA PEMAIN", "SKOR", "PING"));
                sb.append("--------------------------------------------------\n");
                // Insert local player
                int idx = 1;
                sb.append(String.format("%-4s %-20s %-8s %-6s\n", "0", mPlayerName, "1", info.getPingMs() + "ms"));
                for (ServerInfo.PlayerEntry p : info.getPlayerList()) {
                    sb.append(String.format("%-4d %-20s %-8s %-6s\n", idx++, p.getName(), p.getScore(), p.getPing() + "ms"));
                }
                mTvScoreboardContent.setText(sb.toString());
            } else {
                mTvScoreboardContent.setText("Server tidak dapat dijangkau.");
            }
        });
    }

    private void connectToServer() {
        appendConsole("SA-MP 0.3.7-R3 Mobile Client Started");
        appendConsole("Connecting to " + mServerIp + ":" + mServerPort + "...");

        mPingRunnable = new Runnable() {
            @Override
            public void run() {
                SampQueryClient.queryServer(mServerIp, mServerPort, info -> {
                    if (info != null && info.isOnline()) {
                        mTvServerPill.setText("🟢 " + info.getHostname() + " | " + info.getPlayers() + "/" + info.getMaxPlayers() + " | " + info.getPingMs() + "ms | 90 FPS");
                        if (!mIsConnected) {
                            mIsConnected = true;
                            appendConsole("The server is responding. Joining the game...");
                            appendConsole("Connected to " + info.getHostname());
                            appendConsole("Welcome to " + info.getHostname() + ", " + mPlayerName + "!");

                            // Trigger CEF modal dialog for password/login if needed
                            if (info.isPassworded()) {
                                CefManager.getInstance().emitClient("dialog:show", "{\"title\":\"Roleplay Authentication\",\"message\":\"Enter server password:\"}");
                            }
                        }
                    } else {
                        mTvServerPill.setText("🔴 " + mServerIp + ":" + mServerPort + " (Menghubungi ulang...)");
                    }
                });

                mHandler.postDelayed(this, 3000);
            }
        };

        mHandler.post(mPingRunnable);
    }

    private void appendConsole(String message) {
        mTvSampConsole.append(message + "\n");
        mScrollConsole.post(() -> mScrollConsole.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mPingRunnable != null) {
            mHandler.removeCallbacks(mPingRunnable);
        }
        CefManager.getInstance().onDestroy();
    }
}
