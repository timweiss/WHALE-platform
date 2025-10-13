package de.mimuc.senseeverything.permissions.requester

import android.content.Context
import androidx.core.app.ActivityCompat
import de.mimuc.senseeverything.activity.getActivity
import de.mimuc.senseeverything.logging.WHALELog

/**
 * Requests standard Android runtime permissions
 */
class StandardPermissionRequester(private val permission: String) : PermissionRequester {
    override fun request(context: Context) {
        val activity = context.getActivity()
        if (activity != null) {
            ActivityCompat.requestPermissions(activity, arrayOf(permission), REQUEST_CODE)
        } else {
            WHALELog.e(TAG, "Could not get activity to request permission: $permission")
        }
    }

    companion object {
        private const val TAG = "StandardPermissionRequester"
        private const val REQUEST_CODE = 1
    }
}
