package com.rockstargames.gtasa.launcher.net;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous SA-MP UDP Query Protocol Client.
 * Connects via non-blocking worker threads and reports results back on the Android Main UI Thread.
 */
public class SampQueryClient {
    private static final String TAG = "SampQueryClient";
    private static final ExecutorService sExecutor = Executors.newFixedThreadPool(4);
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    private static class CachedInfo {
        final ServerInfo info;
        final long timestamp;
        CachedInfo(ServerInfo info, long timestamp) {
            this.info = info;
            this.timestamp = timestamp;
        }
    }
    private static final Map<String, CachedInfo> sCache = new ConcurrentHashMap<>();

    public interface QueryCallback {
        void onResult(ServerInfo info);
    }

    /**
     * Queries a SA-MP or open.mp server using UDP opcode 'i' (Information).
     */
    public static void queryServer(final String ip, final int port, final QueryCallback callback) {
        sExecutor.execute(() -> {
            ServerInfo result = executeQuery(ip, port);
            sMainHandler.post(() -> {
                if (callback != null) {
                    callback.onResult(result);
                }
            });
        });
    }

    /**
     * Queries full server details: Information ('i'), Rules ('r'), and Players ('d'/'c').
     */
    public static void queryServerDetails(final String ip, final int port, final QueryCallback callback) {
        sExecutor.execute(() -> {
            ServerInfo result = executeFullDetailsQuery(ip, port);
            sMainHandler.post(() -> {
                if (callback != null) {
                    callback.onResult(result);
                }
            });
        });
    }

    private static byte[] buildPacket(byte[] ipOctets, int port, char opcode) {
        ByteBuffer buffer = ByteBuffer.allocate(11);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("SAMP".getBytes(StandardCharsets.US_ASCII));
        buffer.put(ipOctets);
        buffer.putShort((short) port);
        buffer.put((byte) opcode);
        return buffer.array();
    }

    private static ServerInfo executeFullDetailsQuery(String ip, int port) {
        ServerInfo info = executeQuery(ip, port);
        if (!info.isOnline()) {
            return info;
        }

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(1500);
            InetAddress address = InetAddress.getByName(ip);
            byte[] ipOctets = address.getAddress();

            // 1. Query Rules (Opcode 'r')
            try {
                byte[] reqRules = buildPacket(ipOctets, port, 'r');
                socket.send(new DatagramPacket(reqRules, reqRules.length, address, port));

                byte[] recvRules = new byte[4096];
                DatagramPacket packRules = new DatagramPacket(recvRules, recvRules.length);
                socket.receive(packRules);

                if (packRules.getLength() >= 13) {
                    ByteBuffer rBuf = ByteBuffer.wrap(packRules.getData(), 0, packRules.getLength());
                    rBuf.order(ByteOrder.LITTLE_ENDIAN);
                    rBuf.position(11); // Skip SAMP header

                    int ruleCount = rBuf.getShort() & 0xFFFF;
                    java.util.Map<String, String> rulesMap = new java.util.HashMap<>();
                    for (int i = 0; i < ruleCount && rBuf.remaining() > 2; i++) {
                        int nLen = rBuf.get() & 0xFF;
                        if (rBuf.remaining() < nLen) break;
                        byte[] nBytes = new byte[nLen];
                        rBuf.get(nBytes);
                        String rName = new String(nBytes, StandardCharsets.ISO_8859_1);

                        if (rBuf.remaining() < 1) break;
                        int vLen = rBuf.get() & 0xFF;
                        if (rBuf.remaining() < vLen) break;
                        byte[] vBytes = new byte[vLen];
                        rBuf.get(vBytes);
                        String rVal = new String(vBytes, StandardCharsets.ISO_8859_1);

                        rulesMap.put(rName, rVal);
                    }
                    info.setRules(rulesMap);
                }
            } catch (Exception e) {
                Log.d(TAG, "Rules query error: " + e.getMessage());
            }

            // 2. Query Detailed Players (Opcode 'd')
            try {
                byte[] reqPlayers = buildPacket(ipOctets, port, 'd');
                socket.send(new DatagramPacket(reqPlayers, reqPlayers.length, address, port));

                byte[] recvPlayers = new byte[8192];
                DatagramPacket packPlayers = new DatagramPacket(recvPlayers, recvPlayers.length);
                socket.receive(packPlayers);

                if (packPlayers.getLength() >= 13) {
                    ByteBuffer pBuf = ByteBuffer.wrap(packPlayers.getData(), 0, packPlayers.getLength());
                    pBuf.order(ByteOrder.LITTLE_ENDIAN);
                    pBuf.position(11);

                    int pCount = pBuf.getShort() & 0xFFFF;
                    java.util.List<ServerInfo.PlayerEntry> playersList = new java.util.ArrayList<>();
                    for (int i = 0; i < pCount && pBuf.remaining() > 2; i++) {
                        pBuf.get(); // skip id
                        int nameLen = pBuf.get() & 0xFF;
                        if (pBuf.remaining() < nameLen + 8) break;
                        byte[] nameBytes = new byte[nameLen];
                        pBuf.get(nameBytes);
                        String pName = new String(nameBytes, StandardCharsets.ISO_8859_1);
                        int pScore = pBuf.getInt();
                        int pPing = pBuf.getInt();
                        playersList.add(new ServerInfo.PlayerEntry(pName, pScore, pPing));
                    }
                    info.setPlayerList(playersList);
                }
            } catch (Exception e) {
                Log.d(TAG, "Detailed players query error: " + e.getMessage());
            }

        } catch (Exception e) {
            Log.d(TAG, "Full details query socket error: " + e.getMessage());
        }

        return info;
    }

    private static ServerInfo executeQuery(String ip, int port) {
        String key = ip + ":" + port;
        CachedInfo cached = sCache.get(key);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp < 3000) && cached.info.isOnline()) {
            return cached.info;
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            long startTime = System.currentTimeMillis();
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(1500); // 1.5 seconds

                InetAddress address = InetAddress.getByName(ip);
                byte[] ipOctets = address.getAddress();

                // Build SA-MP query packet: 'SAMP' (4) + IP (4) + Port (2, little-endian) + Opcode 'i' (1)
                ByteBuffer buffer = ByteBuffer.allocate(11);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.put("SAMP".getBytes(StandardCharsets.US_ASCII));
                buffer.put(ipOctets);
                buffer.putShort((short) port);
                buffer.put((byte) 'i');

                byte[] requestBytes = buffer.array();
                DatagramPacket requestPacket = new DatagramPacket(requestBytes, requestBytes.length, address, port);
                socket.send(requestPacket);

                byte[] receiveBuffer = new byte[2048];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                socket.receive(receivePacket);

                long pingMs = System.currentTimeMillis() - startTime;

                byte[] data = receivePacket.getData();
                int length = receivePacket.getLength();

                if (length < 11) {
                    continue;
                }

                ByteBuffer inBuf = ByteBuffer.wrap(data, 0, length);
                inBuf.order(ByteOrder.LITTLE_ENDIAN);

                // Skip header (11 bytes)
                inBuf.position(11);

                boolean passworded = inBuf.get() != 0;
                int players = inBuf.getShort() & 0xFFFF;
                int maxPlayers = inBuf.getShort() & 0xFFFF;

                int hostnameLen = inBuf.getInt();
                if (hostnameLen < 0 || hostnameLen > 256 || inBuf.remaining() < hostnameLen) {
                    continue;
                }
                byte[] hostnameBytes = new byte[hostnameLen];
                inBuf.get(hostnameBytes);
                String hostname = new String(hostnameBytes, StandardCharsets.ISO_8859_1);

                int gamemodeLen = inBuf.getInt();
                String gamemode = "Roleplay";
                if (gamemodeLen >= 0 && gamemodeLen <= 128 && inBuf.remaining() >= gamemodeLen) {
                    byte[] gamemodeBytes = new byte[gamemodeLen];
                    inBuf.get(gamemodeBytes);
                    gamemode = new String(gamemodeBytes, StandardCharsets.ISO_8859_1);
                }

                ServerInfo res = new ServerInfo(true, ip, port, hostname, gamemode, players, maxPlayers, (int) pingMs, passworded);
                sCache.put(key, new CachedInfo(res, System.currentTimeMillis()));
                return res;
            } catch (Exception e) {
                if (attempt == 0) {
                    try { Thread.sleep(250); } catch (InterruptedException ignored) {}
                }
            }
        }
        return ServerInfo.offline(ip, port);
    }
}

