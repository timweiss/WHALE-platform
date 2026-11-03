package de.mimuc.senseeverything.service.accessibility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ScreenshotPeriodsTest {
    @TempDir
    lateinit var root: File

    private val app = "com.instagram.android"

    /** two consecutive periods and a gap until the third */
    private var schedule: List<ScreenshotPeriod>? = listOf(
        ScreenshotPeriod(100L, 200L, "baseline", mapOf(app to 10)),
        ScreenshotPeriod(200L, 300L, "treatment", mapOf(app to 3)),
        ScreenshotPeriod(400L, 500L, "treatment", mapOf(app to 3))
    )
    private var scheduleReads = 0
    private val infos = mutableListOf<String>()

    private fun periods() = ScreenshotPeriods(
        root,
        schedule = { scheduleReads++; schedule },
        infoLog = { infos.add(it) },
        warnLog = {}
    )

    @Test
    fun opensThePlannedPeriod() {
        val running = periods().current(150L)

        assertEquals(schedule!![0], running?.period)
        assertEquals(200L, running?.store?.periodEnd)
        assertEquals(listOf("Opened period: start=100 end=200 sampleSizes={$app=10} phase=baseline"), infos)
    }

    @Test
    fun runningPeriodNeedsNoScheduleLookup() {
        val periods = periods()
        val running = periods.current(100L)

        assertSame(running, periods.current(150L))
        assertSame(running, periods.current(199L))
        assertEquals(1, scheduleReads)
    }

    @Test
    fun nextPeriodOpensAtTheRatingTimeAndTheEndedOneIsReported() {
        val periods = periods()
        periods.current(150L)?.store?.setItemsSeen(app, 2)

        val next = periods.current(200L)
        assertEquals(schedule!![1], next?.period)
        assertEquals(300L, next?.store?.periodEnd)
        assertTrue(infos.contains(
            "sampling summary: periodEnd=200 package=$app itemsSeen=2 itemsDropped=0 samples=0 complete=true phase=baseline"
        ))
    }

    @Test
    fun nothingIsSampledOutsideOfPeriods() {
        val periods = periods()

        assertNull(periods.current(50L))
        assertNull(periods.current(350L))
        assertNull(periods.current(600L))
        assertTrue(ScreenshotStore.periodEnds(root).isEmpty())
    }

    @Test
    fun periodBeforeAGapIsReportedOnceWhenItEnds() {
        val periods = periods()
        periods.current(250L)?.store?.setItemsSeen(app, 1)
        infos.clear()

        assertNull(periods.current(300L))
        assertNull(periods.current(350L))
        assertEquals(
            listOf("sampling summary: periodEnd=300 package=$app itemsSeen=1 itemsDropped=0 samples=0 complete=true phase=treatment"),
            infos
        )
    }

    @Test
    fun scheduleLoadedLaterIsPickedUp() {
        val loaded = schedule
        schedule = null
        val periods = periods()
        assertNull(periods.current(150L))

        schedule = loaded
        assertEquals(200L, periods.current(150L)?.period?.end)
    }

    @Test
    fun restartContinuesTheRunningPeriod() {
        periods().current(150L)?.store?.setItemsSeen(app, 4)
        infos.clear()

        // a new instance, as after a process restart
        val reopened = periods().current(160L)
        assertEquals(4, reopened?.store?.reservoir(app)?.itemsSeen)
        assertEquals(listOf("Reopened period: start=100 end=200 sampleSizes={$app=10} phase=baseline"), infos)
    }
}
