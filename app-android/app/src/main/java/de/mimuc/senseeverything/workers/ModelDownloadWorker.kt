package de.mimuc.senseeverything.workers

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mimuc.senseeverything.api.ApiResources
import de.mimuc.senseeverything.api.model.ContentExtractionModel
import de.mimuc.senseeverything.contentextraction.DownloadFailures
import de.mimuc.senseeverything.contentextraction.DownloadResult
import de.mimuc.senseeverything.contentextraction.ModelDownloader
import de.mimuc.senseeverything.contentextraction.ModelStore
import de.mimuc.senseeverything.helpers.backgroundWorkForegroundInfo
import de.mimuc.senseeverything.helpers.setForegroundIfAllowed
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Downloads the content extraction model (2.6 GB) from the study backend, on Wi-Fi, resuming where a
 * previous attempt stopped. Runs as a foreground service when Android allows it; otherwise in
 * JobScheduler's time windows, continuing each time. See docs/content-extraction.md.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val UNIQUE_NAME = "contentExtractionModelDownload"
        const val KEY_TOKEN = "token"
        const val KEY_SHA256 = "sha256"
        const val KEY_SIZE_BYTES = "sizeBytes"
        const val PROGRESS_DOWNLOADED_BYTES = "downloadedBytes"
        const val PROGRESS_TOTAL_BYTES = "totalBytes"
        const val OUTPUT_ERROR = "error"
        private const val TAG = "ModelDownloadWorker"
        private const val NOTIFICATION_ID = 1016

        private val client = OkHttpClient.Builder()
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    override suspend fun doWork(): Result {
        val token = inputData.getString(KEY_TOKEN)
        val sha256 = inputData.getString(KEY_SHA256)
        val sizeBytes = inputData.getLong(KEY_SIZE_BYTES, -1)
        if (token.isNullOrEmpty() || sha256 == null || sizeBytes <= 0) {
            return Result.failure(workDataOf(OUTPUT_ERROR to "missing input"))
        }
        val model = ContentExtractionModel(sha256, sizeBytes)
        val root = ModelStore.root(applicationContext)
        if (ModelStore.isReady(root, model)) return Result.success()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        setForegroundIfAllowed(backgroundWorkForegroundInfo(NOTIFICATION_ID, applicationContext, notificationManager))

        val result = withContext(Dispatchers.IO) {
            ModelDownloader(client).download(
                url = ApiResources.contentExtractionModel(sha256),
                token = token,
                model = model,
                root = root,
                shouldContinue = { !isStopped },
                onProgress = { downloaded, total ->
                    setProgressAsync(workDataOf(PROGRESS_DOWNLOADED_BYTES to downloaded, PROGRESS_TOTAL_BYTES to total))
                }
            )
        }

        val failures = DownloadFailures(root)
        return when (result) {
            DownloadResult.Ready -> {
                WHALELog.i(TAG, "Model $sha256 downloaded and verified")
                failures.clear()
                Result.success()
            }
            is DownloadResult.Retry -> {
                WHALELog.w(TAG, "Model download paused, retrying later: ${result.reason}")
                Result.retry()
            }
            is DownloadResult.Failed -> {
                // counted: after DownloadFailures.MAX the extraction gives up on the model
                val state = failures.record(result.reason)
                WHALELog.e(TAG, "Model download failed (${state.count} of ${DownloadFailures.MAX}): ${result.reason}")
                Result.failure(workDataOf(OUTPUT_ERROR to result.reason))
            }
        }
    }
}

/**
 * Starts the model download unless one is already enqueued or running; after a finished one (also a
 * failed one) a new attempt starts, which is a no-op once the model is there.
 */
fun enqueueModelDownload(context: Context, token: String, model: ContentExtractionModel) {
    WorkManager.getInstance(context)
        .enqueueUniqueWork(ModelDownloadWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, modelDownloadRequest(token, model))
}

/** Also enqueued from the :extraction process through RemoteWorkManager, with the same unique name and policy. */
fun modelDownloadRequest(token: String, model: ContentExtractionModel): OneTimeWorkRequest {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED)
        .setRequiresStorageNotLow(true)
        .build()

    return OneTimeWorkRequestBuilder<ModelDownloadWorker>()
        .addTag(ModelDownloadWorker.UNIQUE_NAME)
        .setInputData(
            workDataOf(
                ModelDownloadWorker.KEY_TOKEN to token,
                ModelDownloadWorker.KEY_SHA256 to model.sha256,
                ModelDownloadWorker.KEY_SIZE_BYTES to model.sizeBytes
            )
        )
        .setConstraints(constraints)
        .build()
}
