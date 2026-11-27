package de.mimuc.senseeverything.contentextraction

import de.mimuc.senseeverything.api.model.ContentExtractionConfig
import de.mimuc.senseeverything.db.models.SensorUploadRequirement
import de.mimuc.senseeverything.sensor.implementation.UITreeSensor

/**
 * Set by the extraction worker on a UI tree row once it has handled it: extracted, not sampled, of an
 * app that isn't configured, or failed. In a study with content extraction a row only uploads with it.
 */
const val FLAG_EXTRACTION_HANDLED = "content_extraction_done"

/**
 * What content extraction adds to the study's upload requirements. There is no SensorUploadPolicy:
 * held-back trees are never deleted for a missing flag, the worker flags every row it handles.
 */
fun contentExtractionUploadRequirements(config: ContentExtractionConfig?): List<SensorUploadRequirement> =
    if (config == null) emptyList()
    else listOf(SensorUploadRequirement(UITreeSensor.NAME, FLAG_EXTRACTION_HANDLED))
