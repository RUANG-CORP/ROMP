package com.rockstargames.gtasa.launcher.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Model representing SA-MP/open.mp server state returned from UDP query.
 */
public class ServerInfo {

    public static class PlayerEntry {
        private final String name;
        private final int score;
        private final int ping;

        public PlayerEntry(String name, int score, int ping) {
            this.name = name;
            this.score = score;
            this.ping = ping;
        }

        public String getName() { return name; }
        public int getScore() { return score; }
        public int getPing() { return ping; }
    }

    private final boolean online;
    private final String ip;
    private final int port;
    private final String hostname;
    private final String gamemode;
    private final int players;
    private final int maxPlayers;
    private final int pingMs;
    private final boolean passworded;

    private Map<String, String> rules = new HashMap<>();
    private List<PlayerEntry> playerList = new ArrayList<>();

    public ServerInfo(boolean online, String ip, int port, String hostname, String gamemode, int players, int maxPlayers, int pingMs, boolean passworded) {
        this.online = online;
        this.ip = ip;
        this.port = port;
        this.hostname = hostname != null ? hostname : "Offline Server";
        this.gamemode = gamemode != null ? gamemode : "Unknown";
        this.players = players;
        this.maxPlayers = maxPlayers;
        this.pingMs = pingMs;
        this.passworded = passworded;
    }

    public static ServerInfo offline(String ip, int port) {
        return new ServerInfo(false, ip, port, "Offline", "-", 0, 0, 999, false);
    }

    public boolean isOnline() { return online; }
    public String getIp() { return ip; }
    public int getPort() { return port; }
    public String getHostname() { return hostname; }
    public String getGamemode() { return gamemode; }
    public int getPlayers() { return players; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getPingMs() { return pingMs; }
    public boolean isPassworded() { return passworded; }

    public String getAddress() {
        return ip + ":" + port;
    }

    public Map<String, String> getRules() {
        return Collections.unmodifiableMap(rules);
    }

    public void setRules(Map<String, String> rules) {
        this.rules = rules != null ? new HashMap<>(rules) : new HashMap<>();
    }

    public List<PlayerEntry> getPlayerList() {
        return Collections.unmodifiableList(playerList);
    }

    public void setPlayerList(List<PlayerEntry> players) {
        this.playerList = players != null ? new ArrayList<>(players) : new ArrayList<>();
    }
}

