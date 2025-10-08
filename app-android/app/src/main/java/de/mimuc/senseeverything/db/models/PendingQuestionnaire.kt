package de.mimuc.senseeverything.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.ForeignKey.Companion.SET_NULL
import androidx.room.PrimaryKey
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.ema.QuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Serializable
enum class PendingQuestionnaireStatus {
    @SerialName("notified") NOTIFIED,
    @SerialName("pending") PENDING,
    @SerialName("completed") COMPLETED
}

@Serializable
enum class PendingQuestionnaireDisplayType {
    @SerialName("inbox") INBOX,
    @SerialName("notification_trigger") NOTIFICATION_TRIGGER
}

@Entity(tableName = "pending_questionnaire", foreignKeys = [
    ForeignKey(
        entity = NotificationTrigger::class,
        parentColumns = arrayOf("uid"),
        childColumns = arrayOf("notification_trigger_uid"),
        onDelete = SET_NULL
    ),
    ForeignKey(
        entity = PendingQuestionnaire::class,
        parentColumns = arrayOf("uid"),
        childColumns = arrayOf("source_pending_notification_id"),
        onDelete = SET_NULL
    )
])
data class PendingQuestionnaire(
    @PrimaryKey() var uid: UUID,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "valid_until") val validUntil: Long,
    @ColumnInfo(name = "questionnaire_json") val questionnaireJson: String,
    @ColumnInfo(name = "trigger_json") val triggerJson: String,
    @ColumnInfo(name = "saved_values") var elementValuesJson: String? = null,
    @ColumnInfo(name = "updated_at") var updatedAt: Long,
    @ColumnInfo(name = "opened_page") var openedPage: Int? = null,
    @ColumnInfo(name = "status") var status: PendingQuestionnaireStatus,
    @ColumnInfo(name = "finished_at") var finishedAt: Long? = null,
    @ColumnInfo(name = "notification_trigger_uid") var notificationTriggerUid: UUID? = null,
    @ColumnInfo(name = "source_pending_notification_id") val sourcePendingNotificationId: UUID? = null,
    @ColumnInfo(name = "display_type") val displayType: PendingQuestionnaireDisplayType = PendingQuestionnaireDisplayType.INBOX
) {

    companion object {
        suspend fun createEntry(
            database: AppDatabase,
            dataStoreManager: DataStoreManager,
            trigger: QuestionnaireTrigger,
            notificationTriggerUid: UUID? = null,
            sourcePendingNotificationId: UUID? = null
        ): UUID? {
            val questionnaire = dataStoreManager.questionnairesFlow.first()
                .find { q -> q.questionnaire.id == trigger.questionnaireId }
            if (questionnaire == null) return null

            val validUntil =
                if (trigger.validDuration != -1L) System.currentTimeMillis() + trigger.validDuration * 1000L * 60L else -1L

            val displayType =
                if (notificationTriggerUid != null) PendingQuestionnaireDisplayType.NOTIFICATION_TRIGGER else PendingQuestionnaireDisplayType.INBOX

            val pendingQuestionnaire = PendingQuestionnaire(
                uid = UUID.randomUUID(),
                System.currentTimeMillis(),
                validUntil,
                fullQuestionnaireJson.encodeToString(questionnaire),
                fullQuestionnaireJson.encodeToString<QuestionnaireTrigger>(trigger),
                null,
                System.currentTimeMillis(),
                -1,
                PendingQuestionnaireStatus.NOTIFIED,
                null,
                notificationTriggerUid,
                sourcePendingNotificationId,
                displayType
            )

            database.pendingQuestionnaireDao().insert(pendingQuestionnaire)
            return pendingQuestionnaire.uid
        }
    }

    fun update(
        database: AppDatabase,
        elementValues: Map<Int, ElementValue>?,
        openedPage: Int?
    ) {
        this.updatedAt = System.currentTimeMillis()
        this.openedPage = openedPage
        this.status = PendingQuestionnaireStatus.PENDING

        if (!elementValues.isNullOrEmpty()) {
            this.elementValuesJson = ElementValue.valueMapToJson(elementValues).toString()
        }

        database.pendingQuestionnaireDao().update(this)
    }

    fun markCompleted(
        database: AppDatabase,
        elementValues: Map<Int, ElementValue>
    ) {
        this.updatedAt = System.currentTimeMillis()
        this.finishedAt = System.currentTimeMillis()
        this.status = PendingQuestionnaireStatus.COMPLETED

        if (elementValues.isNotEmpty()) {
            this.elementValuesJson = ElementValue.valueMapToJson(elementValues).toString()
        }

        database.pendingQuestionnaireDao().update(this)
    }
}

data class QuestionnaireInboxItem(
    val title: String,
    val validUntil: Long,
    val pendingQuestionnaire: PendingQuestionnaire
)

fun QuestionnaireInboxItem.distanceMillis(): Duration {
    val now = System.currentTimeMillis()
    val diff = this.validUntil - now
    return diff.milliseconds
}