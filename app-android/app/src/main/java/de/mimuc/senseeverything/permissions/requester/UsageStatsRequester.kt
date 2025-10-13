package de.mimuc.senseeverything.permissions.requester

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Requests the Usage Stats permission by opening settings
 */
class UsageStatsRequester : PermissionRequester {
    override fun request(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
