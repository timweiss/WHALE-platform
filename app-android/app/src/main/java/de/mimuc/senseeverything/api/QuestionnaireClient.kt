package de.mimuc.senseeverything.api

import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.ema.Questionnaire
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.data.DataStoreManager

suspend fun fetchAndPersistQuestionnaires(studyId: Int, dataStoreManager: DataStoreManager, client: ApiClient): List<FullQuestionnaire> {
    val questionnaires = fetchQuestionnairesForStudy(studyId, client)

    // load full questionnaires
    val fullQuestionnaires = mutableListOf<FullQuestionnaire>()
    for (questionnaire in questionnaires) {
        try {
            val fullQuestionnaire = client.getSerialized<FullQuestionnaire>(
                url = ApiResources.questionnaire(studyId, questionnaire.id),
                json = fullQuestionnaireJson
            )
            fullQuestionnaires.add(fullQuestionnaire)
        } catch (e: Exception) {
            // could not load full questionnaire
            return emptyList()
        }
    }

    // persist questionnaires
    dataStoreManager.saveQuestionnaires(fullQuestionnaires)

    return fullQuestionnaires
}

suspend fun fetchQuestionnairesForStudy(studyId: Int, client: ApiClient): List<Questionnaire> {
    return try {
        client.getSerialized<List<Questionnaire>>(
            url = ApiResources.questionnaires(studyId),
            json = fullQuestionnaireJson
        )
    } catch (e: Exception) {
        // todo: we should fail here
        emptyList()
    }
}