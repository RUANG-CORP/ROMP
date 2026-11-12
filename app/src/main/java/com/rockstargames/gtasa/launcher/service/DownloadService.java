package com.rockstargames.gtasa.launcher.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import com.rockstargames.gtasa.R;
import com.rockstargames.gtasa.launcher.data.CacheDownloader;
import com.rockstargames.gtasa.launcher.ui.LauncherActivity;

/**
 * Foreground Data-Sync Service keeping CDN / ZIP downloads active when the launcher is backgrounded.
 */
public class DownloadService extends Service {
    public static final String CHANNEL_ID = "romp_downloads";
    public static final int NOTIFICATION_ID = 4011;

    public static final String ACTION_START = "com.rockstargames.gtasa.ACTION_DL_START";
    public static final String ACTION_PROGRESS = "com.rockstargames.gtasa.ACTION_DL_PROGRESS";
    public static final String ACTION_STOP = "com.rockstargames.gtasa.ACTION_DL_STOP";
    public static final String ACTION_CANCEL = "com.rockstargames.gtasa.ACTION_DL_CANCEL";

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_SUBTITLE = "subtitle";
    public static final String EXTRA_PROGRESS = "progress";

    public static void startDownloadNotification(Context context, String title) {
        if (context == null) return;
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_TITLE, title);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception ignored) {}
    }

    public static void updateDownloadNotification(Context context, String title, String subtitle, int progress) {
        if (context == null) return;
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_PROGRESS);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_SUBTITLE, subtitle);
        intent.putExtra(EXTRA_PROGRESS, progress);
        try {
            context.startService(intent);
        } catch (Exception ignored) {}
    }

    public static void stopDownloadNotification(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, DownloadService.class);
        intent.setAction(ACTION_STOP);
        try {
            context.startService(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : ACTION_START;
        if (ACTION_CANCEL.equals(action)) {
            CacheDownloader.cancel();
            stopSelfCleanly();
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action)) {
            stopSelfCleanly();
            return START_NOT_STICKY;
        }

        String title = intent != null && intent.hasExtra(EXTRA_TITLE)
                ? intent.getStringExtra(EXTRA_TITLE)
                : "ROMP by HARFA Downloads";
        String subtitle = intent != null && intent.hasExtra(EXTRA_SUBTITLE)
                ? intent.getStringExtra(EXTRA_SUBTITLE)
                : "Mengunduh berkas gim...";
        int progress = intent != null ? intent.getIntExtra(EXTRA_PROGRESS, -1) : -1;

        Notification notification = buildNotification(title, subtitle, progress);
        if (ACTION_START.equals(action)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } else {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, notification);
        }
        return START_NOT_STICKY;
    }

    private void stopSelfCleanly() {
        stopForeground(true);
        stopSelf();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Unduhan ROMP by HARFA",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Status pengunduhan data gim dan mod");
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String title, String subtitle, int progress) {
        Intent openIntent = new Intent(this, LauncherActivity.class);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent contentPi = PendingIntent.getActivity(this, 0, openIntent, piFlags);

        Intent cancelIntent = new Intent(this, DownloadService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        PendingIntent cancelPi = PendingIntent.getService(this, 1, cancelIntent, piFlags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        builder.setContentTitle(title)
                .setContentText(subtitle)
                .setSmallIcon(R.drawable.ic_launcher_romp_small)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentPi)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Batal", cancelPi);

        if (progress >= 0 && progress <= 100) {
            builder.setProgress(100, progress, false);
        } else {
            builder.setProgress(0, 0, true);
        }

        return builder.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

