package de.mimuc.senseeverything.service.accessibility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ScreenshotPeriodEndTest {
    @TempDir
    lateinit var root: File

    private val app = "com.instagram.android"
    private val grace = SCREENSHOT_DELETE_GRACE_MS
    private val hour = 3_600_000L

    private fun period(start: Long, end: Long) = ScreenshotPeriod(start, end, "baseline", mapOf(app to 10))

    @Test
    fun periodIsKeptWhileItsRatingIsOpen() {
        val ratings = mapOf(hour to listOf(ScreenshotRating(completed = false, validUntil = 3 * hour)))

        assertEquals(emptyList<Long>(), screenshotPeriodsToDelete(listOf(hour), ratings, now = 2 * hour))
        // expired
        assertEquals(listOf(hour), screenshotPeriodsToDelete(listOf(hour), ratings, now = 3 * hour))
    }

    @Test
    fun ratedPeriodIsDeleted() {
        val ratings = mapOf(hour to listOf(ScreenshotRating(completed = true, validUntil = 3 * hour)))

        assertEquals(listOf(hour), screenshotPeriodsToDelete(listOf(hour), ratings, now = 2 * hour))
    }

    @Test
    fun periodWithoutRatingIsDeletedAfterTheGraceTime() {
        // e.g. no samples, no questionnaire for the event, or the rating was uploaded and removed
        assertEquals(emptyList<Long>(), screenshotPeriodsToDelete(listOf(hour), emptyMap(), now = hour + grace - 1))
        assertEquals(listOf(hour), screenshotPeriodsToDelete(listOf(hour), emptyMap(), now = hour + grace))
    }

    @Test
    fun ratingWithoutExpiryKeepsThePeriodUntilRated() {
        val ratings = mapOf(hour to listOf(ScreenshotRating(completed = false, validUntil = -1L)))

        assertEquals(emptyList<Long>(), screenshotPeriodsToDelete(listOf(hour), ratings, now = 100 * hour))
    }

    @Test
    fun nextAlarmIsTheNextPeriodEnd() {
        val schedule = listOf(period(0, hour), period(hour, 5 * hour))
        val ratings = mapOf(hour to listOf(ScreenshotRating(completed = false, validUntil = 6 * hour)))

        assertEquals(5 * hour, nextScreenshotAlarm(schedule, listOf(hour), ratings, now = 2 * hour))
    }

    @Test
    fun afterTheLastPeriodTheAlarmWaitsForTheRatingToExpire() {
        val schedule = listOf(period(0, hour))
        val ratings = mapOf(hour to listOf(ScreenshotRating(completed = false, validUntil = 3 * hour)))

        assertEquals(3 * hour, nextScreenshotAlarm(schedule, listOf(hour), ratings, now = 2 * hour))
    }

    @Test
    fun afterTheLastPeriodTheAlarmWaitsForTheGraceTime() {
        val schedule = listOf(period(0, hour))

        assertEquals(hour + grace, nextScreenshotAlarm(schedule, listOf(hour), emptyMap(), now = hour))
        assertNull(nextScreenshotAlarm(schedule, emptyList(), emptyMap(), now = hour + grace))
    }

    @Test
    fun ratingRequestedMarkerAndSampleCount() {
        val store = ScreenshotStore(root, hour, log = {})
        val image = store.newImageFile(app).apply { writeText("image") }
        store.commit(app, 0, image, timestamp = 42L, signature = "abc")

        assertEquals(1, ScreenshotStore.sampleCount(root, hour))
        assertEquals(0, ScreenshotStore.sampleCount(root, 2 * hour))
        assertFalse(ScreenshotStore.isRatingRequested(root, hour))
        ScreenshotStore.markRatingRequested(root, hour)
        assertTrue(ScreenshotStore.isRatingRequested(root, hour))
        // the marker isn't mistaken for an app
        assertEquals(1, ScreenshotStore.readAll(root).size)
    }
}
