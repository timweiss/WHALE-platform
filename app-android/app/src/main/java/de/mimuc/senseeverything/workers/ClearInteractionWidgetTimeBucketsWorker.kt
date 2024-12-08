package de.mimuc.senseeverything.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mimuc.senseeverything.data.DataStoreManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit


@HiltWorker
class ClearInteractionWidgetTimeBucketsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dataStoreManager: DataStoreManager
) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val emptyMap = hashMapOf<String, Boolean>()
        dataStoreManager.setInteractionWidgetTimeBucket(emptyMap)

        return Result.success()
    }
}


fun hoursUntil(targetHour: Int): Long {
    val now = LocalDateTime.now()
    val todayTarget = now.toLocalDate().atTime(LocalTime.of(targetHour, 0))
    val targetTime = if (now.isAfter(todayTarget)) {
        // If the target time has already passed today, use tomorrow's target time
        todayTarget.plusDays(1)
    } else {
        todayTarget
    }
    return Duration.between(now, targetTime).toHours()
}

fun enqueueClearInteractionWidgetTimeBucketsWorker(context: Context) {
    val delay = hoursUntil(0).coerceAtLeast(1)

    val clearTimeBucketsRequest =
        PeriodicWorkRequestBuilder<ClearInteractionWidgetTimeBucketsWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.HOURS)
            .addTag("clearInteractionWidgetTimeBuckets")
            .build()

    // reset
    WorkManager.getInstance(context).cancelAllWorkByTag("clearInteractionWidgetTimeBuckets")

    WorkManager.getInstance(context).enqueue(clearTimeBucketsRequest)
}