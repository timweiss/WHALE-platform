package de.mimuc.senseeverything.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.LogData
import de.mimuc.senseeverything.workers.conversation.VadReader
import de.mimuc.senseeverything.workers.conversation.VadReader.Companion.calculateLength
import de.mimuc.senseeverything.workers.conversation.VadReader.Companion.calculateSpeechPercentage
import de.mimuc.senseeverything.workers.conversation.WebRTCReader
import de.mimuc.senseeverything.workers.conversation.YAMNetReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
import java.io.File
import java.util.Locale

@HiltWorker
class ConversationDetectionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase
) :
    CoroutineWorker(appContext, workerParams) {

    val TAG = "SpeechDetectionWorker"

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as
                NotificationManager

    private val notificationId = 1011

    override suspend fun doWork(): Result {
        val filename = inputData.getString("filename") ?: return Result.failure()
        val timestamp = inputData.getLong("timestamp", 0)

        setForeground(createForegroundInfo(applicationContext.getString(R.string.speech_detection_progress)))
        run(filename, timestamp)

        return Result.success()
    }

    private suspend fun run(filename: String, timestamp: Long) {
        runDetection(filename, timestamp, WebRTCReader())
        runDetection(filename, timestamp, YAMNetReader())
        deleteFile(filename)
    }

    private suspend fun runDetection(filename: String, timestamp: Long, reader: VadReader) {
        val segments = reader.detect(filename, applicationContext)
        val speechPercentage = calculateSpeechPercentage(segments)
        val lengthInSeconds = calculateLength(segments, 44100, 16)

        val log = String.format(Locale.GERMAN, "%.2f;%2f", lengthInSeconds, speechPercentage)

        val name = reader.TAG
        Log.d(TAG, "speech detected in $name audio $log")

        logSpeechDetectionResult(name, timestamp, log)
    }

    private suspend fun logSpeechDetectionResult(type: String, timestamp: Long, line: String) {
        (Dispatchers.IO) {
            database.logDataDao().insertAll(LogData(timestamp, "Conversation ${type}", line))
        }
    }

    private fun deleteFile(filename: String) {
        val existing = File(filename)
        if (existing.exists()) {
            existing.delete()
        }
    }

    private fun createForegroundInfo(progress: String): ForegroundInfo {
        val id = applicationContext.getString(R.string.speech_detection_channel_id)
        val channelName = applicationContext.getString(R.string.speech_detection_channel_name)
        val title = applicationContext.getString(R.string.speech_detection_title)

        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(id, channelName)
        }

        val notification = NotificationCompat.Builder(applicationContext, id)
            .setContentTitle(title)
            .setTicker(title)
            .setContentText(progress)
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .build()

        return ForegroundInfo(notificationId, notification)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createChannel(id: String, channelName: String): String {
        val chan = NotificationChannel(id, channelName, NotificationManager.IMPORTANCE_NONE)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        checkNotNull(notificationManager)
        notificationManager.createNotificationChannel(chan)

        return id
    }
}


fun enqueueConversationDetectionWorker(context: Context, filename: String, timestamp: Long) {
    val data = workDataOf(
        "filename" to filename,
        "timestamp" to timestamp
    )

    val uploadWorkRequest = OneTimeWorkRequestBuilder<ConversationDetectionWorker>()
        .setInputData(data)
        .build()

    WorkManager.getInstance(context).enqueue(uploadWorkRequest)
}