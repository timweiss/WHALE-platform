package de.mimuc.senseeverything.service.accessibility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ScreenshotSampleReadingTest {
    @TempDir
    lateinit var root: File

    private val app = "com.instagram.android"
    private val end = 1_000L

    private fun ScreenshotStore.sample(slot: Int, snapshotId: String, timestamp: Long) =
        commit(app, slot, newImageFile(app).apply { writeText("image") }, timestamp, "signature", snapshotId)

    @Test
    fun listsTheFinalSampleOldestFirst() {
        val store = ScreenshotStore(root, end, log = {})
        store.sample(0, "replaced", 1L)
        store.sample(1, "older", 2L)
        store.sample(0, "newer", 3L)

        val row = ScreenshotSampleReading.of(end, ScreenshotStore.readPeriod(root, end)).toLogData(now = 5_000L)

        assertEquals("ScreenshotSample", row.sensorName)
        assertEquals(5_000L, row.timestamp)
        assertEquals(
            """{"periodEnd":1000,"samples":[""" +
                """{"snapshotId":"older","packageName":"$app","timestamp":2},""" +
                """{"snapshotId":"newer","packageName":"$app","timestamp":3}]}""",
            row.data
        )
        assertFalse(row.hasFile)
    }

    @Test
    fun periodWithoutSamplesIsListedAsEmpty() {
        val row = ScreenshotSampleReading.of(end, ScreenshotStore.readPeriod(root, end)).toLogData(now = 5_000L)

        assertEquals("""{"periodEnd":1000,"samples":[]}""", row.data)
    }

    @Test
    fun localIdFollowsFromThePeriod() {
        fun localId(periodEnd: Long, now: Long) = ScreenshotSampleReading(periodEnd, emptyList()).toLogData(now).localId

        assertEquals(localId(end, now = 1L), localId(end, now = 2L))
        assertNotEquals(localId(end, now = 1L), localId(end + 1, now = 1L))
    }
}
