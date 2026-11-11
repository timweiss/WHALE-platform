package de.mimuc.senseeverything.service.accessibility

import de.mimuc.senseeverything.api.model.ExperimentalGroupPhase
import de.mimuc.senseeverything.study.PhaseScheduleInfo
import java.time.Instant
import java.time.ZoneId

/** The packages whose displayed text is captured, valid in [start, end). */
data class TextCapturePeriod(
    val packages: Set<String>,
    val start: Long,
    val end: Long
)

/**
 * The text capture period at [time]: the packages of the phase running at [time], valid until the
 * phase ends. Before the first phase, in gaps between phases, after the last phase and in phases
 * without text settings, no packages are captured until the next phase boundary.
 *
 * Phase starts are taken from the phase schedule (see [de.mimuc.senseeverything.study.schedulePhaseChanges]).
 * A schedule entry runs until the next phase starts, so before a gap, the phase is cut off at the
 * midnight after its last day, computed in [zone].
 */
internal fun resolveTextCapture(
    schedules: List<PhaseScheduleInfo>,
    phases: List<ExperimentalGroupPhase>,
    time: Long,
    zone: ZoneId = ZoneId.systemDefault()
): TextCapturePeriod {
    val sorted = schedules.sortedBy { it.startTimestamp }
    val periods = sorted.mapIndexed { index, schedule ->
        val phase = phases.find { it.name == schedule.phaseName }
        TextCapturePeriod(
            packages = phase?.sensorSettings?.uiTreeTextSettings?.packages?.toSet().orEmpty(),
            start = schedule.startTimestamp,
            end = phaseEnd(schedule, phase, sorted.getOrNull(index + 1), phases, zone)
        )
    }

    periods.firstOrNull { time in it.start..<it.end }?.let { return it }

    return TextCapturePeriod(
        packages = emptySet(),
        start = periods.filter { it.end <= time }.maxOfOrNull { it.end } ?: Long.MIN_VALUE,
        end = periods.filter { it.start > time }.minOfOrNull { it.start } ?: Long.MAX_VALUE
    )
}

/** The schedule entry's end, or the start of the gap if the next phase does not follow directly. */
private fun phaseEnd(
    schedule: PhaseScheduleInfo,
    phase: ExperimentalGroupPhase?,
    nextSchedule: PhaseScheduleInfo?,
    phases: List<ExperimentalGroupPhase>,
    zone: ZoneId
): Long {
    val nextPhase = nextSchedule?.let { next -> phases.find { it.name == next.phaseName } }
    if (phase == null || nextSchedule == null || nextPhase == null) return schedule.endTimestamp

    val gapDays = nextPhase.fromDay - (phase.fromDay + phase.durationDays)
    if (gapDays <= 0) return schedule.endTimestamp

    // the next phase starts at midnight, the gap starts gapDays midnights earlier
    return Instant.ofEpochMilli(nextSchedule.startTimestamp).atZone(zone).toLocalDate()
        .minusDays(gapDays.toLong())
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
}
