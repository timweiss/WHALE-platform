package de.mimuc.senseeverything.service.accessibility

import android.view.accessibility.AccessibilityEvent

interface AccessibilityLoggingConsumer {
    fun init(service: AccessibilityLogService)
    fun consumeEvent(event: AccessibilityEvent)
    fun shutdown()
}