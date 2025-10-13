package de.mimuc.senseeverything.permissions.requester

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Requests the Notification Listener permission by opening settings
 */
class NotificationListenerRequester : PermissionRequester {
    override fun request(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
