package de.mimuc.senseeverything.contentextraction

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/*
 * Sensor readings of the content extraction, uploaded like any other reading. Shapes documented in
 * docs/content-extraction.md ("Readings").
 */

/** One per extracted snapshot. */
const val SENSOR_CONTENT_EXTRACTION = "ContentExtraction"

/** One per run that started (or failed to start) the engine. */
const val SENSOR_CONTENT_EXTRACTION_RUN = "ContentExtractionRun"

@Serializable
data class ExtractionCallRecord(
    val segment: Int,
    val templateHash: String,
    /** the exact prompt, as the harness's transcripts keep the exact request */
    val prompt: String,
    val rawOutput: String?,
    /** engine error, or why rawOutput isn't usable JSON */
    val error: String?,
    val wallMs: Long,
    val prefillTokens: Int?,
    val decodeTokens: Int?,
    val prefillTokensPerSecond: Double?,
    val decodeTokensPerSecond: Double?,
    val timeToFirstTokenSeconds: Double?
)

@Serializable
data class ContentExtractionReading(
    val runId: String,
    val snapshotId: String,
    val snapshotTimestamp: Long,
    val appPackage: String,
    /** "extracted", or "crashed": the process died on this row twice, it was not extracted */
    val outcome: String,
    val modelSha256: String,
    val runtime: String,
    val backend: String?,
    val rulesVersion: String,
    /** "anchor" or "whole"; null when not extracted */
    val segmentationMode: String?,
    val calls: List<ExtractionCallRecord>,
    /** postprocessed items of all calls, each tagged with "_segment" like the harness's predictions */
    val items: List<JsonObject>,
    /** items postprocessing dropped (no content field) */
    val droppedItems: Int,
    val wallMs: Long
)

@Serializable
data class BatteryState(
    val levelPercent: Int?,
    /** BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER, µAh; null if the device doesn't report it */
    val chargeCounterMicroAh: Long?,
    val charging: Boolean?
)

@Serializable
data class DeviceDescription(
    val manufacturer: String,
    val model: String,
    val socManufacturer: String,
    val socModel: String,
    val sdk: Int,
    val totalRamBytes: Long
)

@Serializable
data class ContentExtractionRunReading(
    val runId: String,
    /** "scheduled" or "manual" */
    val trigger: String,
    /** "done", "stopped" (by the system, a stop of the work or the end of the night window), "engine_failed" or "model_unavailable" */
    val outcome: String,
    val startedAt: Long,
    val endedAt: Long,
    val modelSha256: String,
    val runtime: String,
    val rulesVersion: String,
    val sampleRate: Double,
    val backend: String?,
    /** why the GPU engine couldn't be created, when the CPU ran instead */
    val gpuError: String?,
    val engineInitMs: Long?,
    val engineErrors: List<String>,
    val rowsHandled: Int,
    /** rows flagged without extraction because the engine could not run (they upload without results) */
    val rowsFlaggedWithoutExtraction: Int,
    val rowsCrashed: Int,
    val rowsUndecodable: Int,
    val snapshotsSeen: Int,
    val snapshotsOfConfiguredApps: Int,
    val snapshotsExtracted: Int,
    val calls: Int,
    val callErrors: Int,
    /** CPU time of the :extraction process during the run */
    val cpuMs: Long,
    /** VmHWM of the :extraction process; for this run only if peakRssReset */
    val peakRssKb: Long?,
    val peakRssReset: Boolean,
    val batteryStart: BatteryState,
    val batteryEnd: BatteryState,
    /** PowerManager thermal status, 0 (none) .. 6 (shutdown) */
    val thermalStatusStart: Int,
    val thermalStatusEnd: Int,
    /** WorkInfo stop reason when the system stopped the run, else null */
    val stopReason: Int?,
    val device: DeviceDescription
)
