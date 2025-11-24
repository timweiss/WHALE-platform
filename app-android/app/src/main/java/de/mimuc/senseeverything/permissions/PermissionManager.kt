package de.mimuc.senseeverything.permissions

import android.Manifest
import android.content.Context
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.permissions.checker.AccessibilityServiceChecker
import de.mimuc.senseeverything.permissions.checker.BatteryOptimizationChecker
import de.mimuc.senseeverything.permissions.checker.ExactAlarmChecker
import de.mimuc.senseeverything.permissions.checker.NotificationListenerChecker
import de.mimuc.senseeverything.permissions.checker.StandardPermissionChecker
import de.mimuc.senseeverything.permissions.checker.SystemAlertWindowChecker
import de.mimuc.senseeverything.permissions.checker.UsageStatsChecker
import de.mimuc.senseeverything.permissions.model.PermissionCategory
import de.mimuc.senseeverything.permissions.model.PermissionDefinition
import de.mimuc.senseeverything.permissions.model.PermissionType
import de.mimuc.senseeverything.permissions.requester.AccessibilityServiceRequester
import de.mimuc.senseeverything.permissions.requester.BatteryOptimizationRequester
import de.mimuc.senseeverything.permissions.requester.ExactAlarmRequester
import de.mimuc.senseeverything.permissions.requester.NotificationListenerRequester
import de.mimuc.senseeverything.permissions.requester.StandardPermissionRequester
import de.mimuc.senseeverything.permissions.requester.SystemAlertWindowRequester
import de.mimuc.senseeverything.permissions.requester.UsageStatsRequester

/**
 * Central manager for all app permissions
 *
 * Provides a single source of truth for permission definitions,
 * checking, and requesting across the app.
 */
object PermissionManager {

    /**
     * All permissions required by the app
     */
    val allPermissions: List<PermissionDefinition> by lazy {
        listOf(
            // Standard runtime permissions
            PermissionDefinition(
                permission = Manifest.permission.WAKE_LOCK,
                nameResId = R.string.permission_wake_lock_name,
                descriptionResId = R.string.permission_wake_lock_desc,
                type = PermissionType.STANDARD,
                checker = StandardPermissionChecker(Manifest.permission.WAKE_LOCK),
                requester = StandardPermissionRequester(Manifest.permission.WAKE_LOCK),
                category = PermissionCategory.BackgroundOperation,
            ),
            PermissionDefinition(
                permission = Manifest.permission.FOREGROUND_SERVICE,
                nameResId = R.string.permission_foreground_service_name,
                descriptionResId = R.string.permission_foreground_service_desc,
                type = PermissionType.STANDARD,
                checker = StandardPermissionChecker(Manifest.permission.FOREGROUND_SERVICE),
                requester = StandardPermissionRequester(Manifest.permission.FOREGROUND_SERVICE),
                category = PermissionCategory.BackgroundOperation,
            ),
            PermissionDefinition(
                permission = Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                nameResId = R.string.permission_battery_optimization_name,
                descriptionResId = R.string.permission_battery_optimization_desc,
                type = PermissionType.SPECIAL,
                checker = BatteryOptimizationChecker(),
                requester = BatteryOptimizationRequester(),
                category = PermissionCategory.BackgroundOperation,
                isCritical = true
            ),
            PermissionDefinition(
                permission = Manifest.permission.RECEIVE_BOOT_COMPLETED,
                nameResId = R.string.permission_receive_boot_name,
                descriptionResId = R.string.permission_receive_boot_desc,
                type = PermissionType.STANDARD,
                checker = StandardPermissionChecker(Manifest.permission.RECEIVE_BOOT_COMPLETED),
                requester = StandardPermissionRequester(Manifest.permission.RECEIVE_BOOT_COMPLETED),
                category = PermissionCategory.BackgroundOperation,
            ),

            PermissionDefinition(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                nameResId = R.string.permission_notifications_name,
                descriptionResId = R.string.permission_notifications_desc,
                type = PermissionType.STANDARD,
                checker = StandardPermissionChecker(Manifest.permission.POST_NOTIFICATIONS),
                requester = StandardPermissionRequester(Manifest.permission.POST_NOTIFICATIONS),
                category = PermissionCategory.Questionnaires,
                isCritical = true
            ),
            PermissionDefinition(
                permission = Manifest.permission.SYSTEM_ALERT_WINDOW,
                nameResId = R.string.permission_system_alert_name,
                descriptionResId = R.string.permission_system_alert_desc,
                type = PermissionType.SPECIAL,
                checker = SystemAlertWindowChecker(),
                category = PermissionCategory.Questionnaires,
                requester = SystemAlertWindowRequester()
            ),
            PermissionDefinition(
                permission = Manifest.permission.SCHEDULE_EXACT_ALARM,
                nameResId = R.string.permission_exact_alarm_name,
                descriptionResId = R.string.permission_exact_alarm_desc,
                type = PermissionType.SPECIAL,
                checker = ExactAlarmChecker(),
                requester = ExactAlarmRequester(),
                category = PermissionCategory.Questionnaires,
            ),

            PermissionDefinition(
                permission = Manifest.permission.PACKAGE_USAGE_STATS,
                nameResId = R.string.permission_usage_stats_name,
                descriptionResId = R.string.permission_usage_stats_desc,
                type = PermissionType.SPECIAL,
                checker = UsageStatsChecker(),
                requester = UsageStatsRequester(),
                category = PermissionCategory.SensorA,
            ),
            PermissionDefinition(
                permission = Manifest.permission.READ_PHONE_STATE,
                nameResId = R.string.permission_read_phone_state_name,
                descriptionResId = R.string.permission_read_phone_state_desc,
                type = PermissionType.STANDARD,
                checker = StandardPermissionChecker(Manifest.permission.READ_PHONE_STATE),
                requester = StandardPermissionRequester(Manifest.permission.READ_PHONE_STATE),
                category = PermissionCategory.SensorA,
            ),
            PermissionDefinition(
                permission = Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
                nameResId = R.string.permission_notification_listener_name,
                descriptionResId = R.string.permission_notification_listener_desc,
                type = PermissionType.SPECIAL,
                checker = NotificationListenerChecker(),
                requester = NotificationListenerRequester(),
                isCritical = true,
                category = PermissionCategory.SensorA,
            ),
            PermissionDefinition(
                permission = Manifest.permission.RECORD_AUDIO,
                nameResId = R.string.permission_record_audio_name,
                descriptionResId = R.string.permission_record_audio_desc,
                type = PermissionType.STANDARD,
                checker = StandardPermissionChecker(Manifest.permission.RECORD_AUDIO),
                requester = StandardPermissionRequester(Manifest.permission.RECORD_AUDIO),
                category = PermissionCategory.SensorA,
            ),

            PermissionDefinition(
                permission = Manifest.permission.ACCESS_WIFI_STATE,
                nameResId = R.string.permission_access_wifi_name,
                descriptionResId = R.string.permission_access_wifi_desc,
                type = PermissionType.STANDARD,
                checker = StandardPermissionChecker(Manifest.permission.ACCESS_WIFI_STATE),
                requester = StandardPermissionRequester(Manifest.permission.ACCESS_WIFI_STATE),
                category = PermissionCategory.SensorB
            ),
            PermissionDefinition(
                permission = Manifest.permission.ACCESS_NETWORK_STATE,
                nameResId = R.string.permission_access_network_name,
                descriptionResId = R.string.permission_access_network_desc,
                type = PermissionType.STANDARD,
                checker = StandardPermissionChecker(Manifest.permission.ACCESS_NETWORK_STATE),
                requester = StandardPermissionRequester(Manifest.permission.ACCESS_NETWORK_STATE),
                category = PermissionCategory.SensorB
            ),
            PermissionDefinition(
                permission = Manifest.permission.BLUETOOTH_SCAN,
                nameResId = R.string.permission_bluetooth_scan_name,
                descriptionResId = R.string.permission_bluetooth_scan_desc,
                type = PermissionType.STANDARD,
                checker = StandardPermissionChecker(Manifest.permission.BLUETOOTH_SCAN),
                requester = StandardPermissionRequester(Manifest.permission.BLUETOOTH_SCAN),
                category = PermissionCategory.SensorB
            ),
            PermissionDefinition(
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                nameResId = R.string.permission_fine_location_name,
                descriptionResId = R.string.permission_fine_location_desc,
                type = PermissionType.STANDARD,
                checker = StandardPermissionChecker(Manifest.permission.ACCESS_FINE_LOCATION),
                requester = StandardPermissionRequester(Manifest.permission.ACCESS_FINE_LOCATION),
                category = PermissionCategory.SensorB
            ),
            PermissionDefinition(
                permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                nameResId = R.string.permission_background_location_name,
                descriptionResId = R.string.permission_background_location_desc,
                type = PermissionType.STANDARD,
                checker = StandardPermissionChecker(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                requester = StandardPermissionRequester(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                category = PermissionCategory.SensorB
            ),
            PermissionDefinition(
                permission = Manifest.permission.ACTIVITY_RECOGNITION,
                nameResId = R.string.permission_activity_recognition_name,
                descriptionResId = R.string.permission_activity_recognition_desc,
                type = PermissionType.STANDARD,
                checker = StandardPermissionChecker(Manifest.permission.ACTIVITY_RECOGNITION),
                requester = StandardPermissionRequester(Manifest.permission.ACTIVITY_RECOGNITION),
                category = PermissionCategory.SensorB
            ),
            PermissionDefinition(
                permission = Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
                nameResId = R.string.permission_accessibility_name,
                descriptionResId = R.string.permission_accessibility_desc,
                type = PermissionType.SPECIAL,
                checker = AccessibilityServiceChecker(),
                requester = AccessibilityServiceRequester(),
                isCritical = true,
                category = PermissionCategory.SensorB
            ),
        )
    }

    /**
     * Get only the critical permissions for healthcheck monitoring
     */
    fun getCriticalPermissions(): List<PermissionDefinition> {
        return allPermissions.filter { it.isCritical }
    }

    /**
     * Check the status of all permissions
     *
     * @param context The application context
     * @return Map of permission string to granted status
     */
    fun checkAll(context: Context): Map<String, Boolean> {
        return allPermissions.associate { permDef ->
            permDef.permission to permDef.checker.isGranted(context)
        }
    }

    /**
     * Check the status of critical permissions only
     *
     * @param context The application context
     * @return Map of permission string to granted status
     */
    fun checkCritical(context: Context): Map<String, Boolean> {
        return getCriticalPermissions().associate { permDef ->
            permDef.permission to permDef.checker.isGranted(context)
        }
    }

    /**
     * Check if a specific permission is granted
     *
     * @param permission The permission string to check
     * @param context The application context
     * @return true if granted, false otherwise
     */
    fun checkPermission(permission: String, context: Context): Boolean {
        val permDef = allPermissions.find { it.permission == permission }
        return permDef?.checker?.isGranted(context) ?: false
    }

    /**
     * Request a specific permission
     *
     * @param permission The permission string to request
     * @param context The application context
     */
    fun requestPermission(permission: String, context: Context) {
        val permDef = allPermissions.find { it.permission == permission }
        permDef?.requester?.request(context)
    }

    /**
     * Get permission definition by permission string
     *
     * @param permission The permission string
     * @return PermissionDefinition or null if not found
     */
    fun getPermissionDefinition(permission: String): PermissionDefinition? {
        return allPermissions.find { it.permission == permission }
    }
}
