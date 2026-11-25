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
import com.android.volley.ServerError
import com.android.volley.TimeoutError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.ApiResources
import de.mimuc.senseeverything.api.ChunkedUploadHelper
import de.mimuc.senseeverything.api.UploadResult
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.LogData
import de.mimuc.senseeverything.helpers.backgroundWorkForegroundInfo
import de.mimuc.senseeverything.helpers.setForegroundIfAllowed
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
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

private const val BATCH_SIZE = 200

/** The server will never accept the file: invalid request, too large, or an unsupported type (only JPEG is). */
fun isPermanentFileRejection(statusCode: Int): Boolean = statusCode in setOf(400, 413, 415)

/** 5xx; Volley's ClientError (4xx) is a ServerError, too. */
fun isServerFailure(e: Exception): Boolean = e is ServerError && e !is ClientError

/** The body [ApiResources.sensorReadingWithFile] expects. */
fun readingWithFileBody(logData: LogData, fileName: String, content: ByteArray): MultipartBody = MultipartBody.Builder()
    .setType(MultipartBody.FORM)
    .addFormDataPart("reading", Json.encodeToString(logData.toSensorReading()))
    .addFormDataPart("file", fileName, content.toRequestBody("application/octet-stream".toMediaType()))
    .build()

private fun LogData.toSensorReading() = SensorReading(
    sensorType = sensorName!!,
    timestamp = timestamp,
    data = data!!,
    localId = localId!!
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

    private val client = ApiClient.getInstance(appContext)

    override suspend fun doWork(): Result {
        val token = inputData.getString("token") ?: ""

        if (token.isEmpty()) {
            return Result.failure()
        }

        setForegroundIfAllowed(
            backgroundWorkForegroundInfo(notificationId, applicationContext, notificationManager)
        )

        return withContext(Dispatchers.IO) {
            try {
                syncLogData(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                WHALELog.e(TAG, "Unexpected error during sensor readings upload: $e")
                return@withContext Result.retry()
            }
        }
    }

    private suspend fun syncLogData(
        token: String,
    ): Result {
        val cutoffTimestamp = System.currentTimeMillis()
        val initialTotal = database.logDataDao().getUnsyncedCountBefore(cutoffTimestamp)

        var totalSynced = 0L

        while (!isStopped) {
            val batch = database.logDataDao().getNextNUnsyncedBeforeWithFlags(BATCH_SIZE, cutoffTimestamp)
            if (batch.isEmpty()) {
                return syncReadingsWithFile(token, cutoffTimestamp, totalSynced, initialTotal)
            }

            val uploadResult = try {
                upload(batch, token)
            } catch (e: Exception) {
                return resultForUploadError(e, totalSynced)
            }

            WHALELog.i(
                TAG,
                "Uploaded ${uploadResult.totalItems} items in ${uploadResult.chunksUploaded} chunk(s), " +
                        "total size: ${uploadResult.totalBytesUploaded} bytes, fast path: ${uploadResult.usedFastPath}"
            )

            logDroppedItems(uploadResult, batch)

            database.logDataDao().deleteLogData(*batch.toTypedArray<LogData>())
            WHALELog.i(TAG, "batch synced successful, removed ${batch.size} entries")

            totalSynced += batch.size

            reportProgress(totalSynced, initialTotal)
        }

        WHALELog.w(TAG, "Work cancelled, stopping further sync")
        return Result.success()
    }

    /**
     * Readings with a file, one request each. Every iteration deletes a row or ends the sync, so a file
     * the server rejects never blocks the others.
     */
    private suspend fun syncReadingsWithFile(
        token: String,
        cutoffTimestamp: Long,
        alreadySynced: Long,
        initialTotal: Long
    ): Result {
        var totalSynced = alreadySynced

        while (!isStopped) {
            val row = database.logDataDao().getNextUnsyncedWithFileBefore(cutoffTimestamp)
            if (row == null) {
                WHALELog.i(TAG, "Completed Sensor Reading Sync")
                return Result.success()
            }

            val file = row.filePath?.let { File(it) }
            if (file == null || !file.exists()) {
                // also after a crash between deleting an uploaded file and its row
                WHALELog.w(TAG, "Dropping ${row.sensorName} reading ${row.localId}, its file is gone")
            } else {
                try {
                    client.postMultipart(
                        url = ApiResources.sensorReadingWithFile(),
                        body = readingWithFileBody(row, file.name, file.readBytes()),
                        headers = mapOf("Authorization" to "Bearer $token")
                    )
                    WHALELog.i(TAG, "Uploaded ${row.sensorName} reading ${row.localId} with its file")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (isServerFailure(e)) {
                        WHALELog.w(TAG, "Server failed to store ${row.sensorName} reading ${row.localId}, rescheduling: $e")
                        return Result.retry()
                    }
                    if (e !is ClientError || !isPermanentFileRejection(e.networkResponse.statusCode)) {
                        return resultForUploadError(e, totalSynced)
                    }
                    WHALELog.e(TAG, "Dropping ${row.sensorName} reading ${row.localId}, the server rejected it: " +
                            "${e.networkResponse.statusCode} ${e.networkResponse.data.decodeToString()}")
                }
            }

            // file first: a row without its file is dropped on the next run, a file without its row stays
            file?.delete()
            database.logDataDao().deleteLogData(row)

            totalSynced++
            reportProgress(totalSynced, initialTotal)
        }

        WHALELog.w(TAG, "Work cancelled, stopping further sync")
        return Result.success()
    }

    private suspend fun reportProgress(totalSynced: Long, initialTotal: Long) {
        val progressPercentage = if (initialTotal > 0) {
            ((totalSynced.toDouble() / initialTotal) * 100).toInt()
        } else {
            100
        }
        setProgress(workDataOf("progress" to progressPercentage))
    }

    private suspend fun upload(batch: List<LogData>, token: String): UploadResult {
        val headers = mapOf("Authorization" to "Bearer $token")

        val sensorReadings = batch.map { it.toSensorReading() }

        return ChunkedUploadHelper.uploadWithSizeBasedChunking(
            data = sensorReadings,
            maxBatchSize = BATCH_SIZE,
            client = client,
            url = ApiResources.sensorReadingsBatched(),
            headers = headers
        )
    }

    private fun logDroppedItems(
        uploadResult: UploadResult,
        data: List<LogData>
    ) {
        if (uploadResult.errors.isNotEmpty()) {
            val droppedCount = uploadResult.errors.size
            val successfulCount = data.size - droppedCount

            WHALELog.w(
                TAG,
                "Upload completed with $droppedCount dropped item(s) out of ${data.size} total ($successfulCount successful)"
            )

            uploadResult.errors.forEach { error ->
                WHALELog.e(TAG, "Dropped item: $error")
            }
        }
    }

    private fun resultForUploadError(
        e: Exception,
        currentTotalSynced: Long
    ): Result {
        return when {
            e is NetworkError || e is TimeoutError -> {
                Result.retry()
            }

            e is ClientError && e.networkResponse.statusCode == 429 -> {
                WHALELog.w(TAG, "Rate limited, rescheduling upload with total $currentTotalSynced")
                Result.retry()
            }

            e is ClientError -> {
                val message = e.networkResponse.data.decodeToString()
                WHALELog.e(
                    TAG,
                    "Client error uploading sensor readings: $message with total $currentTotalSynced"
                )
                Result.failure()
            }

            else -> {
                WHALELog.e(
                    TAG,
                    "Error uploading sensor readings: $e, ${e.stackTraceToString()} with total $currentTotalSynced"
                )
                Result.failure()
            }
        }
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
        .setInitialDelay(30.minutes.toJavaDuration()) // ensure that the first setup minutes are logged before first sync
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