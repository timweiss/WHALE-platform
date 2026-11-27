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

    @Insert
    fun insert(logData: LogData): Long

    @Query("SELECT EXISTS (SELECT 1 FROM logdata WHERE sensorName = :sensorName AND localId = :localId)")
    fun exists(sensorName: String, localId: String): Boolean

    /** Adds the row together with its flag, unless the sensor already has a row with that localId. */
    @Transaction
    fun insertOnceWithFlag(logData: LogData, flag: String) {
        if (exists(logData.sensorName!!, logData.localId!!)) return
        insertFlags(LogDataFlag(insert(logData).toInt(), flag))
    }

    // LogDataFlag rows go via ON DELETE CASCADE
    @Query("DELETE FROM logdata WHERE sensorName = :sensorName")
    fun deleteBySensor(sensorName: String)

    // Rows with a file are uploaded one by one, see getNextUnsyncedWithFileBefore.
    // id breaks ties between rows with the same timestamp, so batches have a defined order
    @Query("SELECT logdata.* FROM logdata JOIN log_data_flag_status fs ON logdata.id=fs.logDataId WHERE fs.flagsSatisfied = TRUE AND synced = FALSE AND timestamp <= :cutoffTimestamp AND hasFile = FALSE ORDER BY timestamp ASC, id ASC LIMIT :n")
    fun getNextNUnsyncedBeforeWithFlags(n: Int, cutoffTimestamp: Long): List<LogData>

    @Query("SELECT logdata.* FROM logdata JOIN log_data_flag_status fs ON logdata.id=fs.logDataId WHERE fs.flagsSatisfied = TRUE AND synced = FALSE AND timestamp <= :cutoffTimestamp AND hasFile = TRUE ORDER BY timestamp ASC, id ASC LIMIT 1")
    fun getNextUnsyncedWithFileBefore(cutoffTimestamp: Long): LogData?

    @Query("SELECT filePath FROM logdata WHERE sensorName = :sensorName AND filePath IS NOT NULL")
    fun getFilePaths(sensorName: String): List<String>

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

    // Content extraction: the oldest row of a sensor that doesn't have the flag yet
    @Query("""
        SELECT * FROM logdata WHERE sensorName = :sensorName
          AND NOT EXISTS (SELECT 1 FROM LogDataFlag f WHERE f.logDataId = logdata.id AND f.flag = :flag)
        ORDER BY timestamp ASC, id ASC LIMIT 1
    """)
    fun nextWithoutFlag(sensorName: String, flag: String): LogData?

    @Query("""
        SELECT COUNT(*) FROM logdata WHERE sensorName = :sensorName
          AND NOT EXISTS (SELECT 1 FROM LogDataFlag f WHERE f.logDataId = logdata.id AND f.flag = :flag)
    """)
    fun countWithoutFlag(sensorName: String, flag: String): Int

    @Query("INSERT OR IGNORE INTO LogDataFlag (logDataId, flag) SELECT id, :flag FROM logdata WHERE sensorName = :sensorName")
    fun flagAll(sensorName: String, flag: String)

    /** New readings and the flag of the row they came from, together or not at all. */
    @Transaction
    fun insertAndFlag(readings: List<LogData>, rowId: Int, flag: String) {
        readings.forEach { insert(it) }
        insertFlags(LogDataFlag(rowId, flag))
    }

    // Rows whose sensor has a policy, still miss a required flag, and are older than the policy's
    // clearAfterDays. Sensors without a policy are never touched. LogDataFlag rows go via ON DELETE CASCADE.
    @Query("""
        DELETE FROM logdata WHERE id IN (
            SELECT l.id FROM logdata l
            JOIN sensor_upload_policy p ON p.sensorName = l.sensorName
            JOIN log_data_flag_status fs ON fs.logDataId = l.id
            WHERE fs.flagsSatisfied = FALSE
              AND l.timestamp < :now - p.clearAfterDays * 86400000
        )
    """)
    fun deleteExpiredUnsatisfied(now: Long): Int
}
