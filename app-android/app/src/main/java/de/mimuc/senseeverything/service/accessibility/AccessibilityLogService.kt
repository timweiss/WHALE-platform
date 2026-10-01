package de.mimuc.senseeverything.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.sensor.BufferedLogWriter
import javax.inject.Inject

@AndroidEntryPoint
class AccessibilityLogService : AccessibilityService() {
    @Inject
    lateinit var database: AppDatabase;

    private val info = AccessibilityServiceInfo()

    private val consumers = listOf(
        AccessibilityNameConsumer(),
        UITreeConsumer()
    )

    override fun onCreate() {
        super.onCreate()
        WHALELog.i(TAG, "onCreate")

        consumers.forEach { it.init(this) }
    }

    override fun onInterrupt() {
        WHALELog.v(TAG, "onInterrupt")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        WHALELog.v(TAG, "onServiceConnected")

        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        info.flags = AccessibilityServiceInfo.DEFAULT

        info.notificationTimeout = 100

        this.serviceInfo = info
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        WHALELog.i(TAG, "onStartCommand() was called")

        if (intent?.action == ACTION_DISABLE) {
            // turns the service off in the accessibility settings, so Android stops sending events
            WHALELog.i(TAG, "study is over, disabling accessibility service")
            disableSelf()
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    override fun onDestroy() {
        WHALELog.i(TAG, "service stopped")
        consumers.forEach { it.shutdown() }
        // write the batches delivered during shutdown now instead of after the flush interval
        BufferedLogWriter.flushAll()
        stopForeground(true)
        super.onDestroy()
    }


    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        consumers.forEach { it.consumeEvent(event) }
    }

    companion object {
        @JvmField
        val TAG: String = AccessibilityLogService::class.java.simpleName

        const val SERVICE: String =
            "de.mimuc.whale/de.mimuc.senseeverything.service.accessibility.AccessibilityLogService"

        private const val ACTION_DISABLE = "de.mimuc.senseeverything.action.DISABLE_ACCESSIBILITY"

        /** Turns the accessibility service off for good, e.g. when the study ends. */
        fun disable(context: Context) {
            try {
                context.startService(
                    Intent(context, AccessibilityLogService::class.java).setAction(ACTION_DISABLE)
                )
            } catch (e: IllegalStateException) {
                // not allowed from the background; LogService is stopped next, so captures pause anyway
                WHALELog.w(TAG, "Could not disable accessibility service: ${e.message}")
            }
        }
    }
}