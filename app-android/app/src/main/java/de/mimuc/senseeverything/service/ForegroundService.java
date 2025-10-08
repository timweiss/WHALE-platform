package de.mimuc.senseeverything.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import de.mimuc.senseeverything.R;
import de.mimuc.senseeverything.activity.MainActivity;
import de.mimuc.senseeverything.logging.WHALELog;

public abstract class ForegroundService extends Service {

    String TAG = getClass().getName();
    private final int NOTIF_ID = 101;
    private final String NOTIFICATION_CHANNEL_ID = "whale_foreground";
    private final int NOTIFICATION_CHANNEL_STRING_ID_NAME = R.string.notif_channel_name;

    private PowerManager.WakeLock m_wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(
                getApplicationContext()).setSmallIcon(R.drawable.notification_whale)
                .setTicker(getText(R.string.notif_ticker))
                .setContentTitle(getText(R.string.notif_title))
                .setContentText(getText(R.string.notif_text))
                .setContentIntent(pendingIntent).setAutoCancel(true)
                .setOngoing(true).setContentInfo("");

        startForeground(); //startForeground(42, notification.build());


        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        m_wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        boolean m_InProgress = false;
    }

    private void startForeground() {
        String channelId = getOrCreateNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notif_title))
                .setSmallIcon(R.drawable.notification_whale)
                .setContentIntent(pendingIntent)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    protected void replaceNotification(String title, String text, int icon) {
        String channelId = getOrCreateNotificationChannel();

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(icon)
                .setContentIntent(pendingIntent)
                .build();

        NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(NOTIF_ID, notification);
    }

    private String getOrCreateNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return createNotificationChannel(NOTIFICATION_CHANNEL_ID, getString(NOTIFICATION_CHANNEL_STRING_ID_NAME));
        } else {
            return "";
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(String channelId, String channelName) {
        NotificationChannel chan = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        return channelId;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        WHALELog.INSTANCE.d(TAG, newConfig.toString());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        WHALELog.INSTANCE.d(TAG, "Service Stopped");

        if (m_wakeLock.isHeld()) {
            m_wakeLock.release();
        }

        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
