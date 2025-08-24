package de.mimuc.senseeverything.api.model.ema

import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.ApiResources
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

suspend fun uploadQuestionnaireAnswer(client: ApiClient, answers: String, questionnaireId: Int, studyId: Int, userToken: String, pendingQuestionnaire: PendingQuestionnaire) {
    val json = makeAnswerJson(answers, pendingQuestionnaire)
    val headers = mapOf("Authorization" to "Bearer $userToken")
    val response = suspendCoroutine { continuation ->
        client.post(
            ApiResources.questionnaireAnswer(studyId, questionnaireId),
            json,
            headers,
            { response ->
                // Success
                continuation.resume(response)
            },
            { error ->
                // Error
                continuation.resumeWithException(error)
            })
    }
}

fun makeAnswerJson(answers: String, pendingQuestionnaire: PendingQuestionnaire): JSONObject {
    return JSONObject().apply {
        put("pendingQuestionnaireId", pendingQuestionnaire.uid.toString())
        put("createdTimestamp", pendingQuestionnaire.addedAt)
        put("lastUpdatedTimestamp", pendingQuestionnaire.updatedAt)
        put("finishedTimestamp", pendingQuestionnaire.finishedAt ?: -1)
        put("lastOpenedPage", pendingQuestionnaire.openedPage ?: -1)
        put("status", pendingQuestionnaire.status.name)
        put("answers", JSONArray(answers))
    }
}