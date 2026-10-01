package de.mimuc.senseeverything.service.sampling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.mimuc.senseeverything.helpers.LogServiceHelper
import de.mimuc.senseeverything.logging.WHALELog

class ResumeSamplingReceiver : BroadcastReceiver() {
    val TAG = "ResumeSamplingReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) {
            WHALELog.e(TAG, "Context is null")
            return
        }

        // starting the service sets up lock/unlock listening and periodic sampling
        WHALELog.i(TAG, "Resuming sampling after pause")
        LogServiceHelper.startLogService(context.applicationContext)
    }
}
