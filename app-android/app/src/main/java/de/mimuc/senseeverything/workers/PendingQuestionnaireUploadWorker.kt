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
import androidx.work.workDataOf
import com.android.volley.NetworkError
import com.android.volley.TimeoutError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.ema.QuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.api.model.ema.questionnaireJson
import de.mimuc.senseeverything.api.model.ema.uploadQuestionnaireAnswer
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.helpers.backgroundWorkForegroundInfo
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.TimeUnit

@HiltWorker
class PendingQuestionnaireUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: AppDatabase,
    private val dataStoreManager: DataStoreManager
) :
    CoroutineWorker(appContext, workerParams) {

    private val notificationId = 1014
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    override suspend fun doWork(): Result {
        val studyId = inputData.getInt("studyId", -1)
        val userToken = inputData.getString("userToken") ?: ""

        if (studyId == -1 || userToken.isEmpty()) {
            return Result.failure()
        }

        setForeground(
            backgroundWorkForegroundInfo(notificationId, applicationContext, notificationManager)
        )

        return withContext(Dispatchers.IO) {
            try {
                val pendingQuestionnaires = database.pendingQuestionnaireDao().getAll()

                if (pendingQuestionnaires.isEmpty()) {
                    WHALELog.i("PendingQuestionnaireUploadWorker", "No pending questionnaires to upload.")
                    return@withContext Result.success()
                }

                WHALELog.i("PendingQuestionnaireUploadWorker", "Found ${pendingQuestionnaires.size} pending questionnaires to upload.")
                val apiClient = ApiClient.getInstance(applicationContext)

                val notificationTriggers = database.notificationTriggerDao().getAll().associateBy { it.uid }

                for (pendingQuestionnaire in pendingQuestionnaires) {
                    if (isStopped) {
                        WHALELog.w("PendingQuestionnaireUploadWorker", "Work cancelled, stopping further sync")
                        return@withContext Result.success()
                    }

                    WHALELog.i("PendingQuestionnaireUploadWorker", "Uploading pending questionnaire: ${pendingQuestionnaire.uid}")
                    val questionnaire = fullQuestionnaireJson.decodeFromString<FullQuestionnaire>(pendingQuestionnaire.questionnaireJson)

                    try {
                        uploadQuestionnaireAnswer(
                            apiClient,
                            pendingQuestionnaire.elementValuesJson ?: "[]",
                            questionnaire.questionnaire.id,
                            studyId,
                            userToken,
                            pendingQuestionnaire,
                            notificationTriggers.getOrDefault(pendingQuestionnaire.notificationTriggerUid, null)
                        )
                    } catch (e: Exception) {
                        WHALELog.e("PendingQuestionnaireUploadWorker", "Error uploading pending questionnaire ${pendingQuestionnaire.uid}: $e")
                        // decide whether to continue or return failure based on error type
                        if (e is NetworkError || e is TimeoutError) {
                            return@withContext Result.retry()
                        } else {
                            return@withContext Result.failure()
                        }
                    }
                }

                synchronizeLeftoverNotificationTriggers(notificationTriggers, pendingQuestionnaires)

                Result.success()
            } catch (e: Exception) {
                if (e is NetworkError || e is TimeoutError) {
                    Result.retry()
                } else {
                    WHALELog.e("QuestionnaireUploadWorker", "Error uploading questionnaire answers: $e, ${e.stackTraceToString()}")
                    Result.failure()
                }
            }
        }
    }

    suspend fun synchronizeLeftoverNotificationTriggers(notificationTriggers: Map<UUID, NotificationTrigger>, pendingQuestionnaires: List<PendingQuestionnaire>): Result {
        // remove triggers present in pendingQuestionnaires
        val remainingNotificationTriggers = notificationTriggers.toMutableMap()
        for (pending in pendingQuestionnaires) {
            if (pending.notificationTriggerUid != null) {
                remainingNotificationTriggers.remove(pending.notificationTriggerUid)
            }
        }

        if (remainingNotificationTriggers.isEmpty()) return Result.success()

        WHALELog.i("PendingQuestionnaireUploadWorker", "Synchronizing ${remainingNotificationTriggers.size} leftover notification triggers without pending questionnaires.")
        for ((_, trigger) in remainingNotificationTriggers) {
            if (isStopped) {
                WHALELog.w("PendingQuestionnaireUploadWorker", "Work cancelled while uploading triggers, stopping further sync")
                return Result.success()
            }

            // create dummy pending questionnaire for each remaining trigger
            val pendingQuestionnaireId = PendingQuestionnaire.createEntry(
                database,
                dataStoreManager,
                trigger = questionnaireJson.decodeFromString<QuestionnaireTrigger>(trigger.triggerJson),
                notificationTriggerUid = trigger.uid
            )?.uid

            if (pendingQuestionnaireId == null) {
                WHALELog.e("PendingQuestionnaireUploadWorker", "Failed to create pending questionnaire for trigger: ${trigger.uid}")
                continue
            }

            val pendingQuestionnaire = database.pendingQuestionnaireDao().getById(pendingQuestionnaireId)
            if (pendingQuestionnaire == null) {
                WHALELog.e("PendingQuestionnaireUploadWorker", "Failed to retrieve created pending questionnaire for trigger: ${trigger.uid}")
                continue
            }

            val questionnaire = fullQuestionnaireJson.decodeFromString<FullQuestionnaire>(pendingQuestionnaire.questionnaireJson)
            try {
                uploadQuestionnaireAnswer(
                    ApiClient.getInstance(applicationContext),
                    "[]",
                    questionnaire.questionnaire.id,
                    inputData.getInt("studyId", -1),
                    inputData.getString("userToken") ?: "",
                    pendingQuestionnaire,
                    trigger
                )
                WHALELog.i("PendingQuestionnaireUploadWorker", "Successfully uploaded dummy questionnaire answers for leftover NotificationTrigger: ${trigger.uid}")
            } catch (e: Exception) {
                WHALELog.e("PendingQuestionnaireUploadWorker", "Error uploading dummy questionnaire for leftover trigger ${trigger.uid}: $e")

                if (e is NetworkError || e is TimeoutError) {
                    return Result.retry()
                } else {
                    return Result.failure()
                }
            }
        }

        // clear all expired pending questionnaires
        database.pendingQuestionnaireDao().deleteExpired(System.currentTimeMillis())

        return Result.success()
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

fun enqueueSinglePendingQuestionnaireUploadWorker(
    context: Context,
    studyId: Int,
    userToken: String,
    tag: String
) {
    val data = workDataOf(
        "studyId" to studyId,
        "userToken" to userToken
    )

    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    val uploadWorkRequest = androidx.work.OneTimeWorkRequestBuilder<PendingQuestionnaireUploadWorker>()
        .addTag(tag)
        .setInputData(data)
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueue(uploadWorkRequest)
}