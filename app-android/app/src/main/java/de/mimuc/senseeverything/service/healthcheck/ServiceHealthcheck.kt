package de.mimuc.senseeverything.service.healthcheck

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import androidx.core.app.NotificationManagerCompat
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.permissions.PermissionManager
import de.mimuc.senseeverything.service.LogService
import de.mimuc.senseeverything.service.MyNotificationListenerService
import de.mimuc.senseeverything.service.accessibility.AccessibilityLogService

object ServiceHealthcheck {

    /** [trigger] names what ran the check (periodic, log_service, boot) in the report. */
    fun checkServices(context: Context, trigger: String): HealthcheckResult {
        val notificationService = checkNotificationService(context)
        val accessibilityService = checkAccessibilityService(context)
        val logService = checkLogService(context)
        val permissionsGranted = checkPermissions(context)

        writeReport(trigger, notificationService, accessibilityService, logService, permissionsGranted)

        return HealthcheckResult(
            notificationServiceHealthy = notificationService.healthy,
            accessibilityServiceHealthy = accessibilityService.healthy,
            logServiceHealthy = logService.healthy,
            permissionsGranted = permissionsGranted,
            timestamp = System.currentTimeMillis()
        )
    }

    /** One structured "Healthcheck" row per check, replacing the former text lines per component. */
    private fun writeReport(
        trigger: String,
        notificationService: ComponentStatus,
        accessibilityService: ComponentStatus,
        logService: ComponentStatus,
        permissionsGranted: Map<String, Boolean>
    ) {
        try {
            val json = HealthcheckReport.of(
                trigger = trigger,
                process = Application.getProcessName(),
                notificationService = notificationService,
                accessibilityService = accessibilityService,
                logService = logService,
                permissions = permissionsGranted
            ).toJson()
            WHALELog.d(TAG, "healthcheck $json")
            WHALELog.saveDataRow(HealthcheckReport.SENSOR_NAME, json)
        } catch (e: Exception) {
            WHALELog.e(TAG, "Failed to write healthcheck report: ${e.message}", e)
        }
    }

    private fun checkPermissions(context: Context): Map<String, Boolean> {
        val perms = PermissionManager.checkAll(context)

        perms.filter { !it.value }.forEach { (permission, _) ->
            val permDef = PermissionManager.getPermissionDefinition(permission)
            WHALELog.w(TAG, "Permission revoked: ${permDef?.let { context.getString(it.nameResId) } ?: permission}")
        }

        return perms
    }

    private fun checkNotificationService(context: Context): ComponentStatus {
        // Check 1: Permission enabled
        val hasPermission = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)

        if (!hasPermission) {
            WHALELog.w(TAG, "NotificationListener permission not enabled")
            return ComponentStatus.failing("permission_missing")
        }

        // Check 2: Verify service is actually running
        val serviceRunning = isServiceRunning(context, MyNotificationListenerService::class.java)

        if (!serviceRunning) {
            WHALELog.w(TAG, "NotificationListener permission enabled but service not running")
            return ComponentStatus.failing("not_running")
        }

        return ComponentStatus.OK
    }

    private fun checkLogService(context: Context): ComponentStatus {
        val serviceRunning = isServiceRunning(context, LogService::class.java)

        if (!serviceRunning) {
            WHALELog.w(TAG, "LogService not running")
            return ComponentStatus.failing("not_running")
        }

        return ComponentStatus.OK
    }

    private fun checkAccessibilityService(context: Context): ComponentStatus {
        // Check 1: Permission enabled
        var accessibilityEnabled = 0
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            WHALELog.w(TAG, "Accessibility settings not found")
            return ComponentStatus.failing("settings_unavailable")
        }

        if (accessibilityEnabled != 1) {
            WHALELog.w(TAG, "Accessibility not enabled")
            return ComponentStatus.failing("accessibility_off")
        }

        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        if (settingValue == null) {
            WHALELog.w(TAG, "Accessibility enabled services setting is null")
            return ComponentStatus.failing("service_not_enabled")
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
            return ComponentStatus.failing("service_not_enabled")
        }

        // Check 2: Verify service is actually running
        val serviceRunning = isServiceRunning(context, AccessibilityLogService::class.java)

        if (!serviceRunning) {
            WHALELog.w(TAG, "AccessibilityService permission enabled but service not running")
            return ComponentStatus.failing("not_running")
        }

        return ComponentStatus.OK
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
    val permissionsGranted: Map<String, Boolean>,
    val timestamp: Long
) {
    val allHealthy: Boolean
        get() = notificationServiceHealthy &&
                accessibilityServiceHealthy &&
                logServiceHealthy &&
                permissionsGranted.values.all { it }

    val allCriticalPermissionsGranted: Boolean
        get() = permissionsGranted.values.all { it }
}