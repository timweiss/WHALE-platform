package de.mimuc.senseeverything.service.accessibility

import android.content.Context
import android.content.Intent
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.SnapshotBatch
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.accessibility.model.ScreenSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * Manages batching of screen snapshots and broadcasts them for sensor collection.
 * Uses Room database for cross-process IPC to avoid TransactionTooLargeException.
 */
class SnapshotBatchManager(
    private val context: Context,
    private val database: AppDatabase,
    private val batchSize: Int = 4,
    private val flushIntervalMs: Long = TimeUnit.SECONDS.toMillis(30)
) {
    companion object {
        const val TAG = "SnapshotBatchManager"
        const val BROADCAST_ACTION = "de.mimuc.senseeverything.ACCESSIBILITY_SNAPSHOT_BATCH"
        const val EXTRA_BATCH_ID = "batch_id"
        const val EXTRA_COUNT = "count"
        const val EXTRA_TIMESTAMP = "timestamp"
    }

    private val batchQueue = ConcurrentLinkedQueue<ScreenSnapshot>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flushJob: Job? = null
    private var cleanupJob: Job? = null

    init {
        startPeriodicFlush()
        startPeriodicCleanup()
    }

    fun addSnapshot(snapshot: ScreenSnapshot) {
        batchQueue.offer(snapshot)

        if (batchQueue.size >= batchSize) {
            flushBatch()
        }
    }

    fun flushBatch() {
        scope.launch {
            val snapshots = mutableListOf<ScreenSnapshot>()

            // Drain queue
            while (batchQueue.isNotEmpty() && snapshots.size < batchSize) {
                batchQueue.poll()?.let { snapshots.add(it) }
            }

            if (snapshots.isEmpty()) return@launch

            val batch = createBatchJson(snapshots)
            broadcastBatch(batch)

            WHALELog.i(TAG, "Flushed batch of ${snapshots.size} snapshots via broadcast")
        }
    }

    private fun createBatchJson(snapshots: List<ScreenSnapshot>): JSONObject {
        return JSONObject().apply {
            put("timestamp", System.currentTimeMillis())
            put("count", snapshots.size)
            put("snapshots", JSONArray().apply {
                snapshots.forEach { put(it.toJson()) }
            })
        }
    }

    private fun broadcastBatch(batch: JSONObject) {
        scope.launch {
            try {
                val jsonString = batch.toString()
                val sizeBytes = jsonString.toByteArray(Charsets.UTF_8).size

                // Insert into database
                val snapshotBatch = SnapshotBatch(
                    timestamp = batch.getLong("timestamp"),
                    jsonData = jsonString,
                    count = batch.getInt("count"),
                    createdAt = System.currentTimeMillis()
                )

                val batchId = database.snapshotBatchDao().insert(snapshotBatch)

                WHALELog.i(TAG, "Inserted batch ID $batchId ($sizeBytes bytes, ${batch.getInt("count")} snapshots) into database")

                // Broadcast lightweight Intent with ID only
                val intent = Intent(BROADCAST_ACTION).apply {
                    putExtra(EXTRA_BATCH_ID, batchId)
                    putExtra(EXTRA_COUNT, batch.getInt("count"))
                    putExtra(EXTRA_TIMESTAMP, batch.getLong("timestamp"))
                }

                context.sendBroadcast(intent)
                WHALELog.d(TAG, "Broadcast sent with batch ID $batchId")

            } catch (e: Exception) {
                WHALELog.e(TAG, "Failed to store and broadcast batch: ${e.message}", e)
            }
        }
    }

    private fun startPeriodicFlush() {
        flushJob = scope.launch {
            while (isActive) {
                delay(flushIntervalMs)
                if (batchQueue.isNotEmpty()) {
                    flushBatch()
                }
            }
        }
    }

    private fun startPeriodicCleanup() {
        cleanupJob = scope.launch {
            while (isActive) {
                delay(TimeUnit.MINUTES.toMillis(5))
                cleanupOldBatches()
            }
        }
    }

    private suspend fun cleanupOldBatches() {
        try {
            val cutoffTime = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(5)
            val deleted = database.snapshotBatchDao().deleteOlderThan(cutoffTime)
            if (deleted > 0) {
                WHALELog.i(TAG, "Cleaned up $deleted old snapshot batches")
            }
        } catch (e: Exception) {
            WHALELog.e(TAG, "Failed to cleanup old batches: ${e.message}", e)
        }
    }

    /**
     * Get statistics about current batching state
     */
    suspend fun getStats(): BatchStats {
        val stagingCount = database.snapshotBatchDao().getCount()
        return BatchStats(
            queueSize = batchQueue.size,
            stagingTableSize = stagingCount
        )
    }

    fun shutdown() {
        flushJob?.cancel()
        cleanupJob?.cancel()
        flushBatch()
        scope.cancel()
    }
}

data class BatchStats(
    val queueSize: Int,
    val stagingTableSize: Int
)