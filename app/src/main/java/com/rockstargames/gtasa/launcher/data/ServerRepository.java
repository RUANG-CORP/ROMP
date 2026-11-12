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
    private static final String KEY_PASSWORDS = "server_passwords_map";

    private final SharedPreferences mPrefs;

    public ServerRepository(Context context) {
        mPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public List<ServerItem> getFavorites() {
        List<ServerItem> list = new ArrayList<>();
        String jsonStr = mPrefs.getString(KEY_FAVORITES, null);
        if (jsonStr == null || jsonStr.isEmpty()) {
            list.add(new ServerItem("18.142.244.146", 7777, "RUANG ROLEPLAY (GTA SA)"));
            list.add(new ServerItem("127.0.0.1", 7777, "Localhost Development Server"));
            list.add(new ServerItem("51.79.162.246", 7777, "Indonesian Roleplay Server"));
            saveFavorites(list);
            return list;
        }

        try {
            JSONArray arr = new JSONArray(jsonStr);
            for (int i = 0; i < arr.length(); i++) {
                ServerItem item = ServerItem.fromJson(arr.getJSONObject(i));
                if (item != null) {
                    if (item.getPassword().isEmpty()) {
                        item.setPassword(getSavedPassword(item.getIp(), item.getPort()));
                    }
                    list.add(item);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Ensure 18.142.244.146:7777 is present and prioritized at index 0
        boolean hasTarget = false;
        for (int i = 0; i < list.size(); i++) {
            if ("18.142.244.146".equals(list.get(i).getIp()) && list.get(i).getPort() == 7777) {
                if (i != 0) {
                    ServerItem item = list.remove(i);
                    list.add(0, item);
                    saveFavorites(list);
                }
                hasTarget = true;
                break;
            }
        }
        if (!hasTarget) {
            list.add(0, new ServerItem("18.142.244.146", 7777, "RUANG ROLEPLAY (GTA SA)"));
            saveFavorites(list);
        }

        return list;
    }

    public void addFavorite(ServerItem server) {
        List<ServerItem> list = getFavorites();
        for (ServerItem existing : list) {
            if (existing.getIp().equalsIgnoreCase(server.getIp()) && existing.getPort() == server.getPort()) {
                if (!server.getPassword().isEmpty()) {
                    existing.setPassword(server.getPassword());
                    setSavedPassword(server.getIp(), server.getPort(), server.getPassword());
                }
                if (server.getName() != null && !server.getName().isEmpty()) {
                    existing.setName(server.getName());
                }
                saveFavorites(list);
                return;
            }
        }
        server.setFavorite(true);
        if (!server.getPassword().isEmpty()) {
            setSavedPassword(server.getIp(), server.getPort(), server.getPassword());
        }
        list.add(server);
        saveFavorites(list);
    }

    public void updateFavorite(String id, String newName, String newIp, int newPort, String newPassword) {
        List<ServerItem> list = getFavorites();
        for (int i = 0; i < list.size(); i++) {
            ServerItem s = list.get(i);
            if (s.getId().equals(id)) {
                ServerItem updated = new ServerItem(id, newIp, newPort, newName, true);
                updated.setPassword(newPassword);
                list.set(i, updated);
                setSavedPassword(newIp, newPort, newPassword);
                saveFavorites(list);
                return;
            }
        }
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

    public boolean isFavorite(String ip, int port) {
        for (ServerItem item : getFavorites()) {
            if (item.getIp().equalsIgnoreCase(ip) && item.getPort() == port) return true;
        }
        return false;
    }

    public void setSavedPassword(String ip, int port, String password) {
        if (ip == null) return;
        try {
            JSONObject obj = new JSONObject(mPrefs.getString(KEY_PASSWORDS, "{}"));
            String key = ip + ":" + port;
            if (password == null || password.isEmpty()) obj.remove(key);
            else obj.put(key, password);
            mPrefs.edit().putString(KEY_PASSWORDS, obj.toString()).apply();
        } catch (Exception ignored) {}
    }

    public String getSavedPassword(String ip, int port) {
        if (ip == null) return "";
        try {
            JSONObject obj = new JSONObject(mPrefs.getString(KEY_PASSWORDS, "{}"));
            return obj.optString(ip + ":" + port, "");
        } catch (Exception e) {
            return "";
        }
    }

    public List<ServerItem> getPublicServers() {
        return getOfficialServers();
    }

    public List<ServerItem> getOfficialServers() {
        List<ServerItem> list = new ArrayList<>();
        ServerItem s1 = new ServerItem("18.142.244.146", 7777, "RUANG ROLEPLAY (GTA SA)");
        s1.setSource("OFFICIAL");
        s1.setLanguage("Indonesia");
        list.add(s1);

        ServerItem s2 = new ServerItem("127.0.0.1", 7777, "Localhost Test Server");
        s2.setSource("OFFICIAL");
        s2.setLanguage("Local");
        list.add(s2);

        ServerItem s3 = new ServerItem("51.79.162.246", 7777, "Ruang Roleplay Indonesia");
        s3.setSource("OFFICIAL");
        s3.setLanguage("Indonesia");
        list.add(s3);

        ServerItem s4 = new ServerItem("178.32.74.83", 7777, "open.mp Community Server");
        s4.setSource("OFFICIAL");
        s4.setLanguage("English");
        list.add(s4);

        ServerItem s5 = new ServerItem("51.89.24.18", 7777, "Grand Theft Auto OMP Testbed");
        s5.setSource("OFFICIAL");
        s5.setLanguage("English");
        list.add(s5);

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
