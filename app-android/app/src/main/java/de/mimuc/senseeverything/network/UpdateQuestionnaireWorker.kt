package de.mimuc.senseeverything.network

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.room.Update
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.fetchAndPersistQuestionnaires
import de.mimuc.senseeverything.data.DataStoreManager
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class UpdateQuestionnaireWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dataStoreManager: DataStoreManager):
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val studyId = dataStoreManager.studyIdFlow.first()
        val fullQuestionnaires = fetchAndPersistQuestionnaires(studyId, dataStoreManager, ApiClient.getInstance(applicationContext))

        return if (fullQuestionnaires.isNotEmpty()) {
            Result.success()
        } else {
            Result.failure()
        }
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