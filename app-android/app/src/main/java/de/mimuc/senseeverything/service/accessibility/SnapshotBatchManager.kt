package de.mimuc.senseeverything.service.accessibility

import de.mimuc.senseeverything.db.AppDatabase
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

/**
 * Manages batching of screen snapshots and hands them to the UITreeSensor via [AccessibilityDataBus].
 */
class SnapshotBatchManager(
    private val database: AppDatabase,
    private val batchSize: Int = 4,
    private val flushIntervalMs: Long = TimeUnit.SECONDS.toMillis(30)
) {
    companion object {
        const val TAG = "SnapshotBatchManager"
    }

    private val batchQueue = ConcurrentLinkedQueue<ScreenSnapshot>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flushJob: Job? = null

    init {
        purgeStagingTable()
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

            deliverBatch(snapshots)

            WHALELog.d(TAG, "Flushed batch of ${snapshots.size} snapshots")
        }
    }

    private fun deliverBatch(snapshots: List<ScreenSnapshot>) {
        val listener = AccessibilityDataBus.snapshotBatchListener
        if (listener == null) {
            WHALELog.d(TAG, "No UITreeSensor listening, dropping batch")
            return
        }

        try {
            val timestamp = System.currentTimeMillis()
            listener.onBatch(SnapshotBatchEncoder.encode(timestamp, snapshots), snapshots.size, timestamp)
        } catch (e: Exception) {
            WHALELog.e(TAG, "Failed to deliver batch: ${e.message}", e)
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
     * Batches used to be staged in the snapshot_batches table for a broadcast hop.
     * The table is no longer written to; remove rows left over from older app versions.
     */
    private fun purgeStagingTable() {
        scope.launch {
            try {
                val deleted = database.snapshotBatchDao().deleteOlderThan(System.currentTimeMillis())
                if (deleted > 0) {
                    WHALELog.i(TAG, "Purged $deleted leftover snapshot batches")
                }
            } catch (e: Exception) {
                WHALELog.e(TAG, "Failed to purge snapshot batches: ${e.message}", e)
            }
        }
    }

    fun shutdown() {
        flushJob?.cancel()
        // deliver what is left before cancelling the scope
        val remaining = mutableListOf<ScreenSnapshot>()
        while (batchQueue.isNotEmpty()) {
            batchQueue.poll()?.let { remaining.add(it) }
        }
        remaining.chunked(batchSize).forEach { deliverBatch(it) }
        scope.cancel()
    }
}
