package de.mimuc.senseeverything.permissions

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.RecoverPermissionsActivity
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PermissionNotificationHelper @Inject constructor(
    private val dataStoreManager: DataStoreManager
) {
    companion object {
        private const val TAG = "PermissionNotificationHelper"
        private const val CHANNEL_ID = "permission_revoked_channel"
        private const val NOTIFICATION_ID = 1051
        private const val MIN_NOTIFICATION_INTERVAL_MS = 60 * 60 * 1000 // 1 hour
    }

    /**
     * Shows a notification about revoked permissions if:
     * 1. At least one hour has passed since the last notification
     * 2. The set of revoked permissions has changed
     */
    suspend fun showPermissionRevokedNotification(
        context: Context,
        revokedPermissions: Map<String, Boolean>
    ) {
        val revokedPerms = revokedPermissions.filterValues { !it }
        if (revokedPerms.isEmpty()) {
            WHALELog.i(TAG, "No revoked permissions, skipping notification")
            return
        }

        if (!shouldShowNotification(revokedPerms)) {
            WHALELog.i(TAG, "Notification throttled or same permissions revoked, skipping")
            return
        }

        WHALELog.i(TAG, "Showing permission revoked notification for ${revokedPerms.size} permissions")

        createNotificationChannel(context)
        val notification = buildNotification(context, revokedPerms)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        // Update DataStore with current timestamp and revoked permissions
        dataStoreManager.saveLastPermissionNotificationTime(System.currentTimeMillis())
        dataStoreManager.saveLastRevokedPermissions(revokedPerms.keys)
    }

    /**
     * Determines if a notification should be shown based on:
     * 1. Time since last notification (must be at least 1 hour)
     * 2. Whether the set of revoked permissions has changed
     */
    private suspend fun shouldShowNotification(revokedPermissions: Map<String, Boolean>): Boolean {
        val lastNotificationTime = dataStoreManager.lastPermissionNotificationTimeFlow.first()
        val lastRevokedPerms = dataStoreManager.lastRevokedPermissionsFlow.first()

        val currentTime = System.currentTimeMillis()
        val timeSinceLastNotification = currentTime - lastNotificationTime

        // Check if enough time has passed
        if (timeSinceLastNotification < MIN_NOTIFICATION_INTERVAL_MS) {
            WHALELog.i(
                TAG,
                "Only ${timeSinceLastNotification / 1000}s since last notification, throttling"
            )
            return false
        }

        // Check if the set of revoked permissions has changed
        val currentRevokedPerms = revokedPermissions.filterValues { !it }.keys
        if (currentRevokedPerms == lastRevokedPerms) {
            WHALELog.i(TAG, "Same permissions still revoked, skipping duplicate notification")
            return false
        }

        return true
    }

    private fun createNotificationChannel(context: Context) {
        val name = context.getString(R.string.permission_revoked_channel_name)
        val descriptionText = context.getString(R.string.permission_revoked_channel_description)
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        context: Context,
        revokedPermissions: Map<String, Boolean>
    ): Notification {
        val intent = Intent(context, RecoverPermissionsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val revokedCount = revokedPermissions.filterValues { !it }.size

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification_whale)
            .setContentTitle(context.getString(R.string.permission_revoked_notification_title))
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.permission_revoked_notification_text,
                    revokedCount,
                    revokedCount
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
