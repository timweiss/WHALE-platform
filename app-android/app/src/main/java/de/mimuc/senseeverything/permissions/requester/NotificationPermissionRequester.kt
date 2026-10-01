package de.mimuc.senseeverything.permissions.requester

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Requests POST_NOTIFICATIONS on API 33+, before that opens the app's notification settings
 */
class NotificationPermissionRequester : PermissionRequester {
    private val runtimeRequester = StandardPermissionRequester(Manifest.permission.POST_NOTIFICATIONS)

    override fun request(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runtimeRequester.request(context)
            return
        }

        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
