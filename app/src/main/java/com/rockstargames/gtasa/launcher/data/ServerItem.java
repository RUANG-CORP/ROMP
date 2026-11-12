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
    private String password = "";
    private String gamemode = "";
    private String language = "";
    private int players = -1;
    private int maxPlayers = -1;
    private boolean passworded = false;
    private String source = "CUSTOM";

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

    public String getPassword() { return password != null ? password : ""; }
    public void setPassword(String password) { this.password = password != null ? password : ""; }

    public String getGamemode() { return gamemode != null ? gamemode : ""; }
    public void setGamemode(String gamemode) { this.gamemode = gamemode != null ? gamemode : ""; }

    public String getLanguage() { return language != null ? language : ""; }
    public void setLanguage(String language) { this.language = language != null ? language : ""; }

    public int getPlayers() { return players; }
    public void setPlayers(int players) { this.players = players; }

    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }

    public boolean isPassworded() { return passworded; }
    public void setPassworded(boolean passworded) { this.passworded = passworded; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source != null ? source : "CUSTOM"; }

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
            if (!getPassword().isEmpty()) obj.put("password", password);
            if (!getGamemode().isEmpty()) obj.put("gamemode", gamemode);
            if (!getLanguage().isEmpty()) obj.put("language", language);
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
            ServerItem item = new ServerItem(id, ip, port, name, fav);
            item.setPassword(obj.optString("password", ""));
            item.setGamemode(obj.optString("gamemode", ""));
            item.setLanguage(obj.optString("language", ""));
            return item;
        } catch (Exception e) {
            return null;
        }
    }
}
