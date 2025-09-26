package de.mimuc.senseeverything.db.models

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScheduledAlarmDao {
    @Query("SELECT * FROM scheduled_alarms WHERE receiver = :receiver AND identifier = :identifier")
    fun getByIdentifier(receiver: String, identifier: String): ScheduledAlarm?

    @Query("SELECT * FROM scheduled_alarms WHERE receiver = :receiver")
    fun getByReceiver(receiver: String): List<ScheduledAlarm>

    @Insert
    fun insert(scheduledAlarm: ScheduledAlarm): Long

    @Delete
    fun delete(scheduledAlarm: ScheduledAlarm)

    @Query("DELETE FROM scheduled_alarms")
    fun deleteAll()
}