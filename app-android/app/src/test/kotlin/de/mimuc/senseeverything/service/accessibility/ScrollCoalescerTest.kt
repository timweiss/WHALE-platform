package de.mimuc.senseeverything.service.accessibility

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScrollCoalescerTest {
    private var now = 1_000L
    private val coalescer = ScrollCoalescer(windowMs = 500L, clock = { now })

    @Test
    fun firstScrollIsRecorded() {
        assertTrue(coalescer.shouldRecord("com.instagram.android", "RecyclerView", 1))
    }

    @Test
    fun continuousGestureIsRecordedOnce() {
        assertTrue(coalescer.shouldRecord("app", "List", 1))
        // 10 events per second for 3 seconds, as delivered with notificationTimeout = 100
        repeat(30) {
            now += 100
            assertFalse(coalescer.shouldRecord("app", "List", 1))
        }
    }

    @Test
    fun pauseStartsNewGesture() {
        assertTrue(coalescer.shouldRecord("app", "List", 1))
        now += 499
        assertFalse(coalescer.shouldRecord("app", "List", 1))
        now += 500
        assertTrue(coalescer.shouldRecord("app", "List", 1))
    }

    @Test
    fun differentTargetIsRecorded() {
        assertTrue(coalescer.shouldRecord("app", "List", 1))
        now += 100
        assertTrue(coalescer.shouldRecord("app", "HorizontalPager", 1))
        now += 100
        assertTrue(coalescer.shouldRecord("other.app", "HorizontalPager", 1))
    }

    @Test
    fun differentWindowIsRecorded() {
        assertTrue(coalescer.shouldRecord("app", "List", 1))
        now += 100
        assertTrue(coalescer.shouldRecord("app", "List", 2))
        now += 100
        assertFalse(coalescer.shouldRecord("app", "List", 2))
    }

    @Test
    fun resetEndsGesture() {
        assertTrue(coalescer.shouldRecord("app", "List", 1))
        now += 100
        coalescer.reset()
        assertTrue(coalescer.shouldRecord("app", "List", 1))
        now += 100
        assertFalse(coalescer.shouldRecord("app", "List", 1))
    }

    @Test
    fun handlesNullNames() {
        assertTrue(coalescer.shouldRecord(null, null, -1))
        now += 100
        assertFalse(coalescer.shouldRecord(null, null, -1))
    }
}
