package de.mimuc.senseeverything.service.accessibility

import android.content.Context
import android.content.Intent
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
 */
class SnapshotBatchManager(
    private val context: Context,
    private val batchSize: Int = 50,
    private val flushIntervalMs: Long = TimeUnit.MINUTES.toMillis(1)
) {
    companion object {
        const val TAG = "SnapshotBatchManager"
        const val BROADCAST_ACTION = "de.mimuc.senseeverything.ACCESSIBILITY_SNAPSHOT_BATCH"
        const val EXTRA_BATCH_JSON = "batch_json"
    }

    private val batchQueue = ConcurrentLinkedQueue<ScreenSnapshot>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flushJob: Job? = null

    init {
        startPeriodicFlush()
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
        try {
            val intent = Intent(BROADCAST_ACTION)
            intent.putExtra(EXTRA_BATCH_JSON, batch.toString())
            context.sendBroadcast(intent)
            WHALELog.i(TAG, "Broadcast batch with ${batch.getInt("count")} snapshots")
        } catch (e: Exception) {
            WHALELog.e(TAG, "Failed to broadcast batch", e)
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

    /**
     * Get statistics about current batching state
     */
    fun getStats(): BatchStats {
        return BatchStats(
            queueSize = batchQueue.size
        )
    }

    fun shutdown() {
        flushJob?.cancel()
        flushBatch()
        scope.cancel()
    }
}

data class BatchStats(
    val queueSize: Int
)