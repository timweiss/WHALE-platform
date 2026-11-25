package de.mimuc.senseeverything.service.accessibility

import de.mimuc.senseeverything.db.models.LogData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * The final sample of an ended period, logged as a sensor reading whether or not it gets rated. Only
 * identifies the screens, the images stay on the device.
 */
@Serializable
data class ScreenshotSampleReading(val periodEnd: Long, val apps: List<App>) {
    /** The counts are those of [ScreenshotStore.Reservoir] when the reading is written. */
    @Serializable
    data class App(
        val packageName: String,
        val itemsSeen: Int,
        val itemsDropped: Int,
        val samples: List<Sample>
    )

    @Serializable
    data class Sample(
        /** identifies the UI tree of the same screen, the key of its rating */
        val snapshotId: String,
        /** of the UI tree */
        val timestamp: Long
    )

    val sampleCount: Int get() = apps.sumOf { it.samples.size }

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

        /** By package, each app's samples oldest first. */
        fun of(periodEnd: Long, reservoirs: Map<String, ScreenshotStore.Reservoir>) = ScreenshotSampleReading(
            periodEnd,
            reservoirs.map { (packageName, reservoir) ->
                App(
                    packageName,
                    reservoir.itemsSeen,
                    reservoir.itemsDropped,
                    reservoir.slots.filterNotNull()
                        .map { Sample(it.snapshotId, it.timestamp) }
                        .sortedBy { it.timestamp }
                )
            }
        )
    }
}
