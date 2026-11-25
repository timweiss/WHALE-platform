package de.mimuc.senseeverything.service.accessibility

import de.mimuc.senseeverything.db.models.LogData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * The final sample of an ended period, logged as a sensor reading whether or not it gets rated. Only
 * identifies the screens, the images stay on the device. Counts are in the period's summary log line.
 */
@Serializable
data class ScreenshotSampleReading(val periodEnd: Long, val samples: List<Entry>) {
    @Serializable
    data class Entry(
        /** identifies the UI tree of the same screen, the key of its rating */
        val snapshotId: String,
        val packageName: String,
        /** of the UI tree */
        val timestamp: Long
    )

    /**
     * The localId follows from the period, so if a crash makes the period end run twice, the server
     * drops the second reading (readings are unique by enrolment and localId).
     */
    fun toLogData(now: Long): LogData = LogData(now, SENSOR_NAME, Json.encodeToString(this)).apply {
        localId = UUID.nameUUIDFromBytes("$SENSOR_NAME:$periodEnd".toByteArray()).toString()
    }

    companion object {
        /** Must not be listed in the study's sensorUploadConfig, nothing sets flags on these readings. */
        const val SENSOR_NAME = "ScreenshotSample"

        /** Oldest first, like the rating shows them. */
        fun of(periodEnd: Long, samples: List<ScreenshotStore.StoredSample>) = ScreenshotSampleReading(
            periodEnd,
            samples.map { Entry(it.sample.snapshotId, it.packageName, it.sample.timestamp) }
                .sortedBy { it.timestamp }
        )
    }
}
