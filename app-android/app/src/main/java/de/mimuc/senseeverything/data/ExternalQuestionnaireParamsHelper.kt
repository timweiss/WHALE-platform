package de.mimuc.senseeverything.data

import de.mimuc.senseeverything.db.AppDatabase
import kotlinx.coroutines.flow.first

suspend fun fetchExternalQuestionnaireParams(
    params: Map<String, String>,
    dataStoreManager: DataStoreManager,
    database: AppDatabase
): Map<String, String> {
    var results = mutableMapOf<String, String>()

    for ((key, value) in params) {
        if (value.startsWith("generatedKey")) {
            // todo: implement
        } else if (value.startsWith("configuration")) {
            when (value) {
                "configuration.enrolmentId" -> {
                    val enrolmentId = dataStoreManager.participantIdFlow.first()
                    results.put(key, enrolmentId)
                }
            }
        }
    }

    return results
}