package de.mimuc.senseeverything.db.models

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import java.util.UUID

@Dao
interface ScheduledAlarmDao {
    @Query("SELECT * FROM scheduled_alarms WHERE receiver = :receiver AND identifier = :identifier")
    fun getByIdentifier(receiver: String, identifier: String): ScheduledAlarm?

    @Query("SELECT * FROM scheduled_alarms WHERE receiver = :receiver")
    fun getByReceiver(receiver: String): List<ScheduledAlarm>

    @Query("SELECT * FROM scheduled_alarms WHERE timestamp > :timestamp")
    fun getAfterTimestamp(timestamp: Long): List<ScheduledAlarm>

    @Insert
    fun insert(scheduledAlarm: ScheduledAlarm): Long

    @Delete
    fun delete(scheduledAlarm: ScheduledAlarm)

    @Query("DELETE FROM scheduled_alarms")
    fun deleteAll()

    @Query("UPDATE scheduled_alarms SET pending_questionnaire_id = :pendingQuestionnaireId WHERE uid = :uid")
    fun updatePendingQuestionnaireId(uid: Long, pendingQuestionnaireId: UUID?)
}