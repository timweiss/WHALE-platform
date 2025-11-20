package de.mimuc.senseeverything.data

import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.fetchCompletionStatus
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.GeneratedKey
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.flow.first

suspend fun fetchExternalQuestionnaireParams(
    params: Map<String, String>,
    dataStoreManager: DataStoreManager,
    database: AppDatabase,
    apiClient: ApiClient,
    pendingQuestionnaireId: String? = null
): Map<String, String> {
    val results = mutableMapOf<String, String>()

    for ((key, value) in params) {
        if (value.startsWith("generatedKey")) {
            val name = value.split('.')[1]
            val value = getOrCreateGeneratedKey(name, database)
            if (value != null) {
                results[key] = value
            }
        } else if (value.startsWith("configuration")) {
            when (value) {
                "configuration.enrolmentId" -> {
                    val enrolmentId = dataStoreManager.participantIdFlow.first()
                    results[key] = enrolmentId
                }
            }
        } else if (value.startsWith("questionnaire")) {
            when (value) {
                "questionnaire.pendingQuestionnaireId" -> {
                    if (pendingQuestionnaireId != null) {
                        results[key] = pendingQuestionnaireId
                    }
                }
            }
        } else if (value.startsWith("completionTracking")) {
            try {
                val completion = fetchCompletionStatus(apiClient, dataStoreManager)
                val completedString = completion.filter { (k, v) -> v }.keys.joinToString(",")
                results[key] = completedString
            } catch (e: Exception) {
               WHALELog.e("ExternalQuestionnaireParamsHelper", "Error fetching completion status: $e")
            }
        }
    }

    return results
}

fun getOrCreateGeneratedKey(name: String, database: AppDatabase): String? {
    val existing = database.generatedKeyDao().getByName(name)
    if (existing != null) {
        return existing.key
    } else {
        val generatedKey = GeneratedKey.createEntry(name)
        val id = database.generatedKeyDao().insert(generatedKey)
        if (id != -1L) {
            return generatedKey.key
        }
    }
    return null
}