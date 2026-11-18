package de.mimuc.senseeverything.api.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SensorUploadConfig(
    val sensorType: String,
    val clearAfterDays: Int,
    val requiredFlags: List<String>
)

@Serializable
data class Study(
    val name: String,
    val id: Int,
    val enrolmentKey: String,
    val description: String,
    val contactEmail: String,
    val durationDays: Int,
    val dataProtectionNotice: String?,
    val embeddedInfoUrl: String?,
    val sensorUploadConfig: List<SensorUploadConfig>?
) {
    companion object {
        val empty = Study(
            name = "No Study",
            id = -1,
            enrolmentKey = "",
            description = "No study loaded",
            contactEmail = "",
            durationDays = 0,
            dataProtectionNotice = null,
            embeddedInfoUrl = null,
            sensorUploadConfig = null
        )
    }
}

val studyJson = Json {
    serializersModule = Json.serializersModule
    ignoreUnknownKeys = true
}