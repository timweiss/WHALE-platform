package de.mimuc.senseeverything.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import de.mimuc.senseeverything.logging.WHALELog

class AccessibilityLogService : AccessibilityService() {
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

        return START_STICKY
    }

    override fun onDestroy() {
        WHALELog.i(TAG, "service stopped")
        consumers.forEach { it.shutdown() }
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
    }
}