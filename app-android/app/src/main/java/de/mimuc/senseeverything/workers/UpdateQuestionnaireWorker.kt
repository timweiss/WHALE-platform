package de.mimuc.senseeverything.workers

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.fetchAndPersistQuestionnaires
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.persistQuestionnaireElementContent
import de.mimuc.senseeverything.helpers.backgroundWorkForegroundInfo
import de.mimuc.senseeverything.helpers.setForegroundIfAllowed
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

@HiltWorker
class UpdateQuestionnaireWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dataStoreManager: DataStoreManager):
    CoroutineWorker(appContext, workerParams) {

    private val notificationId = 1015
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    override suspend fun doWork(): Result {
        WHALELog.i(TAG, "started (runAttemptCount=$runAttemptCount)")
        try {
            val studyId = dataStoreManager.studyIdFlow.first()

            setForegroundIfAllowed(
                backgroundWorkForegroundInfo(notificationId, applicationContext, notificationManager)
            )

            val fullQuestionnaires = fetchAndPersistQuestionnaires(studyId, dataStoreManager, ApiClient.getInstance(applicationContext))
            persistQuestionnaireElementContent(applicationContext, fullQuestionnaires)

            return if (fullQuestionnaires.isNotEmpty()) {
                Result.success()
            } else {
                Result.failure()
            }
        } catch (e: CancellationException) {
            // stopReason is one of WorkInfo.STOP_REASON_*, e.g. 6 = STOP_REASON_CONSTRAINT_CHARGING
            WHALELog.w(TAG, "stopped by WorkManager (stopReason=$stopReason, runAttemptCount=$runAttemptCount)")
            throw e
        }
    }

    companion object {
        private const val TAG = "UpdateQuestionnaireWorker"
    }
}

fun enqueueUpdateQuestionnaireWorker(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .setRequiresCharging(true)
        .build()

    val updateQuestionnairesRequest = PeriodicWorkRequestBuilder<UpdateQuestionnaireWorker>(1, TimeUnit.DAYS)
        .addTag("updateQuestionnaires")
        .setConstraints(constraints)
        .build()

    // reset
    WorkManager.getInstance(context).cancelAllWorkByTag("updateQuestionnaires")

    WorkManager.getInstance(context).enqueue(updateQuestionnairesRequest)
}