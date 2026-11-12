package com.rockstargames.gtasa.launcher.net;

import android.os.Handler;
import android.os.Looper;

import com.rockstargames.gtasa.launcher.data.ServerItem;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fetches live public Hosted SA-MP / open.mp server directory from https://api.open.mp/servers
 * with in-memory TTL caching and zero authentication/ad-tracking dependencies.
 */
public class MasterlistClient {
    public static final String MASTERLIST_URL = "https://api.open.mp/servers";
    private static final long CACHE_TTL_MS = 60_000L;
    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor();
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    private static List<ServerItem> sCachedServers = Collections.emptyList();
    private static long sLastFetchMs = 0L;

    public interface Callback {
        void onResult(List<ServerItem> servers, boolean fromCache, String error);
    }

    public static void fetchHostedServers(boolean forceRefresh, final Callback callback) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && !sCachedServers.isEmpty() && (now - sLastFetchMs) < CACHE_TTL_MS) {
            sMainHandler.post(() -> {
                if (callback != null) callback.onResult(new ArrayList<>(sCachedServers), true, null);
            });
            return;
        }

        sExecutor.execute(() -> {
            try {
                URL url = new URL(MASTERLIST_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "ROMP-by-HARFA/1.2");
                conn.setRequestProperty("Accept", "application/json");

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    throw new Exception("HTTP " + code);
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                } finally {
                    conn.disconnect();
                }

                List<ServerItem> parsed = parseMasterlistJson(sb.toString());
                sCachedServers = Collections.unmodifiableList(parsed);
                sLastFetchMs = System.currentTimeMillis();

                sMainHandler.post(() -> {
                    if (callback != null) callback.onResult(new ArrayList<>(parsed), false, null);
                });
            } catch (Exception e) {
                String errMsg = e.getMessage() != null ? e.getMessage() : "Koneksi gagal";
                sMainHandler.post(() -> {
                    if (callback != null) callback.onResult(new ArrayList<>(sCachedServers), true, errMsg);
                });
            }
        });
    }

    public static List<ServerItem> parseMasterlistJson(String rawJson) throws Exception {
        List<ServerItem> out = new ArrayList<>();
        if (rawJson == null || rawJson.trim().isEmpty()) return out;

        JSONArray arr = new JSONArray(rawJson);
        int limit = Math.min(arr.length(), 400);
        for (int i = 0; i < limit; i++) {
            JSONObject obj = arr.optJSONObject(i);
            if (obj == null) continue;
            JSONObject core = obj.has("core") ? obj.optJSONObject("core") : obj;
            if (core == null) continue;

            String ipPort = core.optString("ip", "");
            if (ipPort.isEmpty() || !ipPort.contains(":")) continue;
            String[] parts = ipPort.split(":", 2);
            String ip = parts[0].trim();
            int port;
            try {
                port = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            if (ip.isEmpty() || port <= 0 || port > 65535) continue;

            String hn = core.optString("hn", ipPort);
            String gm = core.optString("gm", "SA-MP");
            String la = core.optString("la", "");
            int pc = core.optInt("pc", 0);
            int pm = core.optInt("pm", 0);
            boolean pa = core.optBoolean("pa", false);

            ServerItem item = new ServerItem("hosted_" + ip + "_" + port, ip, port, hn, false);
            item.setGamemode(gm);
            item.setLanguage(la);
            item.setPlayers(pc);
            item.setMaxPlayers(pm);
            item.setPassworded(pa);
            item.setSource("HOSTED");
            out.add(item);
        }

        Collections.sort(out, (a, b) -> Integer.compare(b.getPlayers(), a.getPlayers()));
        return out;
    }
}

