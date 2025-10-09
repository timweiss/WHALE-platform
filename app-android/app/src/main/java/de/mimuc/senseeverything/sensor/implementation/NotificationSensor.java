package de.mimuc.senseeverything.sensor.implementation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.sensor.AbstractSensor;
import de.mimuc.senseeverything.service.MyNotificationListenerService;

public class NotificationSensor extends AbstractSensor {

    private Context m_Context = null;
    private DataUpdateReceiver m_Receiver;

    public NotificationSensor(Context applicationContext, AppDatabase database) {
        super(applicationContext, database);
        TAG = getClass().getName();
        SENSOR_NAME = "Notification";
        FILE_NAME = "notification.csv";
        m_FileHeader = "TimeUnix,When,Key,Package,ShouldVibrate,Importance,Category";
    }

    @Override
    public boolean isAvailable(Context context) {
        return true;
    }

    @Override
    public boolean availableForPeriodicSampling() {
        return false;
    }

    @Override
    public boolean availableForContinuousSampling() {
        return true;
    }

    @Override
    public void start(Context context) {
        super.start(context);
        if (!m_isSensorAvailable)
            return;

        m_Context = context;

        if (m_Receiver == null)
            m_Receiver = new DataUpdateReceiver();

        IntentFilter intentFilter = new IntentFilter(MyNotificationListenerService.NOTIFICATION_SENSOR_BROADCAST_ACTION);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            m_Context.registerReceiver(m_Receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            m_Context.registerReceiver(m_Receiver, intentFilter);
        }

        m_IsRunning = true;
    }

    @Override
    public void stop() {
        m_IsRunning = false;
        if (m_Context == null)
            return;
        m_Context.unregisterReceiver(m_Receiver);
        closeDataSource();
    }

    private class DataUpdateReceiver extends BroadcastReceiver {

        public DataUpdateReceiver() {
            super();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (MyNotificationListenerService.NOTIFICATION_SENSOR_BROADCAST_ACTION.equals(intent.getAction())) {
                if (m_IsRunning) {
                    onLogDataItem(System.currentTimeMillis(), intent.getStringExtra(Intent.EXTRA_TEXT));
                }
            }
        }
    }
}