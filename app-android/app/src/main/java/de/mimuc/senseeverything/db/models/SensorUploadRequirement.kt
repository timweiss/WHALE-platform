package de.mimuc.senseeverything.db.models

import androidx.room.Entity

@Entity(tableName = "sensor_upload_requirement", primaryKeys = ["sensorName", "flag"])
data class SensorUploadRequirement(
    val sensorName: String,
    val flag: String
)
