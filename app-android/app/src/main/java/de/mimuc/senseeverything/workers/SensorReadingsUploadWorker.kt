package de.mimuc.senseeverything.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.android.volley.NetworkError
import com.android.volley.TimeoutError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.LogData
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

    override suspend fun doWork(): Result {
        val db = database

        val token = inputData.getString("token") ?: ""

        if (token.isEmpty()) {
            return Result.failure()
        }

        return withContext(Dispatchers.IO) {
            try {
                syncNextNActivities(db, applicationContext, token,200)
                Result.success()
            } catch (e: Exception) {
                Result.retry()
            }
        }
    }

    private suspend fun syncNextNActivities(db: AppDatabase, context: Context, token: String, n: Int): Result {
        val data = db.logDataDao().getNextNUnsynced(n)
        if (data.isEmpty()) {
            return Result.success()
        }

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

            return syncNextNActivities(db, context, token, n)
        } catch (e: Exception) {
            if (e is NetworkError || e is TimeoutError) {
                return Result.retry()
            }

            Log.e(TAG, "Error uploading sensor readings: $e, ${e.stackTraceToString()}")
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