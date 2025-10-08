package de.mimuc.senseeverything.helpers

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.sampling.ResumeSamplingReceiver

fun scheduleResumeSamplingAlarm(context: Context, at: Long): Boolean {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
    val tag = "ScheduleResumeSamplingAlarm"

    val intent = Intent(context, ResumeSamplingReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    if (alarmManager != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    at,
                    pendingIntent
                )
            } else {
                WHALELog.e(tag, "Cannot schedule exact alarms")
                return false
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                at,
                pendingIntent
            )
        }
        WHALELog.i(tag, "Scheduled resume timer at $at")
        return true
    } else {
        WHALELog.e(tag, "AlarmManager is null")
    }

    return false
}