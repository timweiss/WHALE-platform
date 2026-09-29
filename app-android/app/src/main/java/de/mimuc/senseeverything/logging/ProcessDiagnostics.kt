package de.mimuc.senseeverything.logging

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.ComponentCallbacks2
import android.content.Context
import de.mimuc.senseeverything.sensor.BufferedLogWriter

/**
 * Records why app processes ended and when they were under memory pressure, so that sampling
 * outages can be attributed to low-memory kills, ANRs or crashes.
 */
object ProcessDiagnostics {
    private const val TAG = "ProcessDiagnostics"
    private const val PREFS = "process_diagnostics"
    private const val MAX_EXIT_REASONS = 20
    private const val CRASH_FLUSH_TIMEOUT_MS = 2_000L

    /**
     * Logs every exit of the current process that has not been reported yet. Each process reports
     * only its own exits, so the main and `:remote` process don't log the same entries twice.
     */
    @JvmStatic
    fun logUnreportedExitReasons(context: Context) {
        Thread({
            try {
                val processName = Application.getProcessName()
                val activityManager = context.getSystemService(ActivityManager::class.java)
                // one file per process: processes writing the same prefs file overwrite each other
                val prefsName = PREFS + "_" + processName.replace(Regex("[^A-Za-z0-9]"), "_")
                val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                val key = "last_exit_ts"
                val lastReported = prefs.getLong(key, 0L)

                val exits = activityManager
                    .getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXIT_REASONS)
                    .filter { it.processName == processName && it.timestamp > lastReported }
                    .sortedBy { it.timestamp }

                exits.forEach { WHALELog.i(TAG, describe(it)) }

                exits.maxOfOrNull { it.timestamp }?.let {
                    prefs.edit().putLong(key, it).apply()
                }
            } catch (e: Exception) {
                WHALELog.e(TAG, "Failed to read process exit reasons: ${e.message}", e)
            }
        }, "ProcessExitLogger").start()
    }

    /**
     * Writes rows still buffered by [BufferedLogWriter] before an uncaught exception ends the
     * process, then hands the exception to the previous handler (which kills the process).
     */
    @JvmStatic
    fun installCrashFlush() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                BufferedLogWriter.flushAllBlocking(CRASH_FLUSH_TIMEOUT_MS)
            } catch (_: Throwable) {
                // never keep the crash from being handled
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    @JvmStatic
    fun onTrimMemory(level: Int) {
        // UI_HIDDEN only means the app's UI went to the background, not memory pressure
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return

        WHALELog.i(TAG, "onTrimMemory process=${Application.getProcessName()} level=${trimLevelName(level)}")
        BufferedLogWriter.flushAll()
    }

    private fun describe(exit: ApplicationExitInfo): String =
        "processExit process=${exit.processName}" +
            " reason=${reasonName(exit.reason)}" +
            " status=${exit.status}" +
            " importance=${exit.importance}" +
            " pssKb=${exit.pss}" +
            " rssKb=${exit.rss}" +
            " timestamp=${exit.timestamp}" +
            " description=${exit.description}"

    private fun reasonName(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        // constants added after API 30
        14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"
        else -> "UNKNOWN($reason)"
    }

    private fun trimLevelName(level: Int): String = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
        ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
        else -> "LEVEL_$level"
    }
}
