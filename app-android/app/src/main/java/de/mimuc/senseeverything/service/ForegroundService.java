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

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

import de.mimuc.senseeverything.R;
import de.mimuc.senseeverything.activity.MainActivity;
import de.mimuc.senseeverything.logging.WHALELog;

public abstract class ForegroundService extends Service {

    String TAG = getClass().getName();
    private final int NOTIF_ID = 101;
    private final int RESUME_MICROPHONE_NOTIF_ID = 102;
    private final String NOTIFICATION_CHANNEL_ID = "whale_foreground";
    private final int NOTIFICATION_CHANNEL_STRING_ID_NAME = R.string.notif_channel_name;

    /** Sent by the "tap to resume" notification and by app screens to add the microphone type. */
    public static final String ACTION_ENABLE_MICROPHONE = "de.mimuc.senseeverything.action.ENABLE_MICROPHONE";

    /**
     * Whether the foreground service currently holds the microphone type. Android only grants it
     * while the app is in use (or after a notification tap), so starts from boot, alarms or a
     * sticky restart run without it. Only meaningful in the service's own process.
     */
    private static volatile boolean microphoneActive = false;

    public static boolean isMicrophoneActive() {
        return microphoneActive;
    }

    private boolean isForeground = false;

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


        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        m_wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        boolean m_InProgress = false;
    }

    private Notification buildForegroundNotification() {
        String channelId = getOrCreateNotificationChannel();
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notif_title))
                .setSmallIcon(R.drawable.notification_whale)
                .setContentIntent(pendingIntent)
                .build();
    }

    /**
     * Enters the foreground with the microphone type if Android allows it right now, otherwise
     * with specialUse only so the other sensors keep running, and asks the participant to tap a
     * notification to get the microphone back.
     */
    private void startForegroundWithBestType(String origin) {
        Notification notification = buildForegroundNotification();
        // specialUse only exists from API 34, which is also when a denied microphone start throws.
        // Below that, Android starts the service anyway and only silences the microphone.
        int fallbackType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                : 0;
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | fallbackType);
            isForeground = true;
            microphoneActive = true;
            WHALELog.INSTANCE.i(TAG, "foreground with microphone (origin: " + origin + ")");
            cancelResumeMicrophoneNotification();
            return;
        } catch (SecurityException | IllegalStateException e) {
            // ForegroundServiceStartNotAllowedException is an IllegalStateException
            WHALELog.INSTANCE.w(TAG, "microphone not allowed (origin: " + origin + "): " + e.getMessage());
        }

        if (isForeground) {
            // already in the foreground with specialUse, nothing more to try
            return;
        }

        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification, fallbackType);
            isForeground = true;
            microphoneActive = false;
            WHALELog.INSTANCE.i(TAG, "foreground without microphone (origin: " + origin + ")");
            showResumeMicrophoneNotification();
        } catch (SecurityException | IllegalStateException e) {
            WHALELog.INSTANCE.e(TAG, "could not enter foreground (origin: " + origin + "): " + e.getMessage());
            stopSelf();
        }
    }

    private void showResumeMicrophoneNotification() {
        Intent intent = new Intent(this, getClass()).setAction(ACTION_ENABLE_MICROPHONE);
        // a notification tap allows the service to use the microphone again
        PendingIntent pendingIntent = PendingIntent.getForegroundService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, "SEChannel")
                .setContentTitle(getString(R.string.resume_microphone_title))
                .setContentText(getString(R.string.resume_microphone_text))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(getString(R.string.resume_microphone_text)))
                .setSmallIcon(R.drawable.notification_whale)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(true)
                .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(RESUME_MICROPHONE_NOTIF_ID, notification);
    }

    private void cancelResumeMicrophoneNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(RESUME_MICROPHONE_NOTIF_ID);
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
        return createNotificationChannel(NOTIFICATION_CHANNEL_ID, getString(NOTIFICATION_CHANNEL_STRING_ID_NAME));
    }

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

        String action = intent != null ? intent.getAction() : null;
        String origin = intent == null ? "sticky_restart" : (action != null ? action : "start");
        if (!isForeground || (ACTION_ENABLE_MICROPHONE.equals(action) && !microphoneActive)) {
            startForegroundWithBestType(origin);
        }

        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        WHALELog.INSTANCE.d(TAG, "Service Stopped");

        if (m_wakeLock.isHeld()) {
            m_wakeLock.release();
        }

        isForeground = false;
        microphoneActive = false;
        cancelResumeMicrophoneNotification();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
