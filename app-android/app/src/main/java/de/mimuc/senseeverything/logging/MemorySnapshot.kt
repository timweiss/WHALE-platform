package de.mimuc.senseeverything.logging

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Debug
import android.os.SystemClock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The device's memory headroom and the current process' footprint at one point in time. Written
 * as a "Memory" row by [ProcessDiagnostics] (process start, background trim) and by the
 * MemorySensor (while sampling).
 */
@Serializable
data class MemorySnapshot(
    val process: String,
    /** start, background or sampling */
    val reason: String,
    val availMb: Long,
    val totalMb: Long,
    /** null when the total memory is unknown */
    val availPct: Int?,
    /** whether the system considers itself low on memory and is killing background processes */
    val lowMemory: Boolean,
    val thresholdMb: Long,
    /** ActivityManager.RunningAppProcessInfo importance, e.g. 125 foreground service, 400 cached */
    val importance: Int,
    val lastTrimLevel: Int,
    /** resident set size of the process; null when /proc/self/status could not be read */
    val rssKb: Long?,
    val nativeHeapKb: Long
) {
    fun toJson(): String = Json.encodeToString(this)

    companion object {
        const val SENSOR_NAME = "Memory"
        private const val BYTES_PER_MB = 1024 * 1024

        // per process, as every process has its own copy of this object
        @Volatile
        private var lastCollectedAt: Long? = null

        /**
         * Returns true, and counts it as collected, if no snapshot was collected in this process
         * for at least [minIntervalMs]. Checked and set together, as a sampling cycle and an
         * unlock can start the sensor within milliseconds of each other.
         */
        @Synchronized
        fun claimIfDue(minIntervalMs: Long): Boolean {
            val now = SystemClock.elapsedRealtime()
            if (!hasIntervalPassed(now, lastCollectedAt, minIntervalMs)) return false
            lastCollectedAt = now
            return true
        }

        /** Reads the current values. A binder call and a small /proc read, no PSS computation. */
        fun collect(context: Context, reason: String): MemorySnapshot {
            lastCollectedAt = SystemClock.elapsedRealtime()
            val activityManager = context.getSystemService(ActivityManager::class.java)
            val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
            val processState = ActivityManager.RunningAppProcessInfo()
                .also { ActivityManager.getMyMemoryState(it) }

            return of(
                process = Application.getProcessName(),
                reason = reason,
                availBytes = memoryInfo.availMem,
                totalBytes = memoryInfo.totalMem,
                thresholdBytes = memoryInfo.threshold,
                lowMemory = memoryInfo.lowMemory,
                importance = processState.importance,
                lastTrimLevel = processState.lastTrimLevel,
                rssKb = readVmRssKb(),
                nativeHeapBytes = Debug.getNativeHeapAllocatedSize()
            )
        }

        internal fun of(
            process: String,
            reason: String,
            availBytes: Long,
            totalBytes: Long,
            thresholdBytes: Long,
            lowMemory: Boolean,
            importance: Int,
            lastTrimLevel: Int,
            rssKb: Long?,
            nativeHeapBytes: Long
        ) = MemorySnapshot(
            process = process,
            reason = reason,
            availMb = availBytes / BYTES_PER_MB,
            totalMb = totalBytes / BYTES_PER_MB,
            availPct = if (totalBytes > 0) Math.round(availBytes * 100.0 / totalBytes).toInt() else null,
            lowMemory = lowMemory,
            thresholdMb = thresholdBytes / BYTES_PER_MB,
            importance = importance,
            lastTrimLevel = lastTrimLevel,
            rssKb = rssKb,
            nativeHeapKb = nativeHeapBytes / 1024
        )

        private fun readVmRssKb(): Long? = try {
            parseVmRssKb(File("/proc/self/status").readText())
        } catch (_: Exception) {
            null
        }

        /** Extracts the `VmRSS` value in kB from the contents of /proc/<pid>/status. */
        internal fun parseVmRssKb(status: String): Long? =
            status.lineSequence()
                .firstOrNull { it.startsWith("VmRSS:") }
                ?.removePrefix("VmRSS:")
                ?.trim()
                ?.substringBefore(' ')
                ?.toLongOrNull()

        internal fun hasIntervalPassed(now: Long, lastAt: Long?, minIntervalMs: Long): Boolean =
            lastAt == null || now - lastAt >= minIntervalMs
    }
}
