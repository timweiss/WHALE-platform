package de.mimuc.senseeverything.sensor.implementation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.sensor.AbstractSensor
import de.mimuc.senseeverything.service.accessibility.SnapshotBatchManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Sensor that listens to UI tree snapshot batches broadcast by the accessibility service.
 * Queries snapshot data from Room database using broadcast IDs to avoid TransactionTooLargeException.
 */
class UITreeSensor(applicationContext: Context, database: AppDatabase) :
    AbstractSensor(applicationContext, database) {

    companion object {
        private const val serialVersionUID = 1L
    }

    private var context: Context? = null
    private var receiver: DataUpdateReceiver? = null

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

        this.context = context

        if (receiver == null) {
            receiver = DataUpdateReceiver()
        }

        val intentFilter = IntentFilter(SnapshotBatchManager.BROADCAST_ACTION)
        ContextCompat.registerReceiver(
            context,
            receiver,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        m_IsRunning = true
    }

    override fun stop() {
        m_IsRunning = false
        context?.let {
            receiver?.let { rcv ->
                it.unregisterReceiver(rcv)
            }
        }
    }

    private inner class DataUpdateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == SnapshotBatchManager.BROADCAST_ACTION) {
                if (m_IsRunning) {
                    val batchId = intent.getLongExtra(SnapshotBatchManager.EXTRA_BATCH_ID, -1)
                    val count = intent.getIntExtra(SnapshotBatchManager.EXTRA_COUNT, 0)

                    if (batchId == -1L) {
                        WHALELog.e(TAG, "Received broadcast without valid batch ID")
                        return
                    }

                    WHALELog.d(TAG, "Received broadcast for batch ID: $batchId ($count snapshots)")

                    // Launch coroutine to fetch from database
                    CoroutineScope(Dispatchers.IO).launch {
                        fetchAndProcessBatch(batchId)
                    }
                }
            }
        }

        private suspend fun fetchAndProcessBatch(batchId: Long) {
            try {
                val dao = database.snapshotBatchDao()

                // Query database
                val batch = dao.getById(batchId)

                if (batch == null) {
                    WHALELog.e(TAG, "Batch ID $batchId not found in database")
                    return
                }

                WHALELog.i(TAG, "Fetched batch ID $batchId: ${batch.jsonData.length} bytes")

                // Log the data to permanent storage
                onLogDataItem(System.currentTimeMillis(), batch.jsonData)

                // Delete from staging table (cleanup)
                dao.deleteById(batchId)

                WHALELog.d(TAG, "Processed and deleted batch ID $batchId")

            } catch (e: Exception) {
                WHALELog.e(TAG, "Failed to fetch batch from database: ${e.message}")
            }
        }
    }
}
