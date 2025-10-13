package de.mimuc.senseeverything.permissions.checker

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * Checks if the Notification Listener permission is granted
 */
class NotificationListenerChecker : PermissionChecker {
    override fun isGranted(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }
}
