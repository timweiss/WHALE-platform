package de.mimuc.senseeverything.service.healthcheck

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.LogService
import de.mimuc.senseeverything.service.MyNotificationListenerService
import de.mimuc.senseeverything.service.accessibility.AccessibilityLogService

object ServiceHealthcheck {

    fun checkServices(context: Context): HealthcheckResult {
        val notificationServiceHealthy = checkNotificationService(context)
        val accessibilityServiceHealthy = checkAccessibilityService(context)
        val logServiceHealthy = checkLogService(context)

        WHALELog.i(
            TAG,
            "NotificationService: $notificationServiceHealthy"
        )
        WHALELog.i(
            TAG,
            "AccessibilityService: $accessibilityServiceHealthy"
        )
        WHALELog.i(TAG, "LogService: $logServiceHealthy")

        return HealthcheckResult(
            notificationServiceHealthy = notificationServiceHealthy,
            accessibilityServiceHealthy = accessibilityServiceHealthy,
            logServiceHealthy = logServiceHealthy,
            timestamp = System.currentTimeMillis()
        )
    }

    private fun checkNotificationService(context: Context): Boolean {
        // Check 1: Permission enabled
        val hasPermission = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

        if (!hasPermission) {
            WHALELog.w(TAG, "NotificationListener permission not enabled")
            return false
        }

        // Check 2: Verify service is actually running
        val serviceRunning = isServiceRunning(context, MyNotificationListenerService::class.java)

        if (!serviceRunning) {
            WHALELog.w(TAG, "NotificationListener permission enabled but service not running")
        }

        return serviceRunning
    }

    private fun checkLogService(context: Context): Boolean {
        val serviceRunning = isServiceRunning(context, LogService::class.java)

        if (!serviceRunning) {
            WHALELog.w(TAG, "LogService not running")
        }

        return serviceRunning
    }

    private fun checkAccessibilityService(context: Context): Boolean {
        // Check 1: Permission enabled
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            WHALELog.w(TAG, "Accessibility settings not found")
            return false
        }

        if (accessibilityEnabled != 1) {
            WHALELog.w(TAG, "Accessibility not enabled")
            return false
        }

        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        if (settingValue == null) {
            WHALELog.w(TAG, "Accessibility enabled services setting is null")
            return false
        }

        val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')
        mStringColonSplitter.setString(settingValue)
        var hasPermission = false

        while (mStringColonSplitter.hasNext()) {
            val accessibilityService = mStringColonSplitter.next()
            if (accessibilityService.equals(AccessibilityLogService.SERVICE, ignoreCase = true)) {
                hasPermission = true
                break
            }
        }

        if (!hasPermission) {
            WHALELog.w(TAG, "AccessibilityService not in enabled services list")
            return false
        }

        // Check 2: Verify service is actually running
        val serviceRunning = isServiceRunning(context, AccessibilityLogService::class.java)

        if (!serviceRunning) {
            WHALELog.w(TAG, "AccessibilityService permission enabled but service not running")
        }

        return serviceRunning
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val runningServices = am.getRunningServices(Int.MAX_VALUE)

        return runningServices.any {
            it.service.className == serviceClass.name
        }
    }

    private const val TAG = "ServiceHealthcheck"
}

data class HealthcheckResult(
    val notificationServiceHealthy: Boolean,
    val accessibilityServiceHealthy: Boolean,
    val logServiceHealthy: Boolean,
    val timestamp: Long
) {
    val allHealthy: Boolean
        get() = notificationServiceHealthy && accessibilityServiceHealthy && logServiceHealthy
}