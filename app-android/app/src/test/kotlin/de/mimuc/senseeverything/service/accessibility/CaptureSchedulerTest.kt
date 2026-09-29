package de.mimuc.senseeverything.service.accessibility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CaptureSchedulerTest {
    /** Stands in for the worker's message queue. */
    private val queue = ArrayList<Runnable>()
    private val log = ArrayList<String>()
    private var onCapture: (Long) -> Unit = {}
    private val scheduler = CaptureScheduler(
        post = { queue.add(it) },
        remove = { r -> queue.removeAll { it === r } },
        capture = {
            log.add("capture@$it")
            onCapture(it)
        }
    )

    private fun interaction(name: String) = Runnable { log.add(name) }

    private fun runNext() = queue.removeAt(0).run()

    private fun runAll() {
        while (queue.isNotEmpty()) runNext()
    }

    @Test
    fun triggerJoinsQueuedCaptureAndUsesLatestTime() {
        assertTrue(scheduler.requestCapture(100))
        assertFalse(scheduler.requestCapture(200))
        runAll()

        assertEquals(listOf("capture@200"), log)
    }

    @Test
    fun interactionWithoutQueuedCaptureIsJustPosted() {
        assertFalse(scheduler.postInteraction(100, interaction("tap")))
        assertTrue(scheduler.requestCapture(200))
        runAll()

        assertEquals(listOf("tap", "capture@200"), log)
    }

    @Test
    fun queuedCaptureIsMovedBehindInteraction() {
        scheduler.requestCapture(100)
        assertTrue(scheduler.postInteraction(150, interaction("tap")))
        runAll()

        // the capture can only read the tree after the tap, so it is not dated before it
        assertEquals(listOf("tap", "capture@150"), log)
    }

    @Test
    fun laterTriggerJoinsTheMovedCapture() {
        scheduler.requestCapture(100)
        scheduler.postInteraction(150, interaction("tap"))
        assertFalse(scheduler.requestCapture(300))
        runAll()

        assertEquals(listOf("tap", "capture@300"), log)
    }

    @Test
    fun interactionsKeepTheirOrder() {
        scheduler.requestCapture(100)
        scheduler.postInteraction(150, interaction("tap1"))
        scheduler.postInteraction(160, interaction("tap2"))
        runAll()

        assertEquals(listOf("tap1", "tap2", "capture@160"), log)
    }

    @Test
    fun replacedCaptureAlreadyTakenFromQueueDoesNothing() {
        scheduler.requestCapture(100)
        // the worker takes the capture from the queue but has not run it yet ...
        val taken = queue.removeAt(0)
        // ... while an interaction moves it behind itself
        scheduler.postInteraction(150, interaction("tap"))
        taken.run()
        runAll()

        assertEquals(listOf("tap", "capture@150"), log)
    }

    @Test
    fun triggerDuringRunningCaptureQueuesNewCapture() {
        onCapture = { time -> if (time == 100L) assertTrue(scheduler.requestCapture(200)) }
        scheduler.requestCapture(100)
        runAll()

        assertEquals(listOf("capture@100", "capture@200"), log)
    }
}
