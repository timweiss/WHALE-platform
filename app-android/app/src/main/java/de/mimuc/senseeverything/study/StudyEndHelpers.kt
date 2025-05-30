package de.mimuc.senseeverything.study

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.WorkManager
import de.mimuc.senseeverything.service.esm.OneTimeNotificationReceiver
import de.mimuc.senseeverything.service.esm.PeriodicNotificationReceiver
import de.mimuc.senseeverything.service.esm.RandomNotificationReceiver
import de.mimuc.senseeverything.service.sampling.ResumeSamplingReceiver


fun runStudyLifecycleCleanup(context: Context) {
    stopLogService(context)
    clearJobs(context)
    clearAllAlarms(context)
}

fun stopLogService(context: Context) {
    // stop LogService
    try {
        val logServiceIntent =
            Intent(context, de.mimuc.senseeverything.service.LogService::class.java)
        context.stopService(logServiceIntent)
        Log.i("EndStudy", "Stopped LogService")
    } catch (e: Exception) {
        // already stopped, ignore
        Log.w("EndStudy", "Error stopping LogService: $e")
    }
}

fun clearJobs(context: Context) {
    // cancel all jobs
    WorkManager.getInstance(context).cancelAllWorkByTag("readingsUpload")
    WorkManager.getInstance(context).cancelAllWorkByTag("updateQuestionnaires")
    Log.i("EndStudy", "Cancelled all jobs")
}

fun clearAllAlarms(context: Context) {
    // cancel all alarms
    clearAlarm(context, EndStudyReceiver::class.java, 101)
    clearAlarm(context, ResumeSamplingReceiver::class.java, 101)
    clearAlarm(context, RandomNotificationReceiver::class.java, 101)
    clearAlarm(context, PeriodicNotificationReceiver::class.java, 101)
    clearAlarm(context, OneTimeNotificationReceiver::class.java, 101)
    clearAlarm(context, ChangePhaseReceiver::class.java, 101)

    Log.i("EndStudy", "Cancelled all alarms")
}

fun <T>clearAlarm(context: Context, receiver: Class<T>, requestCode: Int) {
    // cancel all alarms
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
        Log.i("EndStudy", "Cancelled alarm")
    } else {
        Log.w("EndStudy", "No alarm to cancel")
    }
}