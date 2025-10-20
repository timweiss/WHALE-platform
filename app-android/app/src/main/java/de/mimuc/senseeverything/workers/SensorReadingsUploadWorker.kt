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
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.LogData
import de.mimuc.senseeverything.helpers.backgroundWorkForegroundInfo
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

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
                val total = db.logDataDao().getUnsyncedCount()
                syncNextNActivities(db, applicationContext, token, total, 0, 200)
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }

    private suspend fun syncNextNActivities(
        db: AppDatabase,
        context: Context,
        token: String,
        remaining: Long,
        totalSynced: Long,
        n: Int
    ): Result {
        if (isStopped) {
            WHALELog.w(TAG, "Work cancelled, stopping further sync")
            return Result.success()
        }

        val data = db.logDataDao().getNextNUnsynced(n)
        if (data.isEmpty()) {
            WHALELog.i(TAG, "Completed Sensor Reading Sync")
            return Result.success()
        }

        setProgress(workDataOf("progress" to (totalSynced / (if (remaining != 0L) remaining else 1L))))

        val sensorReadings = data.map { logData ->
            SensorReading(
                sensorType = logData.sensorName,
                timestamp = logData.timestamp,
                data = logData.data,
                localId = logData.localId
            )
        }

        val client = ApiClient.getInstance(context)
        val headers = mapOf("Authorization" to "Bearer $token")

        try {
            client.postSerialized<List<SensorReading>, Unit>(
                url = ApiResources.sensorReadingsBatched(),
                requestData = sensorReadings,
                headers = headers
            )

            db.logDataDao().deleteLogData(*data.toTypedArray<LogData>())
            WHALELog.i(TAG, "batch synced successful, removed ${data.size} entries")

            return syncNextNActivities(
                db,
                context,
                token,
                remaining - data.size,
                totalSynced + data.size,
                n
            )
        } catch (e: Exception) {
            if (e is NetworkError || e is TimeoutError) {
                return Result.retry()
            }

            if (e is ClientError) {
                val message = e.networkResponse.data.decodeToString()
                WHALELog.e(
                    TAG,
                    "Client error uploading sensor readings: $message with total $totalSynced and remaining $remaining"
                )
                Firebase.crashlytics.log("Client error uploading sensor readings: $message with total $totalSynced and remaining $remaining")
                return Result.failure()
            }

            WHALELog.e(
                TAG,
                "Error uploading sensor readings: $e, ${e.stackTraceToString()} with total $totalSynced and remaining $remaining"
            )
            Firebase.crashlytics.log("Error uploading sensor readings: $e, ${e.stackTraceToString()} with total $totalSynced and remaining $remaining")

            return Result.failure()
        }
    }
}

fun enqueueSensorReadingsUploadWorker(context: Context, token: String) {
    val data = workDataOf("token" to token)

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        // .setRequiresCharging(true) --> blocks too many attempts at synchronization
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
    tag: String,
    expedited: Boolean
) {
    val data = workDataOf("token" to token)

    val constraintsBuilder = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)

    if (!expedited) constraintsBuilder.setRequiresCharging(true)

    val uploadWorkRequestBuilder = OneTimeWorkRequestBuilder<SensorReadingsUploadWorker>()
        .addTag(tag)
        .setInputData(data)
        .setConstraints(constraintsBuilder.build())

    if (expedited) {
        uploadWorkRequestBuilder
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
    }

    WorkManager.getInstance(context).enqueue(uploadWorkRequestBuilder.build())
}