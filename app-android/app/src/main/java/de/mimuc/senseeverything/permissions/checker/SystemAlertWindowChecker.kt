package de.mimuc.senseeverything.permissions.checker

import android.content.Context
import android.provider.Settings

/**
 * Checks if the System Alert Window (overlay) permission is granted
 */
class SystemAlertWindowChecker : PermissionChecker {
    override fun isGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
}
