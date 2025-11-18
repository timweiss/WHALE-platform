package de.mimuc.senseeverything.workers

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.android.volley.ClientError
import com.android.volley.NetworkError
import com.android.volley.TimeoutError
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.ApiResources
import de.mimuc.senseeverything.api.ChunkedUploadHelper
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.LogData
import de.mimuc.senseeverything.helpers.backgroundWorkForegroundInfo
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

enum class UploadWorkTag(val tag: String) {
    IMMEDIATE_FROM_DEBUG("immediateFromDebugSettings"),
    FINAL_UPLOAD_MANUAL("finalReadingsUploadUserInitiated"),
    STALE_UPLOAD_MANUAL("staleReadingsUploadUserInitiated")
}

@Serializable
data class SensorReading(
    val sensorType: String,
    val timestamp: Long,
    val data: String,
    val localId: String
)

@HiltWorker
class SensorReadingsUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: AppDatabase
) :
    CoroutineWorker(appContext, workerParams) {
    val TAG = "SensorReadingsUploadWorker"

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    private val notificationId = 1012

    override suspend fun doWork(): Result {
        val db = database

        val token = inputData.getString("token") ?: ""

        if (token.isEmpty()) {
            return Result.failure()
        }

        setForeground(
            backgroundWorkForegroundInfo(notificationId, applicationContext, notificationManager)
        )

        return withContext(Dispatchers.IO) {
            try {
                val cutoffTimestamp = System.currentTimeMillis()
                val total = db.logDataDao().getUnsyncedCountBefore(cutoffTimestamp)
                syncNextNActivities(db, applicationContext, token, total, cutoffTimestamp, 0, 200)
            } catch (e: Exception) {
                WHALELog.e(TAG, "Unexpected error during sensor readings upload: $e")
                return@withContext Result.retry()
            }
        }
    }

    private suspend fun syncNextNActivities(
        db: AppDatabase,
        context: Context,
        token: String,
        initialTotal: Long,
        cutoffTimestamp: Long,
        totalSynced: Long,
        n: Int
    ): Result {
        var currentTotalSynced = totalSynced
        val client = ApiClient.getInstance(context)
        val headers = mapOf("Authorization" to "Bearer $token")

        while (!isStopped) {
            val data = db.logDataDao().getNextNUnsyncedBefore(n, cutoffTimestamp)
            if (data.isEmpty()) {
                WHALELog.i(TAG, "Completed Sensor Reading Sync")
                return Result.success()
            }

            val sensorReadings = data.map { logData ->
                SensorReading(
                    sensorType = logData.sensorName,
                    timestamp = logData.timestamp,
                    data = logData.data,
                    localId = logData.localId
                )
            }

            try {
                val uploadResult = ChunkedUploadHelper.uploadWithSizeBasedChunking(
                    data = sensorReadings,
                    maxBatchSize = n,
                    client = client,
                    url = ApiResources.sensorReadingsBatched(),
                    headers = headers
                )

                WHALELog.i(TAG, "Uploaded ${uploadResult.totalItems} items in ${uploadResult.chunksUploaded} chunk(s), " +
                        "total size: ${uploadResult.totalBytesUploaded} bytes, fast path: ${uploadResult.usedFastPath}")

                // Log if any items were dropped as unuploadable
                if (uploadResult.errors.isNotEmpty()) {
                    val droppedCount = uploadResult.errors.size
                    val successfulCount = data.size - droppedCount

                    WHALELog.w(TAG, "Upload completed with $droppedCount dropped item(s) out of ${data.size} total ($successfulCount successful)")
                    Firebase.crashlytics.log("Dropped $droppedCount unuploadable items out of ${data.size} during sync")

                    uploadResult.errors.forEach { error ->
                        WHALELog.e(TAG, "Dropped item: $error")
                        Firebase.crashlytics.log("Dropped item: $error")
                    }
                }

                db.logDataDao().deleteLogData(*data.toTypedArray<LogData>())
                WHALELog.i(TAG, "batch synced successful, removed ${data.size} entries")

                currentTotalSynced += data.size

                val progressPercentage = if (initialTotal > 0) {
                    ((currentTotalSynced.toDouble() / initialTotal) * 100).toInt()
                } else {
                    100
                }
                setProgress(workDataOf("progress" to progressPercentage))

            } catch (e: Exception) {
                if (e is NetworkError || e is TimeoutError) {
                    return Result.retry()
                }

                if (e is ClientError) {
                    val message = e.networkResponse.data.decodeToString()
                    WHALELog.e(
                        TAG,
                        "Client error uploading sensor readings: $message with total $currentTotalSynced"
                    )
                    Firebase.crashlytics.log("Client error uploading sensor readings: $message with total $currentTotalSynced")
                    return Result.failure()
                }

                WHALELog.e(
                    TAG,
                    "Error uploading sensor readings: $e, ${e.stackTraceToString()} with total $currentTotalSynced"
                )
                Firebase.crashlytics.log("Error uploading sensor readings: $e, ${e.stackTraceToString()} with total $currentTotalSynced")

                return Result.failure()
            }
        }

        WHALELog.w(TAG, "Work cancelled, stopping further sync")
        return Result.success()
    }
}

fun enqueueSensorReadingsUploadWorker(context: Context, token: String) {
    val data = workDataOf("token" to token)

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .build()

    val uploadWorkRequest = PeriodicWorkRequestBuilder<SensorReadingsUploadWorker>(1, TimeUnit.DAYS)
        .addTag("readingsUpload")
        .setInputData(data)
        .setConstraints(constraints)
        .build()

    // Use enqueueUniquePeriodicWork with REPLACE policy to properly replace existing work
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "sensorReadingsUpload",
        ExistingPeriodicWorkPolicy.REPLACE,
        uploadWorkRequest
    )
}

fun enqueueSingleSensorReadingsUploadWorker(
    context: Context,
    token: String,
    workTag: UploadWorkTag = UploadWorkTag.FINAL_UPLOAD_MANUAL,
    expedited: Boolean,
    delay: Duration = 0.milliseconds
) {
    val data = workDataOf("token" to token)

    val constraintsBuilder = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)

    val uploadWorkRequestBuilder = OneTimeWorkRequestBuilder<SensorReadingsUploadWorker>()
        .addTag(workTag.tag)
        .setInputData(data)
        .setInitialDelay(delay.toJavaDuration())
        .setConstraints(constraintsBuilder.build())

    if (expedited) {
        uploadWorkRequestBuilder
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
    }

    WorkManager.getInstance(context).enqueue(uploadWorkRequestBuilder.build())
}