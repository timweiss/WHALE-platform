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
import de.mimuc.senseeverything.service.esm.NotificationPushHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

@HiltWorker
class StaleUnsyncedSensorReadingsCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: AppDatabase
) : CoroutineWorker(appContext, workerParams) {

    private val TAG = "StaleUnsyncedSensorReadingsCheckWorker"

    companion object {
        const val WORKER_TAG = "StaleUnsyncedSensorReadingsCheckWorker"
        val STALE_DURATION = 2.days.inWholeMilliseconds
    }

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val twoDaysAgo = System.currentTimeMillis() - STALE_DURATION
                val oldUnsyncedCount = database.logDataDao().getUnsyncedCountBefore(twoDaysAgo)

                WHALELog.d(TAG, "Old data check: found $oldUnsyncedCount unsynced items older than ${STALE_DURATION.milliseconds.inWholeHours} hours")

                if (oldUnsyncedCount > 0) {
                    val notificationHelper = NotificationPushHelper(applicationContext)
                    notificationHelper.sendOldDataReminderNotification()
                    WHALELog.i(TAG, "Sent old data reminder notification")
                }

                Result.success()
            } catch (e: Exception) {
                WHALELog.e(TAG, "Error checking for old data: $e")
                Result.failure()
            }
        }
    }
}

fun enqueueOldDataCheckWorker(context: Context, targetTime: LocalTime) {
    val now = LocalDateTime.now()
    var nextRun = now.with(targetTime)

    // If time has already passed today, schedule for tomorrow
    if (now.isAfter(nextRun)) {
        nextRun = nextRun.plusDays(1)
    }

    val initialDelayMillis = nextRun.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() -
                             now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    WHALELog.i(StaleUnsyncedSensorReadingsCheckWorker.WORKER_TAG, "Scheduling old data check worker with initial delay of ${initialDelayMillis.milliseconds.inWholeHours} hours")

    val checkWorkRequest = PeriodicWorkRequestBuilder<StaleUnsyncedSensorReadingsCheckWorker>(1, TimeUnit.DAYS)
        .addTag(StaleUnsyncedSensorReadingsCheckWorker.WORKER_TAG)
        .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        StaleUnsyncedSensorReadingsCheckWorker.WORKER_TAG,
        ExistingPeriodicWorkPolicy.KEEP,
        checkWorkRequest
    )
}