package de.mimuc.senseeverything.data

import de.mimuc.senseeverything.api.model.ContentExtractionConfig
import de.mimuc.senseeverything.api.model.ContentExtractionModel
import de.mimuc.senseeverything.api.model.ContentExtractionPackage
import de.mimuc.senseeverything.api.model.SensorUploadConfig
import de.mimuc.senseeverything.api.model.Study
import de.mimuc.senseeverything.api.model.studyJson
import de.mimuc.senseeverything.db.models.SensorUploadPolicy
import de.mimuc.senseeverything.db.models.SensorUploadRequirement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class StudyUploadConfigTest {
    private val extraction = ContentExtractionConfig(
        model = ContentExtractionModel(sha256 = "ab".repeat(32), sizeBytes = 2_588_147_712),
        packages = listOf(ContentExtractionPackage("com.instagram.android", hint = "App-specific notes:\n"))
    )

    private fun study(sensorUploadConfig: List<SensorUploadConfig>?, contentExtraction: ContentExtractionConfig?) =
        Study.empty.copy(sensorUploadConfig = sensorUploadConfig, contentExtraction = contentExtraction)

    @Test
    fun withoutAnyConfigNothingIsHeldBack() {
        assertEquals(Pair(emptyList<SensorUploadPolicy>(), emptyList<SensorUploadRequirement>()), uploadConfigFor(study(null, null)))
    }

    @Test
    fun contentExtractionHoldsUiTreesBackWithoutDeletePolicy() {
        val (policies, requirements) = uploadConfigFor(study(null, extraction))
        assertEquals(emptyList<SensorUploadPolicy>(), policies)
        assertEquals(listOf(SensorUploadRequirement("UITree", "content_extraction_done")), requirements)
    }

    @Test
    fun contentExtractionAddsToTheStudysUploadConfig() {
        val screenshots = SensorUploadConfig("Screenshot", clearAfterDays = 7, requiredFlags = listOf("upload_approved"))
        // a study that also lists the requirement itself gets it once
        val uiTree = SensorUploadConfig("UITree", clearAfterDays = 30, requiredFlags = listOf("content_extraction_done"))

        val (policies, requirements) = uploadConfigFor(study(listOf(screenshots, uiTree), extraction))

        assertEquals(listOf(SensorUploadPolicy("Screenshot", 7), SensorUploadPolicy("UITree", 30)), policies)
        assertEquals(
            listOf(
                SensorUploadRequirement("Screenshot", "upload_approved"),
                SensorUploadRequirement("UITree", "content_extraction_done")
            ),
            requirements
        )
    }

    private val studyFields = """"name": "n", "id": 1, "enrolmentKey": "k", "description": "d", "contactEmail": "e",
        "durationDays": 7, "dataProtectionNotice": null, "embeddedInfoUrl": null, "sensorUploadConfig": null"""

    @Test
    fun studiesStoredBeforeTheFieldExistedHaveNoExtraction() {
        assertNull(studyJson.decodeFromString<Study>("{$studyFields}").contentExtraction)
    }

    @Test
    fun readsTheBackendsConfigWithDefaultSampleRate() {
        val json = """{$studyFields, "contentExtraction": {
            "model": {"sha256": "${"ab".repeat(32)}", "sizeBytes": 2588147712},
            "packages": [{"packageName": "com.instagram.android", "hint": "App-specific notes:\n"}]}}"""
        assertEquals(extraction, studyJson.decodeFromString<Study>(json).contentExtraction)
    }
}
