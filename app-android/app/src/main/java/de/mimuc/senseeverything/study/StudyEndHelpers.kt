package de.mimuc.senseeverything.study

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.WorkManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.esm.NotificationTriggerReceiver
import de.mimuc.senseeverything.service.esm.OneTimeNotificationReceiver
import de.mimuc.senseeverything.service.esm.PeriodicNotificationReceiver
import de.mimuc.senseeverything.service.esm.QuestionnaireReminderNotificationReceiver
import de.mimuc.senseeverything.service.esm.RandomNotificationReceiver
import de.mimuc.senseeverything.service.healthcheck.PeriodicServiceHealthcheckReceiver
import de.mimuc.senseeverything.workers.StaleUnsyncedSensorReadingsCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


suspend fun runStudyLifecycleCleanup(context: Context, database: AppDatabase, clearAlarms: Boolean = false) {
    stopLogService(context)
    clearJobs(context)

    // if alarms are cleared prematurely from EndStudyReceiver,
    // questionnaires after the last day would not be delivered
    if (clearAlarms) {
        clearAllAlarms(context, database)
    }

    PeriodicServiceHealthcheckReceiver.cancel(context)
}

fun stopLogService(context: Context) {
    try {
        val logServiceIntent =
            Intent(context, de.mimuc.senseeverything.service.LogService::class.java)
        context.stopService(logServiceIntent)
        WHALELog.i("EndStudy", "Stopped LogService")
    } catch (e: Exception) {
        // already stopped, ignore
        WHALELog.w("EndStudy", "Error stopping LogService: $e")
    }
}

fun clearJobs(context: Context) {
    WorkManager.getInstance(context).cancelAllWorkByTag("readingsUpload")
    WorkManager.getInstance(context).cancelAllWorkByTag("updateQuestionnaires")
    WorkManager.getInstance(context).cancelAllWorkByTag("pendingQuestionnaireUpload")
    WorkManager.getInstance(context).cancelAllWorkByTag(StaleUnsyncedSensorReadingsCheckWorker.WORKER_TAG)

    WHALELog.i("EndStudy", "Cancelled all jobs")
}

suspend fun clearAllAlarms(context: Context, database: AppDatabase) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        alarmManager.cancelAll()
        WHALELog.i("EndStudy", "API >34, cancelled all alarms")
    } else {
        val scheduledAlarms = withContext(Dispatchers.IO) {
            database.scheduledAlarmDao().getAfterTimestamp(System.currentTimeMillis())
        }

        for (alarm in scheduledAlarms) {
            when (alarm.receiver) {
                "EndStudyReceiver" -> {
                    clearAlarm(context, EndStudyReceiver::class.java, alarm.requestCode)
                }

                "OneTimeNotificationReceiver" -> {
                    clearAlarm(context, OneTimeNotificationReceiver::class.java, alarm.requestCode)
                }

                "RandomNotificationReceiver" -> {
                    clearAlarm(context, RandomNotificationReceiver::class.java, alarm.requestCode)
                }

                "PeriodicNotificationReceiver" -> {
                    clearAlarm(context, PeriodicNotificationReceiver::class.java, alarm.requestCode)
                }

                "NotificationTriggerReceiver" -> {
                    clearAlarm(context, NotificationTriggerReceiver::class.java, alarm.requestCode)
                }

                "ChangePhaseReceiver" -> {
                    clearAlarm(context, ChangePhaseReceiver::class.java, alarm.requestCode)
                }

                "QuestionnaireReminderNotificationReceiver" -> {
                    clearAlarm(context, QuestionnaireReminderNotificationReceiver::class.java, alarm.requestCode)
                }
            }
        }

        WHALELog.i("EndStudy", "API <34, cancelled all alarms via database references")
    }
}

fun <T> clearAlarm(context: Context, receiver: Class<T>, requestCode: Int) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val endStudyIntent = Intent(context, receiver)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        endStudyIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
    )

    if (pendingIntent != null) {
        alarmManager.cancel(pendingIntent)
        WHALELog.i(
            "EndStudy",
            "Cancelled alarm for receiver ${receiver::class.java.name} with code $requestCode"
        )
    } else {
        WHALELog.w("EndStudy", "No alarm to cancel")
    }
}