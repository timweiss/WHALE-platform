package de.mimuc.senseeverything.helpers

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.ForegroundService
import de.mimuc.senseeverything.service.LogService

object LogServiceHelper {
    private const val TAG = "LogServiceHelper"

    fun startLogService(context: Context) {
        startForegroundService(context, Intent(context, LogService::class.java))
    }

    fun stopLogService(context: Context) {
        context.stopService(Intent(context, LogService::class.java))
    }

    /**
     * Gives a running LogService the microphone type it could not get when it was started from the
     * background. Call this while an app screen is visible, which is when Android allows it.
     */
    fun ensureMicrophone(context: Context) {
        if (!isServiceRunning(context, LogService::class.java)) return

        val intent = Intent(context, LogService::class.java)
            .setAction(ForegroundService.ACTION_ENABLE_MICROPHONE)
        startForegroundService(context, intent)
    }

    private fun startForegroundService(context: Context, intent: Intent) {
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException, e.g. when battery optimization was turned back on
            WHALELog.e(TAG, "Could not start LogService: ${e.message}")
        }
    }
}
