package com.rockstargames.gtasa.launcher.data;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class ServerItem {
    private final String id;
    private final String ip;
    private final int port;
    private String name;
    private boolean isFavorite;

    public ServerItem(String ip, int port, String name) {
        this(UUID.randomUUID().toString(), ip, port, name, true);
    }

    public ServerItem(String id, String ip, int port, String name, boolean isFavorite) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.ip = ip;
        this.port = port;
        this.name = name != null && !name.isEmpty() ? name : ip + ":" + port;
        this.isFavorite = isFavorite;
    }

    public String getId() { return id; }
    public String getIp() { return ip; }
    public int getPort() { return port; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }

    public String getAddress() {
        return ip + ":" + port;
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", id);
            obj.put("ip", ip);
            obj.put("port", port);
            obj.put("name", name);
            obj.put("isFavorite", isFavorite);
        } catch (JSONException ignored) {}
        return obj;
    }

    public static ServerItem fromJson(JSONObject obj) {
        try {
            String id = obj.optString("id", UUID.randomUUID().toString());
            String ip = obj.getString("ip");
            int port = obj.getInt("port");
            String name = obj.optString("name", ip + ":" + port);
            boolean fav = obj.optBoolean("isFavorite", true);
            return new ServerItem(id, ip, port, name, fav);
        } catch (Exception e) {
            return null;
        }
    }
}

