package de.mimuc.senseeverything.db.models

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDataDao {
    @get:Query("SELECT * FROM logdata")
    val all: List<LogData>

    @Insert
    fun insertAll(vararg logDatas: LogData?)

    @Query("SELECT * FROM logdata WHERE synced = FALSE AND timestamp <= :cutoffTimestamp ORDER BY timestamp ASC LIMIT :n")
    fun getNextNUnsyncedBefore(n: Int, cutoffTimestamp: Long): List<LogData>

    @get:Query("SELECT COUNT(*) FROM logdata WHERE synced = FALSE")
    val unsyncedCount: Long

    @Query("SELECT COUNT(*) FROM logdata WHERE synced = FALSE AND timestamp <= :cutoffTimestamp")
    fun getUnsyncedCountBefore(cutoffTimestamp: Long): Long

    @Query("SELECT COUNT(*) FROM logdata WHERE synced = FALSE AND timestamp <= :cutoffTimestamp")
    fun getUnsyncedCountBeforeFlow(cutoffTimestamp: Long): Flow<Long>

    @get:Query("SELECT * FROM logdata ORDER BY timestamp DESC LIMIT 1")
    val lastItem: LogData?

    @Update
    fun updateLogData(vararg logData: LogData?)

    @Delete
    fun deleteLogData(vararg logData: LogData?)

    @Query("DELETE FROM logdata")
    fun deleteAll()
}
