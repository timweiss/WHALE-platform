package de.mimuc.senseeverything.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MemorySnapshotTest {
    private val mb = 1024L * 1024L

    private fun snapshot(
        availBytes: Long = 1_024 * mb,
        totalBytes: Long = 4_096 * mb,
        rssKb: Long? = 204_800
    ) = MemorySnapshot.of(
        process = "de.mimuc.whale:remote",
        reason = "sampling",
        availBytes = availBytes,
        totalBytes = totalBytes,
        thresholdBytes = 200 * mb,
        lowMemory = false,
        importance = 125,
        lastTrimLevel = 0,
        rssKb = rssKb,
        nativeHeapBytes = 51_200 * 1024L
    )

    @Test
    fun convertsToMegabytesAndPercent() {
        val s = snapshot()
        assertEquals(1024L, s.availMb)
        assertEquals(4096L, s.totalMb)
        assertEquals(25, s.availPct)
        assertEquals(200L, s.thresholdMb)
        assertEquals(51_200L, s.nativeHeapKb)
    }

    @Test
    fun roundsAvailPct() {
        // 1/3 of memory available
        assertEquals(33, snapshot(availBytes = 1_000 * mb, totalBytes = 3_000 * mb).availPct)
    }

    @Test
    fun availPctIsNullWhenTotalMemoryIsZero() {
        assertNull(snapshot(totalBytes = 0).availPct)
    }

    @Test
    fun encodesJson() {
        assertEquals(
            "{\"process\":\"de.mimuc.whale:remote\",\"reason\":\"sampling\",\"availMb\":1024," +
                "\"totalMb\":4096,\"availPct\":25,\"lowMemory\":false,\"thresholdMb\":200," +
                "\"importance\":125,\"lastTrimLevel\":0,\"rssKb\":204800,\"nativeHeapKb\":51200}",
            snapshot().toJson()
        )
    }

    @Test
    fun encodesMissingRssAsNull() {
        assertTrue(snapshot(rssKb = null).toJson().contains("\"rssKb\":null"))
    }

    @Test
    fun parsesVmRssFromProcStatus() {
        val status = "Name:\tsenseeverything\nVmSize:\t 9000000 kB\nVmRSS:\t  204800 kB\nThreads:\t42\n"
        assertEquals(204_800L, MemorySnapshot.parseVmRssKb(status))
    }

    @Test
    fun parseVmRssReturnsNullWhenMissingOrMalformed() {
        assertNull(MemorySnapshot.parseVmRssKb("Name:\tfoo\n"))
        assertNull(MemorySnapshot.parseVmRssKb("VmRSS:\t  n/a kB\n"))
    }

    @Test
    fun hasIntervalPassed() {
        val hour = 60 * 60 * 1000L
        assertTrue(MemorySnapshot.hasIntervalPassed(now = 5, lastAt = null, minIntervalMs = hour))
        assertFalse(MemorySnapshot.hasIntervalPassed(now = hour, lastAt = 1, minIntervalMs = hour))
        assertTrue(MemorySnapshot.hasIntervalPassed(now = hour + 1, lastAt = 1, minIntervalMs = hour))
    }
}
