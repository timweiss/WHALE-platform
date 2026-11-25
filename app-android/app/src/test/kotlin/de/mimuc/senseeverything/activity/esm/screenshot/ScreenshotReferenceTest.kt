package de.mimuc.senseeverything.activity.esm.screenshot

import de.mimuc.senseeverything.service.accessibility.ScreenshotRatingContext
import de.mimuc.senseeverything.service.accessibility.ScreenshotStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.ZoneOffset
import java.util.Locale

class ScreenshotReferenceTest {
    @TempDir
    lateinit var root: File

    private fun commit(periodEnd: Long, app: String, slot: Int, timestamp: Long, snapshotId: String) {
        val store = ScreenshotStore(root, periodEnd, log = {})
        store.commit(app, slot, store.newImageFile(app).apply { writeText("image") }, timestamp, "abc", snapshotId)
    }

    @Test
    fun listsOnlyTheRatedPeriodOldestFirst() {
        commit(1_000L, "app.a", 0, 30L, "late")
        commit(1_000L, "app.b", 0, 10L, "early")
        commit(2_000L, "app.a", 0, 1_500L, "other period")

        val screenshots = ScreenshotReference.ofPeriod(root, 1_000L)
        assertEquals(listOf("early", "late"), screenshots.map { it.snapshotId })
        assertEquals(listOf("app.b", "app.a"), screenshots.map { it.packageName })
        assertEquals(emptyList<ScreenshotReference>(), ScreenshotReference.ofPeriod(root, 3_000L))
    }

    @Test
    fun labelShowsTimeAndApp() {
        val screenshot = ScreenshotReference("id", "com.instagram.android", 1_764_000_000_000L, File("image.jpg"))

        assertEquals("24.11.25, 16:00 · com.instagram.android", screenshot.label(ZoneOffset.UTC, Locale.GERMANY))
    }

    @Test
    fun contextSurvivesRoundTripAndIgnoresOtherModules() {
        assertEquals(ScreenshotRatingContext(5L), ScreenshotRatingContext.fromJson(ScreenshotRatingContext(5L).toJson()))
        assertNull(ScreenshotRatingContext.fromJson("""{"other":1}"""))
        assertNull(ScreenshotRatingContext.fromJson(null))
    }
}
