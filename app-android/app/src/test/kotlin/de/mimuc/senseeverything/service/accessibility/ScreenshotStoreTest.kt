package de.mimuc.senseeverything.service.accessibility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ScreenshotStoreTest {
    @TempDir
    lateinit var root: File

    private val periodEnd = 1_000L
    private val app = "com.instagram.android"

    private fun open() = ScreenshotStore(root, periodEnd, log = {})

    private fun capture(store: ScreenshotStore): File =
        store.newImageFile(app).apply { writeText("image") }

    @Test
    fun countAndSlotsSurviveRestart() {
        val store = open()
        val image = capture(store)
        store.setItemsSeen(app, 7)
        store.commit(app, 2, image, timestamp = 42L, signature = "abc")

        val reservoir = open().reservoir(app)
        assertEquals(7, reservoir.itemsSeen)
        assertNull(reservoir.slots[0])
        assertEquals(ScreenshotStore.Sample(image.name, 42L, "abc"), reservoir.slots[2])
        assertTrue(image.exists())
    }

    @Test
    fun replacingDeletesTheOldImage() {
        val store = open()
        val first = capture(store)
        store.commit(app, 0, first, 1L, "a")
        val second = capture(store)
        store.commit(app, 0, second, 2L, "b")

        assertFalse(first.exists())
        assertEquals(second.name, store.reservoir(app).slots[0]?.file)
    }

    @Test
    fun uncommittedImagesAreDeletedOnLoad() {
        val store = open()
        store.setItemsSeen(app, 1)
        val inFlight = capture(store)

        open().reservoir(app)
        assertFalse(inFlight.exists())
    }

    @Test
    fun unreadableStateStartsEmptyAndDeletesImages() {
        val store = open()
        val image = capture(store)
        store.commit(app, 0, image, 1L, "a")
        File(image.parentFile, "state.json").writeText("{broken")

        assertEquals(ScreenshotStore.Reservoir(), open().reservoir(app))
        assertFalse(image.exists())
    }

    @Test
    fun periodsAreListedAndDeleted() {
        open().setItemsSeen(app, 1)
        ScreenshotStore(root, 500L, log = {}).setItemsSeen(app, 1)

        assertEquals(listOf(500L, 1_000L), ScreenshotStore.periodEnds(root))
        ScreenshotStore.deletePeriod(root, 500L, log = {})
        assertEquals(listOf(1_000L), ScreenshotStore.periodEnds(root))
    }

    @Test
    fun droppedItemsMoveOutOfTheStream() {
        val store = open()
        store.setItemsSeen(app, 5)
        store.recordDropped(app)

        val reservoir = open().reservoir(app)
        assertEquals(4, reservoir.itemsSeen)
        assertEquals(1, reservoir.itemsDropped)
    }

    @Test
    fun endedPeriodsAreReportedOnce() {
        val store = open()
        store.setItemsSeen(app, 5)
        store.recordDropped(app)
        store.commit(app, 0, capture(store), 1L, "a")
        val lines = mutableListOf<String>()

        ScreenshotStore.reportEndedPeriods(root, now = periodEnd - 1, log = { lines.add(it) })
        assertTrue(lines.isEmpty())

        ScreenshotStore.reportEndedPeriods(root, now = periodEnd, log = { lines.add(it) })
        ScreenshotStore.reportEndedPeriods(root, now = periodEnd, log = { lines.add(it) })
        ScreenshotStore.deletePeriod(root, periodEnd, log = { lines.add(it) })
        assertEquals(
            listOf("sampling summary: periodEnd=1000 package=$app itemsSeen=4 itemsDropped=1 samples=1 complete=true"),
            lines
        )
    }

    @Test
    fun readAllListsCommittedSamplesWithoutDeletingOthers() {
        val store = open()
        val committed = capture(store)
        store.commit(app, 1, committed, 42L, "abc")
        val inFlight = capture(store)

        val samples = ScreenshotStore.readAll(root)
        assertEquals(
            listOf(ScreenshotStore.StoredSample(periodEnd, app, 1, ScreenshotStore.Sample(committed.name, 42L, "abc"), committed)),
            samples
        )
        assertTrue(inFlight.exists())
    }
}
