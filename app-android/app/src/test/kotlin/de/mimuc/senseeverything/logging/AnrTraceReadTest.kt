package de.mimuc.senseeverything.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class AnrTraceReadTest {
    private val max = ProcessDiagnostics.MAX_ANR_TRACE_BYTES

    private fun read(input: InputStream) = ProcessDiagnostics.readAnrTraceStream(input)

    private fun bytes(size: Int) = ByteArray(size) { ('a' + it % 26).code.toByte() }

    /** Returns at most [chunk] bytes per read, like a pipe or socket may. */
    private class ShortReadStream(data: ByteArray, private val chunk: Int) : InputStream() {
        private val inner = ByteArrayInputStream(data)
        var closed = false

        override fun read(): Int = inner.read()
        override fun read(b: ByteArray, off: Int, len: Int): Int = inner.read(b, off, minOf(len, chunk))
        override fun close() {
            closed = true
        }
    }

    @Test
    fun emptyStreamGivesEmptyTrace() {
        val (truncated, trace) = read(ByteArrayInputStream(ByteArray(0)))
        assertFalse(truncated)
        assertEquals("", trace)
    }

    @Test
    fun shortTraceIsKeptWithoutPadding() {
        val (truncated, trace) = read(ByteArrayInputStream("\"main\" prio=5 tid=1 Blocked".toByteArray()))
        assertFalse(truncated)
        assertEquals("\"main\" prio=5 tid=1 Blocked", trace)
    }

    @Test
    fun traceOfExactlyMaxIsNotTruncated() {
        val (truncated, trace) = read(ByteArrayInputStream(bytes(max)))
        assertFalse(truncated)
        assertEquals(max, trace.length)
    }

    @Test
    fun traceOneByteOverMaxIsTruncated() {
        val data = bytes(max + 1)
        val (truncated, trace) = read(ByteArrayInputStream(data))
        assertTrue(truncated)
        assertEquals(String(data, 0, max, Charsets.UTF_8), trace)
    }

    @Test
    fun shortReadsLeaveNoGaps() {
        val data = bytes(10_000)
        val (truncated, trace) = read(ShortReadStream(data, chunk = 7))
        assertFalse(truncated)
        assertEquals(String(data, Charsets.UTF_8), trace)
    }

    @Test
    fun closesTheStream() {
        val stream = ShortReadStream(bytes(100), chunk = 1024)
        read(stream)
        assertTrue(stream.closed)
    }
}
