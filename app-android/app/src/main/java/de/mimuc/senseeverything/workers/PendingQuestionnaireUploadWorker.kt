package de.mimuc.senseeverything.workers

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
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
import de.mimuc.senseeverything.api.model.ema.makeQuestionnaireFromJson
import de.mimuc.senseeverything.api.model.ema.uploadQuestionnaireAnswer
import de.mimuc.senseeverything.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@HiltWorker
class PendingQuestionnaireUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: AppDatabase
) :
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val studyId = inputData.getInt("studyId", -1)
        val userToken = inputData.getString("userToken") ?: ""

        if (studyId == -1 || userToken.isEmpty()) {
            return Result.failure()
        }

        return withContext(Dispatchers.IO) {
            try {
                val pendingQuestionnaires = database.pendingQuestionnaireDao().getAll()

                if (pendingQuestionnaires.isEmpty()) {
                    Log.i("PendingQuestionnaireUploadWorker", "No pending questionnaires to upload.")
                    return@withContext Result.success()
                }

                Log.i("PendingQuestionnaireUploadWorker", "Found ${pendingQuestionnaires.size} pending questionnaires to upload.")
                val apiClient = ApiClient.getInstance(applicationContext)

                for (pendingQuestionnaire in pendingQuestionnaires) {
                    Log.i("PendingQuestionnaireUploadWorker", "Uploading pending questionnaire: ${pendingQuestionnaire.uid}")
                    val questionnaire = makeQuestionnaireFromJson(JSONObject(pendingQuestionnaire.questionnaireJson))
                    uploadQuestionnaireAnswer(
                        apiClient,
                        pendingQuestionnaire.elementValuesJson ?: "",
                        questionnaire.id,
                        studyId,
                        userToken,
                        pendingQuestionnaire
                    )
                }

                // clear all expired pending questionnaires
                database.pendingQuestionnaireDao().deleteExpired(System.currentTimeMillis())

                Result.success()
            } catch (e: Exception) {
                if (e is NetworkError || e is TimeoutError) {
                    Result.retry()
                } else {
                    Log.d("QuestionnaireUploadWorker", "Error uploading questionnaire answers: $e, ${e.stackTraceToString()}")
                    Result.failure()
                }
            }
        }
    }
}

fun enqueuePendingQuestionnaireUploadWorker(context: Context, studyId: Int, userToken: String) {
    val data = workDataOf(
        "studyId" to studyId,
        "userToken" to userToken
    )

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val delayHours = WorkerHelpers.hoursUntil(0).coerceAtLeast(1)

    val uploadWorkRequest = PeriodicWorkRequestBuilder<PendingQuestionnaireUploadWorker>(1, TimeUnit.DAYS)
        .addTag("pendingQuestionnaireUpload")
        .setInputData(data)
        .setConstraints(constraints)
        .setInitialDelay(delayHours, TimeUnit.HOURS)
        .build()

    WorkManager.getInstance(context).enqueue(uploadWorkRequest)
}