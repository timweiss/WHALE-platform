package de.mimuc.senseeverything.workers

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.android.volley.ClientError
import com.android.volley.NetworkError
import com.android.volley.TimeoutError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.model.ema.uploadQuestionnaireAnswer
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.helpers.backgroundWorkForegroundInfo
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@HiltWorker
class QuestionnaireUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: AppDatabase
) :
    CoroutineWorker(appContext, workerParams) {

    private val notificationId = 1013
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    override suspend fun doWork(): Result {
        val questionnaireAnswers = inputData.getString("questionnaireAnswers") ?: ""
        val questionnaireId = inputData.getInt("questionnaireId", -1)
        val studyId = inputData.getInt("studyId", -1)
        val userToken = inputData.getString("userToken") ?: ""
        val pendingQuestionnaireId =
            inputData.getString("pendingQuestionnaireId")?.let { UUID.fromString(it) }

        setForeground(
            backgroundWorkForegroundInfo(notificationId, applicationContext, notificationManager)
        )

        if (questionnaireAnswers.isEmpty() || questionnaireId == -1 || studyId == -1 || userToken.isEmpty()) {
            return Result.failure()
        }

        return withContext(Dispatchers.IO) {
            try {
                val pendingQuestionnaire = getPendingQuestionnaire(pendingQuestionnaireId)

                if (pendingQuestionnaire == null) {
                    WHALELog.e("QuestionnaireUploadWorker", "Pending questionnaire not found for ID: $pendingQuestionnaireId")
                    return@withContext Result.failure()
                }

                val notificationTrigger = getNotificationTrigger(pendingQuestionnaire.notificationTriggerUid)

                val apiClient = ApiClient.getInstance(applicationContext)
                uploadQuestionnaireAnswer(
                    apiClient,
                    questionnaireAnswers,
                    questionnaireId,
                    studyId,
                    userToken,
                    pendingQuestionnaire,
                    notificationTrigger
                )

                // hint: cannot be deleted in case it is a source of another PendingQuestionnaire
                // deletePendingQuestionnaire(pendingQuestionnaireId)

                WHALELog.i("QuestionnaireUploadWorker", "Successfully uploaded questionnaire answers for pending questionnaire ID: $pendingQuestionnaireId")
                Result.success()
            } catch (e: Exception) {
                when (e) {
                    is NetworkError, is TimeoutError -> {
                        WHALELog.w("QuestionnaireUploadWorker", "Network error uploading questionnaire answers, will retry", e)
                        Result.retry()
                    }

                    is ClientError -> {
                        val message = e.networkResponse.data.decodeToString()
                        WHALELog.e("QuestionnaireUploadWorker", "Client error uploading questionnaire answers: $message", e)
                        Result.failure()
                    }

                    else -> {
                        WHALELog.e(
                            "QuestionnaireUploadWorker",
                            "Error uploading questionnaire answers: $e, ${e.stackTraceToString()}"
                        )
                        Result.failure()
                    }
                }
            }
        }
    }

    private fun getNotificationTrigger(id: UUID?): NotificationTrigger? {
        if (id == null) return null
        val notificationTrigger = database.notificationTriggerDao().getById(id)
        return notificationTrigger
    }

    private fun getPendingQuestionnaire(id: UUID?): PendingQuestionnaire? {
        if (id == null) return null
        val pendingQuestionnaire = database.pendingQuestionnaireDao().getById(id)
        return pendingQuestionnaire
    }

    private fun deletePendingQuestionnaire(id: UUID?) {
        if (id == null) return
        database.pendingQuestionnaireDao().deleteById(id)
    }
}

fun enqueueQuestionnaireUploadWorker(
    context: Context,
    questionnaireAnswers: String,
    questionnaireId: Int,
    studyId: Int,
    userToken: String,
    pendingQuestionnaireId: UUID?
) {
    val data = workDataOf(
        "questionnaireAnswers" to questionnaireAnswers,
        "questionnaireId" to questionnaireId,
        "studyId" to studyId,
        "userToken" to userToken,
        "pendingQuestionnaireId" to pendingQuestionnaireId.toString()
    )

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val uploadWorkRequest = OneTimeWorkRequestBuilder<QuestionnaireUploadWorker>()
        .setInputData(data)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueue(uploadWorkRequest)
}