package de.mimuc.senseeverything.workers

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class EndStudyWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase
) :
    CoroutineWorker(appContext, workerParams) {

    val TAG = "EndStudyWorker"

    override suspend fun doWork(): Result {
        // mark study as ended
        dataStoreManager.saveStudyEnded(true)
        Log.i(TAG, "Marked study as ended")

        // stop LogService
        try {
            val logServiceIntent =
                Intent(applicationContext, de.mimuc.senseeverything.service.LogService::class.java)
            applicationContext.stopService(logServiceIntent)
            Log.i(TAG, "Stopped LogService")
        } catch (e: Exception) {
            // already stopped, ignore
            Log.w(TAG, "Error stopping LogService: $e")
        }

        // upload and clear all data and jobs
        WorkManager.getInstance(applicationContext).cancelAllWorkByTag("readingsUpload")
        WorkManager.getInstance(applicationContext).cancelAllWorkByTag("updateQuestionnaires")
        Log.i(TAG, "Cancelled all jobs")

        val token = dataStoreManager.tokenFlow.first()
        enqueueSingleSensorReadingsUploadWorker(applicationContext, token, "finalReadingsUpload")
        Log.i(TAG, "Scheduled final sensor readings upload")

        return Result.success()
    }
}

fun enqueueEndStudyWorker(context: Context, afterDays: Int) {
    val updateQuestionnairesRequest = OneTimeWorkRequestBuilder<EndStudyWorker>()
        .setInitialDelay(afterDays.toLong(), TimeUnit.DAYS)
        .addTag("endStudy")
        .build()

    // reset
    WorkManager.getInstance(context).cancelAllWorkByTag("endStudy")

    WorkManager.getInstance(context).enqueue(updateQuestionnairesRequest)
}