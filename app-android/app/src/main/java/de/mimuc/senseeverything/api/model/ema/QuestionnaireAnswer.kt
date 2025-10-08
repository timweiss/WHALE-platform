package de.mimuc.senseeverything.api.model.ema

import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.ApiResources
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.db.models.PendingQuestionnaireStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

@Serializable
data class QuestionnaireAnswerRequest(
    val pendingQuestionnaireId: String,
    val createdTimestamp: Long,
    val lastUpdatedTimestamp: Long,
    val finishedTimestamp: Long,
    val lastOpenedPage: Int,
    val status: PendingQuestionnaireStatus,
    val notificationTrigger: SerializableNotificationTrigger?,
    val answers: JsonArray
)

@Serializable
data class SerializableNotificationTrigger(
    val uid: String,
    val addedAt: Long,
    val name: String,
    val status: String,
    val validFrom: Long,
    val priority: String,
    val timeBucket: String,
    val modality: String,
    val source: String,
    val questionnaireId: Long,
    val triggerId: Int,
    val plannedAt: Long?,
    val pushedAt: Long?,
    val displayedAt: Long?,
    val answeredAt: Long?,
    val updatedAt: Long
)

suspend fun uploadQuestionnaireAnswer(
    client: ApiClient,
    answers: String,
    questionnaireId: Int,
    studyId: Int,
    userToken: String,
    pendingQuestionnaire: PendingQuestionnaire,
    notificationTrigger: NotificationTrigger?
): JsonElement {
    val request = makeAnswerRequest(answers, pendingQuestionnaire, notificationTrigger)
    val headers = mapOf("Authorization" to "Bearer $userToken")
    
    return client.postSerialized<QuestionnaireAnswerRequest, JsonElement>(
        url = ApiResources.questionnaireAnswer(studyId, questionnaireId),
        requestData = request,
        headers = headers
    )
}

fun makeAnswerRequest(
    answers: String,
    pendingQuestionnaire: PendingQuestionnaire,
    notificationTrigger: NotificationTrigger?
): QuestionnaireAnswerRequest {
    val answersJsonArray = Json.parseToJsonElement(answers) as JsonArray
    
    val serializableNotificationTrigger = notificationTrigger?.let { trigger ->
        // Parse the trigger JSON to get the trigger ID
        val triggerData = fullQuestionnaireJson.decodeFromString<QuestionnaireTrigger>(trigger.triggerJson)
        
        SerializableNotificationTrigger(
            uid = trigger.uid.toString(),
            addedAt = trigger.addedAt,
            name = trigger.name,
            status = trigger.status.name,
            validFrom = trigger.validFrom,
            priority = trigger.priority.name,
            timeBucket = trigger.timeBucket,
            modality = trigger.modality.name,
            source = trigger.source.name,
            questionnaireId = trigger.questionnaireId,
            triggerId = triggerData.id,
            plannedAt = trigger.plannedAt,
            pushedAt = trigger.pushedAt,
            displayedAt = trigger.displayedAt,
            answeredAt = trigger.answeredAt,
            updatedAt = trigger.updatedAt
        )
    }
    
    return QuestionnaireAnswerRequest(
        pendingQuestionnaireId = pendingQuestionnaire.uid.toString(),
        createdTimestamp = pendingQuestionnaire.addedAt,
        lastUpdatedTimestamp = pendingQuestionnaire.updatedAt,
        finishedTimestamp = pendingQuestionnaire.finishedAt ?: -1,
        lastOpenedPage = pendingQuestionnaire.openedPage ?: -1,
        status = pendingQuestionnaire.status,
        notificationTrigger = serializableNotificationTrigger,
        answers = answersJsonArray
    )
}