package com.rockstargames.gtasa.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ServerRepository {
    private static final String PREF_NAME = "samp_servers_pref";
    private static final String KEY_FAVORITES = "favorite_servers";

    private final SharedPreferences mPrefs;

    public ServerRepository(Context context) {
        mPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public List<ServerItem> getFavorites() {
        List<ServerItem> list = new ArrayList<>();
        String jsonStr = mPrefs.getString(KEY_FAVORITES, null);
        if (jsonStr == null || jsonStr.isEmpty()) {
            // Default seed servers
            list.add(new ServerItem("127.0.0.1", 7777, "Localhost Development Server"));
            list.add(new ServerItem("51.79.162.246", 7777, "Indonesian Roleplay Server"));
            saveFavorites(list);
            return list;
        }

        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                ServerItem item = ServerItem.fromJson(arr.getJSONObject(i));
                if (item != null) list.add(item);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public void addFavorite(ServerItem server) {
        List<ServerItem> list = getFavorites();
        // Avoid duplicate IP:Port
        for (ServerItem existing : list) {
            if (existing.getIp().equalsIgnoreCase(server.getIp()) && existing.getPort() == server.getPort()) {
                return;
            }
        }
        list.add(server);
        saveFavorites(list);
    }

    public void removeFavorite(String serverId) {
        List<ServerItem> list = getFavorites();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(serverId)) {
                list.remove(i);
                break;
            }
        }
        saveFavorites(list);
    }

    public List<ServerItem> getPublicServers() {
        // Curated directory of active servers
        List<ServerItem> list = new ArrayList<>();
        list.add(new ServerItem("127.0.0.1", 7777, "Localhost Test Server"));
        list.add(new ServerItem("51.79.162.246", 7777, "Ruang Roleplay Indonesia"));
        list.add(new ServerItem("178.32.74.83", 7777, "open.mp Community Server"));
        list.add(new ServerItem("51.89.24.18", 7777, "Grand Theft Auto OMP Testbed"));
        return list;
    }

    private void saveFavorites(List<ServerItem> list) {
        JSONArray arr = new JSONArray();
        for (ServerItem item : list) {
            arr.put(item.toJson());
        }
        mPrefs.edit().putString(KEY_FAVORITES, arr.toString()).apply();
    }
}

