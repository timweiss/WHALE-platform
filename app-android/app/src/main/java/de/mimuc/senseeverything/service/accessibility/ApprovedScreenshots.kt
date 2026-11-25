package de.mimuc.senseeverything.service.accessibility

import android.content.Context
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.LogData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Screenshots the participant approved for upload, outside of [ScreenshotStore] so they outlive their
 * period. Each is one sensor reading with the image as its file; without approval there is no reading.
 */
object ApprovedScreenshots {
    const val SENSOR_NAME = "Screenshot"

    /** The only flag these readings get, so a sensorUploadConfig for them must not require others. */
    const val FLAG_UPLOAD_APPROVED = "upload_approved"

    @Serializable
    private data class Data(
        val snapshotId: String,
        val packageName: String,
        /** of the UI tree */
        val timestamp: Long,
        val periodEnd: Long
    )

    /** Not backed up, like [ScreenshotStore.root]. */
    fun root(context: Context) = File(context.noBackupFilesDir, "approved_screenshots")

    /** Copies the image, then adds the reading and its flag; idempotent. Reads and writes the disk. */
    fun keep(context: Context, database: AppDatabase, sample: ScreenshotStore.StoredSample) {
        val image = copyImage(root(context), sample)
        database.logDataDao().insertOnceWithFlag(
            toLogData(sample, image, System.currentTimeMillis()),
            FLAG_UPLOAD_APPROVED
        )
    }

    /** Copied, not moved: the rating may be opened again if the questionnaire isn't completed after all. */
    fun copyImage(root: File, sample: ScreenshotStore.StoredSample): File {
        root.mkdirs()
        return sample.image.copyTo(File(root, "${sample.sample.snapshotId}.jpg"), overwrite = true)
    }

    /** The localId follows from the screenshot, so it is recognised if it is approved twice. */
    fun toLogData(sample: ScreenshotStore.StoredSample, image: File, now: Long): LogData = LogData(
        now,
        SENSOR_NAME,
        Json.encodeToString(
            Data(sample.sample.snapshotId, sample.packageName, sample.sample.timestamp, sample.periodEnd)
        ),
        hasFile = true,
        filePath = image.path
    ).apply {
        localId = UUID.nameUUIDFromBytes("$SENSOR_NAME:${sample.sample.snapshotId}".toByteArray()).toString()
    }

    /** When the participant cancels or the enrolment is removed: also what is approved but not uploaded yet. */
    fun deleteAll(context: Context, database: AppDatabase) {
        // rows first: without one, a leftover file is never uploaded
        database.logDataDao().deleteBySensor(SENSOR_NAME)
        root(context).deleteRecursively()
    }
}
