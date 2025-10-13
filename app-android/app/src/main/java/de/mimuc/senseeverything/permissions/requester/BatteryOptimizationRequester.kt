package de.mimuc.senseeverything.permissions.requester

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Requests battery optimization exemption by opening settings
 */
class BatteryOptimizationRequester : PermissionRequester {
    override fun request(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(Uri.parse("package:${context.packageName}"))
        )
    }
}
