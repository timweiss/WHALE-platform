package de.mimuc.senseeverything.api.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SensorUploadConfig(
    val sensorType: String,
    val clearAfterDays: Int,
    val requiredFlags: List<String>
)

/** The model file; the app downloads it from the study backend and checks it against [sha256]. */
@Serializable
data class ContentExtractionModel(
    /** SHA-256 of the .litertlm file, lowercase hex */
    val sha256: String,
    val sizeBytes: Long
)

@Serializable
data class ContentExtractionPackage(
    val packageName: String,
    /** the app's hint block, rendered by the harness (export_content_extraction_golden.py); inserted verbatim */
    val hint: String?
)

/** On-device content extraction, see docs/content-extraction.md. */
@Serializable
data class ContentExtractionConfig(
    val model: ContentExtractionModel,
    /** apps whose snapshots are extracted; snapshots of other apps are only flagged */
    val packages: List<ContentExtractionPackage>,
    /** share of a configured app's snapshots that is extracted, (0, 1] */
    val sampleRate: Double = 1.0
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
    val sensorUploadConfig: List<SensorUploadConfig>?,
    /** null: no content extraction. The default reads studies stored before this field existed. */
    val contentExtraction: ContentExtractionConfig? = null
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