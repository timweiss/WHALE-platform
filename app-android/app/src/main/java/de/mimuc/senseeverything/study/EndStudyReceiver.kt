package de.mimuc.senseeverything.study

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.helpers.goAsync
import de.mimuc.senseeverything.workers.enqueueSingleSensorReadingsUploadWorker
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class EndStudyReceiver : BroadcastReceiver() {
    val TAG = "EndStudyReceiver"

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context?, intent: Intent?) = goAsync {
        val applicationContext = context?.applicationContext ?: return@goAsync

        dataStoreManager.saveStudyEnded(true)
        Log.i(TAG, "Marked study as ended")

        // stop LogService
        try {
            val logServiceIntent =
                Intent(applicationContext, de.mimuc.senseeverything.service.LogService::class.java)
            applicationContext.stopService(logServiceIntent)
            Log.i(TAG, "Stopped LogService")
        } catch (e: Exception) {
            // already stopped, ignore
            Log.w(TAG, "Error stopping LogService: $e")
        }

        // upload and clear all data and jobs
        WorkManager.getInstance(applicationContext).cancelAllWorkByTag("readingsUpload")
        WorkManager.getInstance(applicationContext).cancelAllWorkByTag("updateQuestionnaires")
        Log.i(TAG, "Cancelled all jobs")

        val token = dataStoreManager.tokenFlow.first()
        enqueueSingleSensorReadingsUploadWorker(applicationContext, token, "finalReadingsUpload", false)
        Log.i(TAG, "Scheduled final sensor readings upload")
    }
}

fun scheduleStudyEndAlarm(context: Context, afterDays: Int) {
    val endStudyIntent = Intent(context, EndStudyReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        101,
        endStudyIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
    )

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + TimeUnit.DAYS.toMillis(afterDays.toLong()),
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + TimeUnit.DAYS.toMillis(afterDays.toLong()),
                pendingIntent
            )
        }
    } else {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + TimeUnit.DAYS.toMillis(afterDays.toLong()),
            pendingIntent
        )
    }
}