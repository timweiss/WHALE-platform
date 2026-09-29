package de.mimuc.senseeverything.sensor.implementation

import android.content.Context
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.sensor.AbstractSensor
import de.mimuc.senseeverything.service.accessibility.AccessibilityDataBus

/**
 * Sensor that receives UI tree snapshot batches from the accessibility service.
 * Both run in the `:remote` process, so batches are handed over in memory via [AccessibilityDataBus].
 */
class UITreeSensor(applicationContext: Context, database: AppDatabase) :
    AbstractSensor(applicationContext, database) {

    companion object {
        private const val serialVersionUID = 1L
    }

    init {
        m_IsRunning = false
        this.TAG = "UITreeSensor"
        SENSOR_NAME = "UITree"
        FILE_NAME = "ui_tree.json"
        m_FileHeader = "" // JSON format, no CSV header needed
    }

    override fun isAvailable(context: Context): Boolean = true

    override fun availableForPeriodicSampling(): Boolean = false

    override fun availableForContinuousSampling(): Boolean = true

    override fun start(context: Context) {
        super.start(context)
        if (!m_isSensorAvailable) return

        // called on the batch manager's background thread
        AccessibilityDataBus.snapshotBatchListener = AccessibilityDataBus.SnapshotBatchListener { payload, count, _ ->
            if (!m_IsRunning) return@SnapshotBatchListener
            WHALELog.d(TAG, "Received batch ($count snapshots, ${payload.length} chars compressed)")
            onLogDataItem(System.currentTimeMillis(), payload)
        }

        m_IsRunning = true
    }

    override fun stop() {
        m_IsRunning = false
        AccessibilityDataBus.snapshotBatchListener = null
        flushLogData()
    }
}
