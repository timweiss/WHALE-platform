package de.mimuc.senseeverything.db.models

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LogDataDao {
    @get:Query("SELECT * FROM logdata")
    val all: List<LogData>

    @Insert
    fun insertAll(vararg logDatas: LogData?)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertFlags(vararg flags: LogDataFlag)

    // id breaks ties between rows with the same timestamp, so batches have a defined order
    @Query("SELECT logdata.* FROM logdata JOIN log_data_flag_status fs ON logdata.id=fs.logDataId WHERE fs.flagsSatisfied = TRUE AND synced = FALSE AND timestamp <= :cutoffTimestamp ORDER BY timestamp ASC, id ASC LIMIT :n")
    fun getNextNUnsyncedBeforeWithFlags(n: Int, cutoffTimestamp: Long): List<LogData>

    @get:Query("SELECT COUNT(*) FROM logdata WHERE synced = FALSE")
    val unsyncedCount: Long

    // For upload progress
    @Query("SELECT COUNT(*) FROM logdata JOIN log_data_flag_status fs ON logdata.id=fs.logDataId WHERE fs.flagsSatisfied = TRUE AND synced = FALSE AND timestamp <= :cutoffTimestamp")
    fun getUnsyncedCountBefore(cutoffTimestamp: Long): Long

    @Query("SELECT EXISTS (SELECT 1 FROM logdata JOIN log_data_flag_status fs ON logdata.id=fs.logDataId WHERE fs.flagsSatisfied = TRUE AND synced = FALSE AND timestamp <= :cutoffTimestamp)")
    fun hasUnsyncedBefore(cutoffTimestamp: Long): Boolean

    @Query("SELECT EXISTS (SELECT 1 FROM logdata JOIN log_data_flag_status fs ON logdata.id=fs.logDataId WHERE fs.flagsSatisfied = TRUE AND synced = FALSE AND timestamp <= :cutoffTimestamp)")
    fun hasUnsyncedBeforeFlow(cutoffTimestamp: Long): Flow<Boolean>

    @get:Query("SELECT * FROM logdata ORDER BY timestamp DESC LIMIT 1")
    val lastItem: LogData?

    @Update
    fun updateLogData(vararg logData: LogData?)

    @Delete
    fun deleteLogData(vararg logData: LogData?)

    @Query("DELETE FROM logdata")
    fun deleteAll()
}
