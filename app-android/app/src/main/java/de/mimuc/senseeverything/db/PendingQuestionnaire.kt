package de.mimuc.senseeverything.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import de.mimuc.senseeverything.api.model.QuestionnaireTrigger
import de.mimuc.senseeverything.data.DataStoreManager
import kotlinx.coroutines.flow.first

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
            questionnaireId: Int,
            trigger: QuestionnaireTrigger
        ): Long {
            val questionnaire = dataStoreManager.questionnairesFlow.first()
                .find { q -> q.questionnaire.id == questionnaireId }
            if (questionnaire == null) return -1

            val pendingQuestionnaire = PendingQuestionnaire(
                0,
                System.currentTimeMillis(),
                trigger.validDuration,
                questionnaire.toJson().toString(),
                trigger.toJson().toString()
            )

            return database.pendingQuestionnaireDao().insert(pendingQuestionnaire)
        }
    }
}
