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

fun enqueueClearInteractionWidgetTimeBucketsWorker(context: Context) {
    val delay = WorkerHelpers.hoursUntil(0).coerceAtLeast(1)

    val clearTimeBucketsRequest =
        PeriodicWorkRequestBuilder<ClearInteractionWidgetTimeBucketsWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.HOURS)
            .addTag("clearInteractionWidgetTimeBuckets")
            .build()

    // reset
    WorkManager.getInstance(context).cancelAllWorkByTag("clearInteractionWidgetTimeBuckets")

    WorkManager.getInstance(context).enqueue(clearTimeBucketsRequest)
}