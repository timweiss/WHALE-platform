package de.mimuc.senseeverything.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.QuestionnaireTrigger
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import kotlinx.coroutines.flow.first
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class PendingQuestionnaireStatus {
    NOTIFIED,
    PENDING,
    COMPLETED
}

@Entity(tableName = "pending_questionnaire")
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
    @ColumnInfo(name = "finished_at") var finishedAt: Long? = null
) {

    companion object {
        suspend fun createEntry(
            database: AppDatabase,
            dataStoreManager: DataStoreManager,
            trigger: QuestionnaireTrigger
        ): UUID? {
            val questionnaire = dataStoreManager.questionnairesFlow.first()
                .find { q -> q.questionnaire.id == trigger.questionnaireId }
            if (questionnaire == null) return null

            val validUntil =
                if (trigger.validDuration != -1L) System.currentTimeMillis() + trigger.validDuration * 1000L * 60L else -1L

            val pendingQuestionnaire = PendingQuestionnaire(
                uid = UUID.randomUUID(),
                System.currentTimeMillis(),
                validUntil,
                questionnaire.toJson().toString(),
                trigger.toJson().toString(),
                null,
                System.currentTimeMillis(),
                -1,
                PendingQuestionnaireStatus.NOTIFIED,
                null
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