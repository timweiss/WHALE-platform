package de.mimuc.senseeverything.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.QuestionnaireTrigger
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Entity(tableName = "pending_questionnaire")
data class PendingQuestionnaire(
    @PrimaryKey(autoGenerate = true) var uid: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "valid_until") val validUntil: Long,
    @ColumnInfo(name = "questionnaire_json") val questionnaireJson: String,
    @ColumnInfo(name = "trigger_json") val triggerJson: String,
    @ColumnInfo(name = "saved_values") var elementValuesJson: String? = null,
    @ColumnInfo(name = "updated_at") var updatedAt: Long,
    @ColumnInfo(name = "opened_page") var openedPage: Int? = null
) {

    companion object {
        suspend fun createEntry(
            database: AppDatabase,
            dataStoreManager: DataStoreManager,
            trigger: QuestionnaireTrigger
        ): Long {
            val questionnaire = dataStoreManager.questionnairesFlow.first()
                .find { q -> q.questionnaire.id == trigger.questionnaireId }
            if (questionnaire == null) return -1

            val validUntil =
                if (trigger.validDuration != -1L) System.currentTimeMillis() + trigger.validDuration * 1000L * 60L else -1L

            val pendingQuestionnaire = PendingQuestionnaire(
                0,
                System.currentTimeMillis(),
                validUntil,
                questionnaire.toJson().toString(),
                trigger.toJson().toString(),
                null,
                System.currentTimeMillis()
            )

            return database.pendingQuestionnaireDao().insert(pendingQuestionnaire)
        }
    }

    fun update(
        database: AppDatabase,
        elementValues: Map<Int, ElementValue>?,
        openedPage: Int?
    ) {
        this.updatedAt = System.currentTimeMillis()
        this.openedPage = openedPage

        if (!elementValues.isNullOrEmpty()) {
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