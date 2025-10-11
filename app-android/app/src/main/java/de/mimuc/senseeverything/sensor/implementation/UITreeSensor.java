package de.mimuc.senseeverything.sensor.implementation;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import de.mimuc.senseeverything.db.AppDatabase;
import de.mimuc.senseeverything.sensor.AbstractSensor;
import de.mimuc.senseeverything.service.accessibility.SnapshotBatchManager;

/**
 * Sensor that listens to UI tree snapshot batches broadcast by the accessibility service.
 * Logs the batched JSON data to the database for later analysis.
 */
public class UITreeSensor extends AbstractSensor {

    private static final long serialVersionUID = 1L;

    private Context m_Context = null;
    private DataUpdateReceiver m_Receiver;

    public UITreeSensor(Context applicationContext, AppDatabase database) {
        super(applicationContext, database);
        m_IsRunning = false;
        TAG = "UITreeSensor";
        SENSOR_NAME = "UITree";
        FILE_NAME = "ui_tree.json";
        m_FileHeader = ""; // JSON format, no CSV header needed
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

        IntentFilter intentFilter = new IntentFilter(SnapshotBatchManager.BROADCAST_ACTION);
        m_Context.registerReceiver(m_Receiver, intentFilter);

        m_IsRunning = true;
    }

    @Override
    public void stop() {
        m_IsRunning = false;
        if (m_Context == null)
            return;
        m_Context.unregisterReceiver(m_Receiver);
    }

    private class DataUpdateReceiver extends BroadcastReceiver {
        public DataUpdateReceiver() {
            super();
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(SnapshotBatchManager.BROADCAST_ACTION)) {
                if (m_IsRunning) {
                    String batchJson = intent.getStringExtra(SnapshotBatchManager.EXTRA_BATCH_JSON);
                    if (batchJson != null) {
                        onLogDataItem(System.currentTimeMillis(), batchJson);
                    }
                }
            }
        }
    }
}