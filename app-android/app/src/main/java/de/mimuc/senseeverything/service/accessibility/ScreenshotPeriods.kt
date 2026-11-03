package de.mimuc.senseeverything.service.accessibility

import de.mimuc.senseeverything.logging.WHALELog
import java.io.File

/**
 * Finds the planned sampling period (see [planScreenshotPeriods]) that is running and opens its store.
 *
 * [current] is called for every logged UI tree. While a period runs, that is one time comparison;
 * otherwise a search of the schedule in memory. The disk is only touched when a period opens, also
 * after a process restart: the new store then continues the period with its state on disk.
 *
 * Not thread-safe; use it from the capture worker only, like [ScreenshotStore].
 */
class ScreenshotPeriods(
    private val root: File,
    /** the planned periods, null while not loaded or not planned; called for every UI tree */
    private val schedule: () -> List<ScreenshotPeriod>?,
    private val infoLog: (String) -> Unit = { WHALELog.i(TAG, it) },
    private val warnLog: (String) -> Unit = { WHALELog.w(TAG, it) }
) {
    companion object {
        const val TAG = "ScreenshotPeriods"
    }

    class Running(val period: ScreenshotPeriod, val store: ScreenshotStore)

    private var running: Running? = null

    /** The running period with its store, or null if nothing is sampled right now. */
    fun current(now: Long = System.currentTimeMillis()): Running? {
        running?.let { if (now < it.period.end) return it }

        val schedule = schedule()
        val period = schedule?.firstOrNull { now >= it.start && now < it.end }
        if (period == null) {
            // the last period before a gap or the study end is reported once, when it has ended
            if (running != null) {
                ScreenshotStore.reportEndedPeriods(root, schedule, now, infoLog)
                running = null
            }
            return null
        }

        ScreenshotStore.reportEndedPeriods(root, schedule, now, infoLog)
        val reopened = ScreenshotStore.periodDir(root, period.end).exists()
        infoLog("${if (reopened) "Reopened" else "Opened"} period: start=${period.start}" +
                " end=${period.end} sampleSizes=${period.sampleSizes} phase=${period.phaseName}")
        return Running(period, ScreenshotStore(root, period.end, warnLog)).also { running = it }
    }
}
