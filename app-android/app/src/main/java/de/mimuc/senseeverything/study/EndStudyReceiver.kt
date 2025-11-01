package de.mimuc.senseeverything.study

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.StudyState
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.ScheduledAlarm
import de.mimuc.senseeverything.helpers.goAsync
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.workers.enqueueSingleSensorReadingsUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@AndroidEntryPoint
class EndStudyReceiver : BroadcastReceiver() {
    val TAG = "EndStudyReceiver"

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context?, intent: Intent?) = goAsync {
        val applicationContext = context?.applicationContext ?: return@goAsync

        dataStoreManager.saveStudyState(StudyState.ENDED)
        WHALELog.i(TAG, "Marked study as ended")

        runStudyLifecycleCleanup(applicationContext)

        val token = dataStoreManager.tokenFlow.first()
        enqueueSingleSensorReadingsUploadWorker(applicationContext, token, "finalReadingsUpload", false, 1.minutes)
        WHALELog.i(TAG, "Scheduled final sensor readings upload")
    }
}

suspend fun scheduleStudyEndAlarm(context: Context, timestamp: Long, database: AppDatabase) {
    val scheduledAlarm = ScheduledAlarm.getOrCreateScheduledAlarm(
        database,
        "EndStudyReceiver",
        "End",
        timestamp
    )

    setStudyEndAlarm(context, scheduledAlarm)
    WHALELog.i("EndStudyReceiver", "Scheduled study end alarm for timestamp $timestamp")
}

suspend fun rescheduleStudyEndAlarm(context: Context, database: AppDatabase) {
    val scheduledAlarm = withContext(Dispatchers.IO) {
        database.scheduledAlarmDao().getByIdentifier("EndStudyReceiver", "End")
    }

    if (scheduledAlarm != null) {
        setStudyEndAlarm(context, scheduledAlarm)
        WHALELog.i("EndStudyReceiver", "Rescheduled study end alarm for timestamp ${scheduledAlarm.timestamp}")
    } else {
        WHALELog.w("EndStudyReceiver", "No scheduled study end alarm found to reschedule")
    }
}

fun setStudyEndAlarm(context: Context, scheduledAlarm: ScheduledAlarm) {
    val endStudyIntent = Intent(context, EndStudyReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        scheduledAlarm.requestCode,
        endStudyIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
    )

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                scheduledAlarm.timestamp,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                scheduledAlarm.timestamp,
                pendingIntent
            )
        }
    } else {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            scheduledAlarm.timestamp,
            pendingIntent
        )
    }
}