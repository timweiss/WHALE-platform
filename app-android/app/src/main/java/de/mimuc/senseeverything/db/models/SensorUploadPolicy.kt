package de.mimuc.senseeverything.db.models

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_upload_policy")
data class SensorUploadPolicy(@PrimaryKey val sensorName: String, val clearAfterDays: Int)