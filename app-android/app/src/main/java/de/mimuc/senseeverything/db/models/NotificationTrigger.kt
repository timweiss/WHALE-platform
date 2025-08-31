package de.mimuc.senseeverything.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "notification_trigger")
data class NotificationTrigger(
    @PrimaryKey() var uid: UUID,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "status") var status: NotificationTriggerStatus,
    @ColumnInfo(name = "valid_from") val validFrom: Long,
    @ColumnInfo(name = "priority") val priority: NotificationTriggerPriority,
    @ColumnInfo(name = "time_bucket") val timeBucket: String,
    @ColumnInfo(name = "modality") val modality: NotificationTriggerModality,
    @ColumnInfo(name = "source") val source: NotificationTriggerSource,
    @ColumnInfo(name = "questionnaire_id") val questionnaireId: Long,
    @ColumnInfo(name = "trigger_json") val triggerJson: String,
    @ColumnInfo(name = "planned_at") var plannedAt: Long? = null,
    @ColumnInfo(name = "pushed_at") var pushedAt: Long? = null,
    @ColumnInfo(name = "displayed_at") var displayedAt: Long? = null,
    @ColumnInfo(name = "answered_at") var answeredAt: Long? = null
)

/** The priority determines whether the next wave of notifications will be triggered as usual or whether the current wave should be continued instead. */
enum class NotificationTriggerPriority {
    /** The next wave will be triggered as usual, discarding the notification at the top */
    Default,
    /** If the trigger is still valid and at the top, the next wave will be skipped and instead continues with the current one */
    WaveBreaking
}

enum class NotificationTriggerModality {
    EventContingent,
    Push
}

enum class NotificationTriggerStatus {
    Planned,
    Pushed,
    Displayed,
    Answered
}

/**
 * The source type determines whether the notification will be created on study enrolment or only through rules defined in a questionnaire.
 */
enum class NotificationTriggerSource {
    Scheduled,
    RuleBased
}