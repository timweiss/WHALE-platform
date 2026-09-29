package de.mimuc.senseeverything.sensor

import de.mimuc.senseeverything.db.models.LogData
import de.mimuc.senseeverything.db.models.LogDataDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BufferedLogWriterTest {
    private class FakeDao : LogDataDao {
        val inserts = CopyOnWriteArrayList<List<LogData?>>()

        override fun insertAll(vararg logDatas: LogData?) {
            inserts.add(logDatas.toList())
        }

        override val all: List<LogData> get() = inserts.flatten().filterNotNull()
        override fun getNextNUnsyncedBefore(n: Int, cutoffTimestamp: Long): List<LogData> = emptyList()
        override val unsyncedCount: Long get() = 0
        override fun getUnsyncedCountBefore(cutoffTimestamp: Long): Long = 0
        override fun getUnsyncedCountBeforeFlow(cutoffTimestamp: Long): Flow<Long> = emptyFlow()
        override val lastItem: LogData? get() = null
        override fun updateLogData(vararg logData: LogData?) {}
        override fun deleteLogData(vararg logData: LogData?) {}
        override fun deleteAll() {}
    }

    private val dao = FakeDao()
    private val executor = Executors.newSingleThreadScheduledExecutor()

    @AfterEach
    fun tearDown() {
        executor.shutdownNow()
    }

    private fun awaitIdle() {
        // everything is executed in order on the single thread, so a no-op marks the end of the queue
        executor.submit {}.get(5, TimeUnit.SECONDS)
    }

    private fun row(i: Int) = LogData(i.toLong(), "Test", "row $i")

    @Test
    fun flushesInOneTransactionWhenBatchIsFull() {
        val writer = BufferedLogWriter(dao, maxBatchSize = 10, flushIntervalMs = 60_000L, executor = executor)
        repeat(10) { writer.add(row(it)) }
        awaitIdle()

        assertEquals(1, dao.inserts.size)
        assertEquals(10, dao.inserts[0].size)
    }

    @Test
    fun flushesAfterIntervalWhenBatchIsNotFull() {
        val writer = BufferedLogWriter(dao, maxBatchSize = 50, flushIntervalMs = 50L, executor = executor)
        repeat(3) { writer.add(row(it)) }
        Thread.sleep(200)
        awaitIdle()

        assertEquals(listOf(3), dao.inserts.map { it.size })
    }

    @Test
    fun explicitFlushWritesEverythingInOrder() {
        val writer = BufferedLogWriter(dao, maxBatchSize = 4, flushIntervalMs = 60_000L, executor = executor)
        repeat(10) { writer.add(row(it)) }
        writer.flush()
        awaitIdle()

        val timestamps = dao.inserts.flatten().map { it!!.timestamp }
        assertEquals((0L until 10L).toList(), timestamps)
    }

    @Test
    fun blockingFlushHasWrittenRowsWhenItReturns() {
        val writer = BufferedLogWriter(dao, maxBatchSize = 50, flushIntervalMs = 60_000L, executor = executor)
        repeat(3) { writer.add(row(it)) }
        writer.flushBlocking(5_000L)

        assertEquals(3, dao.inserts.flatten().size)
    }

    @Test
    fun flushAllBlockingFlushesLiveWriters() {
        val otherDao = FakeDao()
        val otherExecutor = Executors.newSingleThreadScheduledExecutor()
        try {
            val writer = BufferedLogWriter(dao, maxBatchSize = 50, flushIntervalMs = 60_000L, executor = executor)
            val other = BufferedLogWriter(otherDao, maxBatchSize = 50, flushIntervalMs = 60_000L, executor = otherExecutor)
            writer.add(row(1))
            other.add(row(2))
            BufferedLogWriter.flushAllBlocking(5_000L)

            assertEquals(1, dao.inserts.flatten().size)
            assertEquals(1, otherDao.inserts.flatten().size)
        } finally {
            otherExecutor.shutdownNow()
        }
    }

    @Test
    fun flushAllFlushesLiveWriters() {
        val writer = BufferedLogWriter(dao, maxBatchSize = 50, flushIntervalMs = 60_000L, executor = executor)
        writer.add(row(1))
        BufferedLogWriter.flushAll()
        awaitIdle()

        assertEquals(1, dao.inserts.flatten().size)
    }
}
