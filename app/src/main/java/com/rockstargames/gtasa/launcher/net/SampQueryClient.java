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

    private static ServerInfo executeQuery(String ip, int port) {
        long startTime = System.currentTimeMillis();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(2000); // 2 seconds timeout

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
                return ServerInfo.offline(ip, port);
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
                return ServerInfo.offline(ip, port);
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

            return new ServerInfo(true, ip, port, hostname, gamemode, players, maxPlayers, (int) pingMs, passworded);
        } catch (Exception e) {
            Log.d(TAG, "Query failed for " + ip + ":" + port + " (" + e.getMessage() + ")");
            return ServerInfo.offline(ip, port);
        }
    }
}

