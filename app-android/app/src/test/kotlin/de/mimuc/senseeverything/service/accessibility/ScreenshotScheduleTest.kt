package de.mimuc.senseeverything.service.accessibility

import de.mimuc.senseeverything.api.model.ExperimentalGroupPhase
import de.mimuc.senseeverything.api.model.InteractionWidgetDisplayStrategy
import de.mimuc.senseeverything.api.model.PackageScreenshotSettings
import de.mimuc.senseeverything.api.model.ScreenshotSettings
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScreenshotScheduleTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val app = "com.instagram.android"

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun phase(name: String, fromDay: Int, durationDays: Int, ratingTime: String?, sampleSize: Int = 10) =
        ExperimentalGroupPhase(
            1, name, fromDay, durationDays, InteractionWidgetDisplayStrategy.DEFAULT,
            ratingTime?.let { ScreenshotSettings(it, listOf(PackageScreenshotSettings(app, sampleSize))) }
        )

    private fun plan(studyStart: String, vararg phases: ExperimentalGroupPhase) =
        planScreenshotPeriods(at(studyStart), phases.toList(), zone, warn = {})

    /** start, end and phase of each period, for readable assertions */
    private fun List<ScreenshotPeriod>.summary() = map { Triple(it.start, it.end, it.phaseName) }

    @Test
    fun periodsRunFromRatingTimeToRatingTime() {
        val periods = plan("2026-11-03T14:00", phase("baseline", 0, 2, "20:00"))

        assertEquals(
            listOf(
                Triple(at("2026-11-03T14:00"), at("2026-11-03T20:00"), "baseline"),
                Triple(at("2026-11-03T20:00"), at("2026-11-04T20:00"), "baseline"),
                // starts on the last study day, so it runs until that day's rating time on the next day
                Triple(at("2026-11-04T20:00"), at("2026-11-05T20:00"), "baseline")
            ),
            periods.summary()
        )
        assertEquals(mapOf(app to 10), periods[0].sampleSizes)
    }

    @Test
    fun studyStartAfterTheRatingTimeOpensAPeriodUntilTheNextDay() {
        val periods = plan("2026-11-03T21:00", phase("baseline", 0, 1, "20:00"))

        assertEquals(listOf(Triple(at("2026-11-03T21:00"), at("2026-11-04T20:00"), "baseline")), periods.summary())
    }

    @Test
    fun periodKeepsTheSettingsOfThePhaseItStartedIn() {
        val periods = plan(
            "2026-11-03T14:00",
            phase("baseline", 0, 2, "20:00", sampleSize = 10),
            phase("treatment", 2, 1, "18:00", sampleSize = 3)
        )

        assertEquals(
            listOf(
                Triple(at("2026-11-03T14:00"), at("2026-11-03T20:00"), "baseline"),
                Triple(at("2026-11-03T20:00"), at("2026-11-04T20:00"), "baseline"),
                // the phase changes at midnight inside this period
                Triple(at("2026-11-04T20:00"), at("2026-11-05T20:00"), "baseline"),
                // starts on a treatment day, so it ends at the treatment's rating time
                Triple(at("2026-11-05T20:00"), at("2026-11-06T18:00"), "treatment")
            ),
            periods.summary()
        )
        assertEquals(mapOf(app to 3), periods.last().sampleSizes)
    }

    @Test
    fun daysWithoutSettingsAndGapsGetNoPeriod() {
        val periods = plan(
            "2026-11-03T14:00",
            phase("baseline", 0, 1, "20:00"),
            phase("washout", 1, 1, null),
            // day 2 belongs to no phase
            phase("treatment", 3, 1, "20:00")
        )

        assertEquals(
            listOf(
                Triple(at("2026-11-03T14:00"), at("2026-11-03T20:00"), "baseline"),
                Triple(at("2026-11-03T20:00"), at("2026-11-04T20:00"), "baseline"),
                Triple(at("2026-11-06T00:00"), at("2026-11-06T20:00"), "treatment"),
                Triple(at("2026-11-06T20:00"), at("2026-11-07T20:00"), "treatment")
            ),
            periods.summary()
        )
    }

    @Test
    fun invalidRatingTimeIsTreatedLikeNoSettings() {
        val warnings = mutableListOf<String>()
        val periods = planScreenshotPeriods(
            at("2026-11-03T14:00"), listOf(phase("baseline", 0, 2, "8 pm")), zone, warn = { warnings.add(it) }
        )

        assertTrue(periods.isEmpty())
        assertEquals(1, warnings.size)
    }

    @Test
    fun withoutPhasesNothingIsPlanned() {
        assertTrue(plan("2026-11-03T14:00").isEmpty())
    }

    @Test
    fun ratingTimeFollowsDaylightSavingTime() {
        // clocks go back on 2026-10-25 in Berlin, so the second period is 25 hours long
        val periods = plan("2026-10-24T14:00", phase("baseline", 0, 2, "20:00"))

        assertEquals(at("2026-10-25T20:00"), periods[1].end)
        assertEquals(25 * 3_600_000L, periods[1].end - periods[1].start)
    }
}
