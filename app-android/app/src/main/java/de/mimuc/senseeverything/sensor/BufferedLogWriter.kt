package de.mimuc.senseeverything.sensor

import de.mimuc.senseeverything.db.models.LogData
import de.mimuc.senseeverything.db.models.LogDataDao
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Collects [LogData] rows for high-frequency sensors and writes them in one transaction per batch,
 * instead of one AsyncTask and one transaction per row.
 *
 * Rows are flushed when [maxBatchSize] rows are buffered or [flushIntervalMs] after the first
 * buffered row, whichever comes first. All database work runs on a single background thread.
 */
class BufferedLogWriter @JvmOverloads constructor(
    private val dao: LogDataDao,
    private val maxBatchSize: Int = 50,
    private val flushIntervalMs: Long = 2000L,
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "BufferedLogWriter").apply { isDaemon = true }
    }
) {
    companion object {
        private val instances: MutableSet<BufferedLogWriter> =
            Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

        /** Flushes every live writer in this process, e.g. on memory pressure. */
        @JvmStatic
        fun flushAll() {
            synchronized(instances) { instances.toList() }.forEach { it.flush() }
        }
    }

    private val lock = Any()
    private var buffer = ArrayList<LogData>(maxBatchSize)
    private var flushScheduled = false

    init {
        instances.add(this)
    }

    fun add(row: LogData) {
        var flushNow = false
        synchronized(lock) {
            buffer.add(row)
            if (buffer.size >= maxBatchSize) {
                flushNow = true
            } else if (!flushScheduled) {
                flushScheduled = true
                executor.schedule({ writePending() }, flushIntervalMs, TimeUnit.MILLISECONDS)
            }
        }
        if (flushNow) flush()
    }

    /** Asynchronously writes everything buffered so far. */
    fun flush() {
        if (executor.isShutdown) return
        executor.execute { writePending() }
    }

    private fun writePending() {
        val rows: List<LogData>
        synchronized(lock) {
            flushScheduled = false
            if (buffer.isEmpty()) return
            rows = buffer
            buffer = ArrayList(maxBatchSize)
        }
        try {
            dao.insertAll(*rows.toTypedArray())
        } catch (e: Exception) {
            // Logging here would go through the same database, so only print the failure.
            System.err.println("BufferedLogWriter: failed to write ${rows.size} rows: $e")
        }
    }
}
