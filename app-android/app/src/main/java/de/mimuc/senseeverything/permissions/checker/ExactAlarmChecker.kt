package de.mimuc.senseeverything.permissions.checker

import android.app.AlarmManager
import android.content.Context
import android.os.Build

/**
 * Checks if the Exact Alarm permission is granted
 */
class ExactAlarmChecker : PermissionChecker {
    override fun isGranted(context: Context): Boolean {
        // Permission is only required on API 31+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        return alarmManager?.canScheduleExactAlarms() ?: false
    }
}
