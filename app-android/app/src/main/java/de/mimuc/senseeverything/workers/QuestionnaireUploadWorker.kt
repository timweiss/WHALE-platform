package de.mimuc.senseeverything.workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.android.volley.NetworkError
import com.android.volley.TimeoutError
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.ApiResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class QuestionnaireUploadWorker(appContext: Context, workerParams: WorkerParameters):
    CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val questionnaireAnswers = inputData.getString("questionnaireAnswers") ?: ""
        val questionnaireId = inputData.getInt("questionnaireId", -1)
        val studyId = inputData.getInt("studyId", -1)
        val userToken = inputData.getString("userToken") ?: ""

        if (questionnaireAnswers.isEmpty() || questionnaireId == -1 || studyId == -1 || userToken.isEmpty()) {
            return Result.failure()
        }

        return withContext(Dispatchers.IO) {
            try {
                upload(questionnaireAnswers, questionnaireId, studyId, userToken)
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

    private suspend fun upload(answers: String, questionnaireId: Int, studyId: Int, userToken: String) {
        val client = ApiClient.getInstance(applicationContext)
        val json = JSONObject()
        json.put("answers", JSONArray(answers))
        val headers = mapOf("Authorization" to "Bearer $userToken")
        val response = suspendCoroutine { continuation ->
            client.post(
                ApiResources.questionnaireAnswer(studyId, questionnaireId),
                json,
                headers,
                { response ->
                    // Success
                    continuation.resume(response)
                },
                { error ->
                    // Error
                    continuation.resumeWithException(error)
                })
        }
    }
}

fun enqueueQuestionnaireUploadWorker(context: Context, questionnaireAnswers: String, questionnaireId: Int, studyId: Int, userToken: String) {
    val data = workDataOf(
        "questionnaireAnswers" to questionnaireAnswers,
        "questionnaireId" to questionnaireId,
        "studyId" to studyId,
        "userToken" to userToken
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