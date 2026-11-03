package de.mimuc.senseeverything.service.accessibility

import de.mimuc.senseeverything.api.model.ExperimentalGroupPhase
import de.mimuc.senseeverything.data.phaseOnStudyDay
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

private const val TAG = "ScreenshotSchedule"

/** One sampling period, planned when the study starts; its settings never change. */
@Serializable
data class ScreenshotPeriod(
    /** the study start, the previous rating time, or the midnight after days without settings */
    val start: Long,
    /** the rating time that ends the period, also its key on disk */
    val end: Long,
    /**
     * the study phase the settings were taken from; a period can outlast its phase, so this
     * attributes the period's sample to a phase in the analysis
     */
    val phaseName: String,
    /** reservoir size N per package; packages not listed are not sampled */
    val sampleSizes: Map<String, Int>
)

/**
 * Plans all sampling periods of the study. Each period takes the settings of the phase of the study
 * day it starts on (by calendar date, like [de.mimuc.senseeverything.data.getCurrentStudyPhase]) and
 * keeps them until its rating time, even if a phase change falls inside it. Days of a phase without
 * screenshot settings, gaps between phases and the time after the last phase get no period.
 *
 * Timestamps are absolute, computed in [zone]: if the participant changes time zones, the rating
 * time stays at the time of the zone the study started in.
 */
fun planScreenshotPeriods(
    studyStart: Long,
    phases: List<ExperimentalGroupPhase>,
    zone: ZoneId = ZoneId.systemDefault(),
    warn: (String) -> Unit = { WHALELog.w(TAG, it) }
): List<ScreenshotPeriod> {
    // exclusive, 0-based like ExperimentalGroupPhase.fromDay
    val endDay = phases.maxOfOrNull { it.fromDay + it.durationDays } ?: return emptyList()
    val ratingTimes = phases.associateWith { phase ->
        phase.screenshotSettings?.let {
            try {
                LocalTime.parse(it.periodEnd)
            } catch (e: DateTimeParseException) {
                warn("Invalid rating time '${it.periodEnd}' in phase ${phase.name}, not sampling in it")
                null
            }
        }
    }

    val startDate = Instant.ofEpochMilli(studyStart).atZone(zone).toLocalDate()
    val periods = mutableListOf<ScreenshotPeriod>()
    var cursor = Instant.ofEpochMilli(studyStart).atZone(zone)
    while (true) {
        val day = ChronoUnit.DAYS.between(startDate, cursor.toLocalDate())
        if (day >= endDay) break

        val phase = phases.phaseOnStudyDay(day)
        val settings = phase?.screenshotSettings
        val ratingTime = phase?.let { ratingTimes[it] }
        if (settings == null || ratingTime == null) {
            cursor = cursor.toLocalDate().plusDays(1).atStartOfDay(zone)
            continue
        }

        val today = cursor.with(ratingTime)
        val end = if (today.isAfter(cursor)) today else today.plusDays(1)
        periods.add(
            ScreenshotPeriod(
                start = cursor.toInstant().toEpochMilli(),
                end = end.toInstant().toEpochMilli(),
                phaseName = phase.name,
                sampleSizes = settings.packageSettings.associate { it.packageName to it.sample }
            )
        )
        cursor = end
    }
    return periods
}

fun logScreenshotSchedule(periods: List<ScreenshotPeriod>) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val sb = StringBuilder()

    sb.appendLine("=== Screenshot Schedule ===")
    sb.appendLine("Total periods: ${periods.size}")

    periods.forEachIndexed { index, period ->
        sb.appendLine("Period ${index + 1}: ${period.phaseName}")
        sb.appendLine("  Start: ${dateFormat.format(Date(period.start))} (${period.start})")
        sb.appendLine("  End:   ${dateFormat.format(Date(period.end))} (${period.end})")
        sb.appendLine("  Sample sizes: ${period.sampleSizes}")
    }

    sb.append("===========================")

    WHALELog.i(TAG, sb.toString())
}
