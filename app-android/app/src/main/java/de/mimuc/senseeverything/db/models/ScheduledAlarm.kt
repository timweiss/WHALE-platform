package de.mimuc.senseeverything.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.mimuc.senseeverything.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@Entity(tableName = "scheduled_alarms")
data class ScheduledAlarm(
    @PrimaryKey(autoGenerate = true) val uid: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "receiver") val receiver: String,
    @ColumnInfo(name = "identifier") val identifier: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "request_code") val requestCode: Int,
    @ColumnInfo(name = "pending_questionnaire_id") var pendingQuestionnaireId: UUID? = null
) {
    companion object {
        fun createEntry(
            receiver: String,
            identifier: String,
            timestamp: Long,
            pendingQuestionnaireId: UUID? = null
        ): ScheduledAlarm {
            return ScheduledAlarm(
                0,
                System.currentTimeMillis(),
                receiver,
                identifier,
                timestamp,
                generateRequestCode(receiver, identifier),
                pendingQuestionnaireId
            )
        }

        fun generateRequestCode(receiver: String, identifier: String): Int {
            return (receiver + identifier).hashCode()
        }

        suspend fun getOrCreateScheduledAlarm(
            database: AppDatabase,
            receiver: String,
            identifier: String,
            timestamp: Long,
            pendingQuestionnaireId: UUID? = null
        ): ScheduledAlarm = withContext(Dispatchers.IO) {
            database.scheduledAlarmDao().getByIdentifier(receiver, identifier)
                ?: createEntry(receiver, identifier, timestamp, pendingQuestionnaireId).let { entry ->
                    val uid = database.scheduledAlarmDao().insert(entry)
                    entry.copy(uid = uid)
                }
        }
    }
}