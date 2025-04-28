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
            val name = value.split('.')[1]
            val value = getOrCreateGeneratedKey(name, database)
            if (value != null) {
                results.put(key, value)
            }
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

fun getOrCreateGeneratedKey(name: String, database: AppDatabase): String? {
    val existing = database.generatedKeyDao().getByName(name)
    if (existing != null) {
        return existing.key
    } else {
        val generatedKey = de.mimuc.senseeverything.db.GeneratedKey.createEntry(name)
        val id = database.generatedKeyDao().insert(generatedKey)
        if (id != -1L) {
            return generatedKey.key
        }
    }
    return null
}