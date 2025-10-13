package de.mimuc.senseeverything.service.healthcheck

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.mimuc.senseeverything.logging.WHALELog

class PeriodicServiceHealthcheckReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        WHALELog.i(TAG, "Periodic healthcheck triggered")
        val result = ServiceHealthcheck.checkServices(context)

        if (!result.allHealthy) {
            WHALELog.w(
                TAG,
                "Healthcheck failed - NotificationService: ${result.notificationServiceHealthy}, " +
                "AccessibilityService: ${result.accessibilityServiceHealthy}, " +
                "LogService: ${result.logServiceHealthy}, " +
                "CriticalPermissions: ${result.allCriticalPermissionsGranted}"
            )

            if (!result.allCriticalPermissionsGranted) {
                val revokedPerms = result.permissionsGranted.filter { !it.value }
                WHALELog.w(TAG, "Revoked critical permissions: ${revokedPerms.keys}")
            }
        }
    }

    companion object {
        private const val TAG = "PeriodicHealthcheck"
        private const val INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
        private const val REQUEST_CODE = 9001

        fun schedule(context: Context) {
            val intent = Intent(context, PeriodicServiceHealthcheckReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + INTERVAL_MS,
                INTERVAL_MS,
                pendingIntent
            )

            WHALELog.i(TAG, "Scheduled periodic healthcheck every 15 minutes")
        }

        fun cancel(context: Context) {
            val intent = Intent(context, PeriodicServiceHealthcheckReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )

            pendingIntent?.let {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(it)
                it.cancel()
                WHALELog.i(TAG, "Cancelled periodic healthcheck")
            }
        }
    }
}