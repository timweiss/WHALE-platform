package de.mimuc.senseeverything.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.accessibility.ApprovedScreenshots
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Deletes sensor readings that never got all their required upload flags within the sensor's
 * SensorUploadPolicy.clearAfterDays. Runs without a network constraint, so the retention limit holds
 * even when the upload worker (Wi-Fi only) doesn't run. Also deletes the images of deleted screenshot readings.
 */
@HiltWorker
class UnsatisfiedSensorReadingsCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: AppDatabase
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORKER_TAG = "UnsatisfiedSensorReadingsCleanupWorker"
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val deleted = database.logDataDao().deleteExpiredUnsatisfied(System.currentTimeMillis())
                WHALELog.i(WORKER_TAG, "Deleted $deleted expired readings with unsatisfied upload flags")
                val deletedImages = ApprovedScreenshots.deleteUnreferenced(applicationContext, database)
                WHALELog.i(WORKER_TAG, "Deleted $deletedImages approved screenshots without a reading")
                Result.success()
            } catch (e: Exception) {
                WHALELog.e(WORKER_TAG, "Error deleting expired unsatisfied readings: $e")
                Result.failure()
            }
        }
    }
}

fun enqueueUnsatisfiedSensorReadingsCleanupWorker(context: Context) {
    val request = PeriodicWorkRequestBuilder<UnsatisfiedSensorReadingsCleanupWorker>(1, TimeUnit.DAYS)
        .addTag(UnsatisfiedSensorReadingsCleanupWorker.WORKER_TAG)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        UnsatisfiedSensorReadingsCleanupWorker.WORKER_TAG,
        ExistingPeriodicWorkPolicy.KEEP,
        request
    )
}
