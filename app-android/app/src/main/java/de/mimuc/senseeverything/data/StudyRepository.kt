package de.mimuc.senseeverything.data

import de.mimuc.senseeverything.api.model.SensorUploadConfig
import de.mimuc.senseeverything.api.model.Study
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

@Singleton
class StudyRepository @Inject constructor(
    private val database: AppDatabase,
    private val dataStore: DataStoreManager
) {
    suspend fun saveStudy(study: Study) {
        // update upload config attached to study config, empty if not present
        val (policies, requirements) = mapSensorUploadConfig(study.sensorUploadConfig ?: emptyList())
        withContext(Dispatchers.IO) {
            database.sensorUploadDao().replaceUploadConfig(policies, requirements)
        }

        dataStore.saveStudy(study)
    }
}