package de.mimuc.senseeverything.api

import de.mimuc.senseeverything.api.model.FullQuestionnaire
import de.mimuc.senseeverything.api.model.Questionnaire
import de.mimuc.senseeverything.api.model.makeFullQuestionnaireFromJson
import de.mimuc.senseeverything.api.model.makeQuestionnaireFromJson
import de.mimuc.senseeverything.data.DataStoreManager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun fetchAndPersistQuestionnaires(studyId: Int, dataStoreManager: DataStoreManager, client: ApiClient): List<FullQuestionnaire> {
    val questionnaires = fetchQuestionnairesForStudy(studyId, client)

    // load full questionnaires
    val fullQuestionnaires = mutableListOf<FullQuestionnaire>()
    for (questionnaire in questionnaires) {
        val json = suspendCoroutine { continuation ->
            client.getJson("https://sisensing.medien.ifi.lmu.de/v1/study/$studyId/questionnaire/${questionnaire.id}",
                { response ->
                    continuation.resume(response)
                }, { error ->
                    continuation.resume(null)
                })
        }

        if (json == null) {
            // could not load full questionnaire
            return emptyList()
        }

        val fullQuestionnaire = makeFullQuestionnaireFromJson(json)
        fullQuestionnaires.add(fullQuestionnaire)
    }

    // persist questionnaires
    dataStoreManager.saveQuestionnaires(fullQuestionnaires)

    return fullQuestionnaires
}

suspend fun fetchQuestionnairesForStudy(studyId: Int, client: ApiClient): List<Questionnaire> {
    val response = suspendCoroutine { continuation ->
        client.getJsonArray("https://sisensing.medien.ifi.lmu.de/v1/study/$studyId/questionnaire",
            { response ->
                continuation.resume(response)
            }, { error ->
                continuation.resume(null)
            })
    }

    // could not load questionnaires for study, for example study not found
    if (response == null) {
        // todo: we should fail here
        return emptyList()
    }

    // parse questionnaires
    val questionnaires = mutableListOf<Questionnaire>()
    for (i in 0 until response.length()) {
        val questionnaire = response.getJSONObject(i)
        questionnaires.add(makeQuestionnaireFromJson(questionnaire))
    }

    return questionnaires.toList()
}