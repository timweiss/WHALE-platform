package de.mimuc.senseeverything.permissions.requester

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import de.mimuc.senseeverything.activity.getActivity
import de.mimuc.senseeverything.logging.WHALELog

/**
 * Requests the System Alert Window (overlay) permission
 */
class SystemAlertWindowRequester : PermissionRequester {
    override fun request(context: Context) {
        val activity = context.getActivity()
        if (activity != null) {
            if (!Settings.canDrawOverlays(activity)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                ActivityCompat.startActivityForResult(activity, intent, REQUEST_CODE, null)
            } else {
                WHALELog.i(TAG, "SYSTEM_ALERT_WINDOW permission already granted")
            }
        } else {
            WHALELog.e(TAG, "Could not get activity to request System Alert Window permission")
        }
    }

    companion object {
        private const val TAG = "SystemAlertWindowRequester"
        private const val REQUEST_CODE = 1001
    }
}
