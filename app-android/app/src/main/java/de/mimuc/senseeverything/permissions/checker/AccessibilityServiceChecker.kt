package de.mimuc.senseeverything.permissions.checker

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import de.mimuc.senseeverything.service.accessibility.AccessibilityLogService

/**
 * Checks if the Accessibility Service is enabled
 */
class AccessibilityServiceChecker : PermissionChecker {
    override fun isGranted(context: Context): Boolean {
        var accessibilityEnabled = 0

        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            return false
        }

        if (accessibilityEnabled != 1) {
            return false
        }

        val settingValue = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(settingValue)

        while (colonSplitter.hasNext()) {
            val accessibilityService = colonSplitter.next()
            if (accessibilityService.equals(AccessibilityLogService.SERVICE, ignoreCase = true)) {
                return true
            }
        }

        return false
    }
}
