package de.mimuc.senseeverything.permissions.requester

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Requests the Accessibility Service permission by opening settings
 */
class AccessibilityServiceRequester : PermissionRequester {
    override fun request(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
