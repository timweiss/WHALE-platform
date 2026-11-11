package de.mimuc.senseeverything.service.accessibility

import de.mimuc.senseeverything.api.model.ExperimentalGroupPhase
import de.mimuc.senseeverything.api.model.InteractionWidgetDisplayStrategy
import de.mimuc.senseeverything.api.model.SensorSettings
import de.mimuc.senseeverything.api.model.UiTreeTextSettings
import de.mimuc.senseeverything.study.PhaseScheduleInfo
import de.mimuc.senseeverything.study.PhaseSchedulingStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TextCaptureTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val app = "com.instagram.android"

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun phase(name: String, fromDay: Int, durationDays: Int, packages: List<String>?) =
        ExperimentalGroupPhase(
            1, name, fromDay, durationDays, InteractionWidgetDisplayStrategy.DEFAULT,
            SensorSettings(null, packages?.let { UiTreeTextSettings(it) })
        )

    private fun schedule(name: String, start: String, end: String) =
        PhaseScheduleInfo(name, at(start), at(end), PhaseSchedulingStatus.SCHEDULED)

    // study starts 2026-11-03T14:00, so the day-0 phase starts two minutes later (see schedulePhaseChanges)
    private val baseline = phase("baseline", 0, 2, listOf(app))
    private val treatment = phase("treatment", 2, 2, null)
    private val followUp = phase("followUp", 6, 1, listOf(app))
    private val phases = listOf(baseline, treatment, followUp)
    private val schedules = listOf(
        schedule("baseline", "2026-11-03T14:02", "2026-11-05T00:00"),
        // the schedule runs the treatment until the follow-up starts, across the gap on days 4 and 5
        schedule("treatment", "2026-11-05T00:00", "2026-11-09T00:00"),
        schedule("followUp", "2026-11-09T00:00", "2026-11-10T00:00")
    )

    private fun resolve(time: String) = resolveTextCapture(schedules, phases, at(time), zone)

    @Test
    fun insideAPhaseItsPackagesAreCapturedUntilItEnds() {
        assertEquals(
            TextCapturePeriod(setOf(app), at("2026-11-03T14:02"), at("2026-11-05T00:00")),
            resolve("2026-11-04T12:00")
        )
    }

    @Test
    fun theBoundaryBelongsToTheNextPhase() {
        assertEquals(
            TextCapturePeriod(emptySet(), at("2026-11-05T00:00"), at("2026-11-07T00:00")),
            resolve("2026-11-05T00:00")
        )
    }

    @Test
    fun aPhaseWithoutTextSettingsCapturesNothing() {
        assertEquals(
            TextCapturePeriod(emptySet(), at("2026-11-05T00:00"), at("2026-11-07T00:00")),
            resolve("2026-11-06T12:00")
        )
    }

    @Test
    fun aGapCapturesNothingUntilTheNextPhase() {
        assertEquals(
            TextCapturePeriod(emptySet(), at("2026-11-07T00:00"), at("2026-11-09T00:00")),
            resolve("2026-11-08T12:00")
        )
    }

    @Test
    fun aGapAfterAPhaseWithTextSettingsCapturesNothing() {
        val phases = listOf(phase("baseline", 0, 2, listOf(app)), phase("followUp", 4, 1, listOf(app)))
        val schedules = listOf(
            schedule("baseline", "2026-11-03T14:02", "2026-11-07T00:00"),
            schedule("followUp", "2026-11-07T00:00", "2026-11-08T00:00")
        )

        assertEquals(
            TextCapturePeriod(setOf(app), at("2026-11-03T14:02"), at("2026-11-05T00:00")),
            resolveTextCapture(schedules, phases, at("2026-11-04T23:59"), zone)
        )
        assertEquals(
            TextCapturePeriod(emptySet(), at("2026-11-05T00:00"), at("2026-11-07T00:00")),
            resolveTextCapture(schedules, phases, at("2026-11-05T00:00"), zone)
        )
    }

    @Test
    fun beforeTheFirstPhaseNothingIsCaptured() {
        assertEquals(
            TextCapturePeriod(emptySet(), Long.MIN_VALUE, at("2026-11-03T14:02")),
            resolve("2026-11-03T14:00")
        )
    }

    @Test
    fun afterTheLastPhaseNothingIsCaptured() {
        assertEquals(
            TextCapturePeriod(emptySet(), at("2026-11-10T00:00"), Long.MAX_VALUE),
            resolve("2026-11-10T00:00")
        )
    }
}
