package de.mimuc.senseeverything.db.models

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.IGNORE
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface SensorUploadDao {
    @Insert(onConflict = IGNORE)
    fun insertUploadPolicies(policies: List<SensorUploadPolicy>)

    @Insert(onConflict = IGNORE)
    fun insertUploadRequirements(requirements: List<SensorUploadRequirement>)

    @Query("DELETE FROM sensor_upload_policy")
    fun deleteAllPolicies()

    @Query("DELETE FROM sensor_upload_requirement")
    fun deleteAllRequirements()

    @Transaction
    suspend fun replaceUploadConfig(policies: List<SensorUploadPolicy>, requirements: List<SensorUploadRequirement>) {
        deleteAllPolicies()
        deleteAllRequirements()
        insertUploadPolicies(policies)
        insertUploadRequirements(requirements)
    }
}