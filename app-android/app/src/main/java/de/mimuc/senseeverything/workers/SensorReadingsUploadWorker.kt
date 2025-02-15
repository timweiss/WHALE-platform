package de.mimuc.senseeverything.workers

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.android.volley.NetworkError
import com.android.volley.TimeoutError
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.LogData
import de.mimuc.senseeverything.helpers.backgroundWorkForegroundInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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
        val data = db.logDataDao().getNextNUnsynced(n)
        if (data.isEmpty()) {
            Log.i(TAG, "Completed Sensor Reading Sync")
            return Result.success()
        }

        setProgress(workDataOf("progress" to (totalSynced / remaining)))

        val jsonReadings = JSONArray()
        data.forEach {
            val o = JSONObject()
            o.put("sensorType", it.sensorName)
            o.put("timestamp", it.timestamp)
            o.put("data", it.data)
            jsonReadings.put(o)
        }

        val client = ApiClient.getInstance(context)
        val headers = mapOf("Authorization" to "Bearer $token")

        try {
            val response = suspendCoroutine { continuation ->
                client.postArray(
                    "https://sisensing.medien.ifi.lmu.de/v1/reading/batch",
                    jsonReadings,
                    headers,
                    { response ->
                        continuation.resume(response)
                    },
                    { error ->
                        continuation.resumeWithException(error)
                    })
            }

            db.logDataDao().deleteLogData(*data.toTypedArray<LogData>())
            Log.i(TAG, "batch synced successful, removed ${data.size} entries")

            return syncNextNActivities(db, context, token, remaining - data.size, totalSynced + data.size, n)
        } catch (e: Exception) {
            if (e is NetworkError || e is TimeoutError) {
                return Result.retry()
            }

            Log.e(TAG, "Error uploading sensor readings: $e, ${e.stackTraceToString()} with total $totalSynced and remaining $remaining")
            Firebase.crashlytics.log("Error uploading sensor readings: $e, ${e.stackTraceToString()} with total $totalSynced and remaining $remaining")

            return Result.failure()
        }
    }
}

fun enqueueSensorReadingsUploadWorker(context: Context, token: String) {
    val data = workDataOf("token" to token)

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresCharging(true)
        .build()

    val uploadWorkRequest = PeriodicWorkRequestBuilder<SensorReadingsUploadWorker>(1, TimeUnit.DAYS)
        .addTag("readingsUpload")
        .setInputData(data)
        .setConstraints(constraints)
        .build()

    // reset
    WorkManager.getInstance(context).cancelAllWorkByTag("readingsUpload")

    WorkManager.getInstance(context).enqueue(uploadWorkRequest)
}

fun enqueueSingleSensorReadingsUploadWorker(context: Context, token: String, tag: String) {
    val data = workDataOf("token" to token)

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresCharging(true)
        .build()

    val uploadWorkRequest = OneTimeWorkRequestBuilder<SensorReadingsUploadWorker>()
        .addTag(tag)
        .setInputData(data)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueue(uploadWorkRequest)
}