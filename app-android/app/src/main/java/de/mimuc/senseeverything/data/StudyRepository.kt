package de.mimuc.senseeverything.data

import de.mimuc.senseeverything.api.model.SensorUploadConfig
import de.mimuc.senseeverything.api.model.Study
import de.mimuc.senseeverything.contentextraction.contentExtractionUploadRequirements
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.SensorUploadPolicy
import de.mimuc.senseeverything.db.models.SensorUploadRequirement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

fun mapSensorUploadConfig(configs: List<SensorUploadConfig>): Pair<List<SensorUploadPolicy>, List<SensorUploadRequirement>> {
    val policies = configs.map { SensorUploadPolicy(it.sensorType, it.clearAfterDays) }
    val requirements = configs.flatMap {
        it.requiredFlags.map { flag ->
            SensorUploadRequirement(
                it.sensorType,
                flag
            )
        }
    }

    return Pair(policies, requirements)
}

/** The study's sensorUploadConfig plus what its features need to hold readings back. */
fun uploadConfigFor(study: Study): Pair<List<SensorUploadPolicy>, List<SensorUploadRequirement>> {
    val (policies, requirements) = mapSensorUploadConfig(study.sensorUploadConfig ?: emptyList())
    val extraction = contentExtractionUploadRequirements(study.contentExtraction)
    return Pair(policies, (requirements + extraction).distinct())
}

@Singleton
class StudyRepository @Inject constructor(
    private val database: AppDatabase,
    private val dataStore: DataStoreManager
) {
    suspend fun saveStudy(study: Study) {
        // update upload config attached to study config, empty if not present
        val (policies, requirements) = uploadConfigFor(study)
        withContext(Dispatchers.IO) {
            database.sensorUploadDao().replaceUploadConfig(policies, requirements)
        }

        dataStore.saveStudy(study)
    }
}