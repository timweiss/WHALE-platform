package de.mimuc.senseeverything.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.mimuc.senseeverything.api.model.QuestionnaireTrigger
import de.mimuc.senseeverything.data.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds

@Entity(tableName = "pending_questionnaire")
data class PendingQuestionnaire(
    @PrimaryKey(autoGenerate = true) var uid: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "valid_until") val validUntil: Long,
    @ColumnInfo(name = "questionnaire_json") val questionnaireJson: String,
    @ColumnInfo(name = "trigger_json") val triggerJson: String
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
                trigger.toJson().toString()
            )

            return database.pendingQuestionnaireDao().insert(pendingQuestionnaire)
        }
    }
}

data class QuestionnaireInboxItem(
    val title: String,
    val validUntil: Long,
    val pendingQuestionnaire: PendingQuestionnaire
)

fun QuestionnaireInboxItem.distanceMillis(): kotlin.time.Duration {
    val now = System.currentTimeMillis()
    val diff = this.validUntil - now
    return diff.milliseconds
}