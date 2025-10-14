package de.mimuc.senseeverything.helpers

import android.content.Context
import android.content.Intent
import de.mimuc.senseeverything.service.LogService

object LogServiceHelper {
    fun startLogService(context: Context) {
        context.startService(Intent(context, LogService::class.java))
    }

    fun stopLogService(context: Context) {
        context.stopService(Intent(context, LogService::class.java))
    }
}
