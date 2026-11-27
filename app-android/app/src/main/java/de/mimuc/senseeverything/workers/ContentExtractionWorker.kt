package de.mimuc.senseeverything.workers

import android.app.NotificationManager
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteCoroutineWorker
import androidx.work.multiprocess.RemoteListenableWorker
import androidx.work.multiprocess.RemoteWorkManager
import androidx.work.multiprocess.RemoteWorkerService
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mimuc.senseeverything.api.model.ContentExtractionConfig
import de.mimuc.senseeverything.contentextraction.ContentExtractionRunReading
import de.mimuc.senseeverything.contentextraction.DownloadFailures
import de.mimuc.senseeverything.contentextraction.ExtractionRunner
import de.mimuc.senseeverything.contentextraction.ExtractionWindow
import de.mimuc.senseeverything.contentextraction.leavesNoBacklog
import de.mimuc.senseeverything.contentextraction.LiteRtExtractionEngine
import de.mimuc.senseeverything.contentextraction.Measurements
import de.mimuc.senseeverything.contentextraction.ModelStore
import de.mimuc.senseeverything.contentextraction.OUTCOME_NOTHING_TO_DO
import de.mimuc.senseeverything.contentextraction.RULES_VERSION
import de.mimuc.senseeverything.contentextraction.RoomExtractionStore
import de.mimuc.senseeverything.contentextraction.RunState
import de.mimuc.senseeverything.contentextraction.SENSOR_CONTENT_EXTRACTION_RUN
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.StudyState
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.LogData
import de.mimuc.senseeverything.helpers.backgroundWorkForegroundInfo
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Hosts [ContentExtractionWorker] in the :extraction process (see AndroidManifest.xml). */
class ContentExtractionWorkerService : RemoteWorkerService()

/**
 * Extracts content from held-back UI trees with the on-device model, in the :extraction process.
 * Scheduled while charging and idle (not as a foreground service: with a charging constraint that
 * loops, see the questionnaire worker), or started by hand as a foreground service. See
 * docs/content-extraction.md.
 */
@HiltWorker
class ContentExtractionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val database: AppDatabase,
    private val dataStore: DataStoreManager
) : RemoteCoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_TRIGGER = "trigger"
        const val TRIGGER_SCHEDULED = "scheduled"
        const val TRIGGER_MANUAL = "manual"
        const val UNIQUE_SCHEDULED = "contentExtractionScheduled"
        const val UNIQUE_MANUAL = "contentExtractionManual"
        const val UNIQUE_FINAL_UPLOAD = "contentExtractionFinalUpload"
        const val TAG = "ContentExtractionWorker"
        private const val NOTIFICATION_ID = 1017
    }

    override suspend fun doRemoteWork(): Result {
        val trigger = inputData.getString(KEY_TRIGGER) ?: TRIGGER_SCHEDULED
        val manual = trigger == TRIGGER_MANUAL
        val config = dataStore.studyFlow.first()?.contentExtraction
        if (config == null) {
            WHALELog.i(TAG, "The study has no content extraction")
            return Result.success()
        }
        if (!manual && !ExtractionWindow.contains(LocalTime.now())) {
            // scheduled runs only at night; this costs a process start and a DataStore read
            return Result.success()
        }

        val root = ModelStore.root(applicationContext)
        if (!ModelStore.isReady(root, config.model) && !DownloadFailures(root).exhausted()) {
            // also after a failed download: KEEP only adds one if none is pending
            WHALELog.i(TAG, "Model not there yet, waiting for its download")
            withContext(Dispatchers.IO) { ensureModelDownload(config) }
            return Result.success()
        }

        if (manual) withContext(Dispatchers.IO) { setForegroundIfPossible() }

        // a stop must not cancel this: the runner notices it, ends the running call and still
        // writes the run reading; the database calls are blocking
        withContext(NonCancellable + Dispatchers.IO) {
            val lock = RunLock.tryAcquire(File(root, "run.lock"))
            if (lock == null) {
                WHALELog.i(TAG, "Another extraction run is active")
            } else {
                lock.use {
                    val outcome = runExtraction(config, root, trigger)
                    if (!manual && leavesNoBacklog(outcome)) finishIfStudyEnded()
                }
            }
        }
        return Result.success()
    }

    /** Returns the run's outcome. */
    private fun runExtraction(config: ContentExtractionConfig, root: File, trigger: String): String {
        val runId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val cpuStart = Measurements.cpuMs()
        val peakRssReset = Measurements.resetPeakRss()
        val batteryStart = Measurements.battery(applicationContext)
        val thermalStart = Measurements.thermalStatus(applicationContext)

        val cacheDir = File(applicationContext.cacheDir, "litertlm").apply { mkdirs() }
        val runner = ExtractionRunner(
            config = config,
            store = RoomExtractionStore(database),
            state = RunState(File(root, "run-state.json")),
            openEngine = { LiteRtExtractionEngine.open(ModelStore.modelFile(root, config.model.sha256), cacheDir) },
            runId = runId
        )
        val summary = if (ModelStore.isReady(root, config.model)) {
            val manual = trigger == TRIGGER_MANUAL
            runner.run(shouldContinue = { !isStopped && (manual || ExtractionWindow.contains(LocalTime.now())) })
        } else {
            val failures = DownloadFailures(root).read()
            runner.flagWithoutModel("the model download failed ${failures.count} times, last: ${failures.lastReason}")
        }
        if (summary.outcome == OUTCOME_NOTHING_TO_DO) return summary.outcome

        val reading = ContentExtractionRunReading(
            runId = runId,
            trigger = trigger,
            outcome = summary.outcome,
            startedAt = startedAt,
            endedAt = System.currentTimeMillis(),
            modelSha256 = config.model.sha256,
            runtime = LiteRtExtractionEngine.RUNTIME,
            rulesVersion = RULES_VERSION,
            sampleRate = config.sampleRate,
            backend = summary.backend,
            gpuError = summary.gpuError,
            engineInitMs = summary.engineInitMs,
            engineErrors = summary.engineErrors,
            rowsHandled = summary.rowsHandled,
            rowsFlaggedWithoutExtraction = summary.rowsFlaggedWithoutExtraction,
            rowsCrashed = summary.rowsCrashed,
            rowsUndecodable = summary.rowsUndecodable,
            snapshotsSeen = summary.snapshotsSeen,
            snapshotsOfConfiguredApps = summary.snapshotsOfConfiguredApps,
            snapshotsExtracted = summary.snapshotsExtracted,
            calls = summary.calls,
            callErrors = summary.callErrors,
            cpuMs = Measurements.cpuMs() - cpuStart,
            peakRssKb = Measurements.peakRssKb(),
            peakRssReset = peakRssReset,
            batteryStart = batteryStart,
            batteryEnd = Measurements.battery(applicationContext),
            thermalStatusStart = thermalStart,
            thermalStatusEnd = Measurements.thermalStatus(applicationContext),
            stopReason = if (isStopped) stopReason else null,
            device = Measurements.device(applicationContext)
        )
        val json = Json.encodeToString(reading)
        database.logDataDao().insert(LogData(reading.endedAt, SENSOR_CONTENT_EXTRACTION_RUN, json))
        // for the debug screen: the reading is deleted once it is uploaded
        runCatching { ModelStore.lastRunFile(root).writeText(json) }
        WHALELog.i(
            TAG, "Run $runId: ${summary.outcome}, ${summary.rowsHandled} rows, ${summary.snapshotsExtracted} snapshots, " +
                    "${summary.calls} calls (${summary.callErrors} errors), backend ${summary.backend}"
        )
        return summary.outcome
    }

    /**
     * After the study end the scheduled runs go on until the last UI trees are handled; then the
     * trees and readings are uploaded once more and the extraction ends.
     */
    private suspend fun finishIfStudyEnded() {
        if (dataStore.studyStateFlow.first() != StudyState.ENDED) return
        val token = dataStore.tokenFlow.first()
        try {
            val workManager = RemoteWorkManager.getInstance(applicationContext)
            workManager.enqueueUniqueWork(
                UNIQUE_FINAL_UPLOAD,
                ExistingWorkPolicy.KEEP,
                singleSensorReadingsUploadRequest(token, UploadWorkTag.FINAL_UPLOAD_AFTER_EXTRACTION, expedited = false)
            ).get(30, TimeUnit.SECONDS)
            workManager.cancelUniqueWork(UNIQUE_SCHEDULED).get(30, TimeUnit.SECONDS)
            WHALELog.i(TAG, "Study ended and all UI trees handled: final upload enqueued, extraction ends")
        } catch (e: Exception) {
            WHALELog.e(TAG, "Could not finish the extraction after the study end: $e")
        }
    }

    private suspend fun ensureModelDownload(config: ContentExtractionConfig) {
        val token = dataStore.tokenFlow.first()
        try {
            // this process isn't WorkManager's default process, so the main process enqueues it
            RemoteWorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(ModelDownloadWorker.UNIQUE_NAME, ExistingWorkPolicy.KEEP, modelDownloadRequest(token, config.model))
                .get(30, TimeUnit.SECONDS)
        } catch (e: Exception) {
            WHALELog.e(TAG, "Could not enqueue the model download: $e")
        }
    }

    private fun setForegroundIfPossible() {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            setForegroundAsync(backgroundWorkForegroundInfo(NOTIFICATION_ID, applicationContext, notificationManager))
                .get(30, TimeUnit.SECONDS)
        } catch (e: Exception) {
            WHALELog.w(TAG, "Could not run as foreground service, continuing in background: $e")
        }
    }
}

/** A lock file, so a manual and a scheduled run never load two engines. Works across processes. */
private class RunLock private constructor(private val file: RandomAccessFile, private val lock: java.nio.channels.FileLock) : Closeable {
    companion object {
        fun tryAcquire(path: File): RunLock? {
            path.parentFile?.mkdirs()
            val file = RandomAccessFile(path, "rw")
            val lock = try {
                file.channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null // held by this process
            }
            if (lock == null) {
                file.close()
                return null
            }
            return RunLock(file, lock)
        }
    }

    override fun close() {
        lock.release()
        file.close()
    }
}

private fun extractionInput(context: Context, trigger: String) = workDataOf(
    // RemoteListenableWorker binds to this service, which runs the worker in :extraction
    RemoteListenableWorker.ARGUMENT_PACKAGE_NAME to context.packageName,
    RemoteListenableWorker.ARGUMENT_CLASS_NAME to ContentExtractionWorkerService::class.java.name,
    ContentExtractionWorker.KEY_TRIGGER to trigger
)

/** Every 15 minutes while the phone is charging, idle and the battery isn't low; each run continues the backlog. */
fun enqueueScheduledContentExtraction(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiresCharging(true)
        .setRequiresDeviceIdle(true)
        .setRequiresBatteryNotLow(true)
        .build()
    val request = PeriodicWorkRequestBuilder<ContentExtractionWorker>(15, TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setInputData(extractionInput(context, ContentExtractionWorker.TRIGGER_SCHEDULED))
        .addTag(ContentExtractionWorker.TAG)
        .build()
    WorkManager.getInstance(context)
        .enqueueUniquePeriodicWork(ContentExtractionWorker.UNIQUE_SCHEDULED, ExistingPeriodicWorkPolicy.KEEP, request)
}

/** When the participant cancels: no extraction, no download. (At the study end it goes on, see finishIfStudyEnded.) */
fun cancelContentExtraction(context: Context) {
    val workManager = WorkManager.getInstance(context)
    workManager.cancelUniqueWork(ContentExtractionWorker.UNIQUE_SCHEDULED)
    workManager.cancelUniqueWork(ContentExtractionWorker.UNIQUE_MANUAL)
    workManager.cancelUniqueWork(ModelDownloadWorker.UNIQUE_NAME)
}

/** A run now, without constraints, as a foreground service (e.g. on battery, to measure power). */
fun enqueueManualContentExtraction(context: Context) {
    val request = OneTimeWorkRequestBuilder<ContentExtractionWorker>()
        .setInputData(extractionInput(context, ContentExtractionWorker.TRIGGER_MANUAL))
        .addTag(ContentExtractionWorker.TAG)
        .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork(ContentExtractionWorker.UNIQUE_MANUAL, ExistingWorkPolicy.KEEP, request)
}
