package de.mimuc.senseeverything.service.accessibility

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScrollCoalescerTest {
    private var now = 1_000L
    private val coalescer = ScrollCoalescer(windowMs = 500L, clock = { now })

    @Test
    fun firstScrollIsRecorded() {
        assertTrue(coalescer.shouldRecord("com.instagram.android", "RecyclerView"))
    }

    @Test
    fun continuousGestureIsRecordedOnce() {
        assertTrue(coalescer.shouldRecord("app", "List"))
        // 10 events per second for 3 seconds, as delivered with notificationTimeout = 100
        repeat(30) {
            now += 100
            assertFalse(coalescer.shouldRecord("app", "List"))
        }
    }

    @Test
    fun pauseStartsNewGesture() {
        assertTrue(coalescer.shouldRecord("app", "List"))
        now += 499
        assertFalse(coalescer.shouldRecord("app", "List"))
        now += 500
        assertTrue(coalescer.shouldRecord("app", "List"))
    }

    @Test
    fun differentTargetIsRecorded() {
        assertTrue(coalescer.shouldRecord("app", "List"))
        now += 100
        assertTrue(coalescer.shouldRecord("app", "HorizontalPager"))
        now += 100
        assertTrue(coalescer.shouldRecord("other.app", "HorizontalPager"))
    }

    @Test
    fun handlesNullNames() {
        assertTrue(coalescer.shouldRecord(null, null))
        now += 100
        assertFalse(coalescer.shouldRecord(null, null))
    }
}
