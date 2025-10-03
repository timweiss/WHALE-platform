package de.mimuc.senseeverything.service.accessibility

import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import de.mimuc.senseeverything.activity.CONST

class AccessibilityNameConsumer: AccessibilityLoggingConsumer {
    companion object {
        const val TAG = "DefaultAccessibilityLoggingConsumer"
    }

    lateinit var service: AccessibilityLogService

    override fun init(service: AccessibilityLogService) {
        this.service = service
    }

    override fun consumeEvent(event: AccessibilityEvent) {
        if (AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED == event.eventType) return

        val s = String.format(
            "%s,%s,%s,%s,%s\n",
            CONST.dateFormat.format(System.currentTimeMillis()),
            event.eventTime,
            getEventType(event),
            event.className,
            event.packageName
        )

        val message = Intent(TAG)
        message.putExtra(Intent.EXTRA_TEXT, s)
        service.sendBroadcast(message)
    }

    private fun getEventType(event: AccessibilityEvent): String {
        when (event.eventType) {
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> return "TYPE_ANNOUNCEMENT"
            AccessibilityEvent.TYPE_GESTURE_DETECTION_END -> return "TYPE_GESTURE_DETECTION_END"
            AccessibilityEvent.TYPE_GESTURE_DETECTION_START -> return "TYPE_GESTURE_DETECTION_START"
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_END -> return "TYPE_TOUCH_EXPLORATION_GESTURE_END"
            AccessibilityEvent.TYPE_TOUCH_EXPLORATION_GESTURE_START -> return "TYPE_TOUCH_EXPLORATION_GESTURE_START"
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> return "TYPE_TOUCH_INTERACTION_END"
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START -> return "TYPE_TOUCH_INTERACTION_START"
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED -> return "TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED"
            AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED -> return "TYPE_VIEW_ACCESSIBILITY_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> return "TYPE_VIEW_CLICKED"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> return "TYPE_VIEW_FOCUSED"
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER -> return "TYPE_VIEW_HOVER_ENTER"
            AccessibilityEvent.TYPE_VIEW_HOVER_EXIT -> return "TYPE_VIEW_HOVER_EXIT"
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> return "TYPE_VIEW_LONG_CLICKED"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> return "TYPE_VIEW_SCROLLED"
            AccessibilityEvent.TYPE_VIEW_SELECTED -> return "TYPE_VIEW_SELECTED"
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> return "TYPE_VIEW_TEXT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> return "TYPE_VIEW_TEXT_SELECTION_CHANGED"
            AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY -> return "TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> return "TYPE_WINDOW_CONTENT_CHANGED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> return "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_ASSIST_READING_CONTEXT -> return "TYPE_ASSIST_READING_CONTEXT"
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> return "TYPE_NOTIFICATION_STATE_CHANGED"
            AccessibilityEvent.TYPE_SPEECH_STATE_CHANGE -> return "TYPE_SPEECH_STATE_CHANGE"
            AccessibilityEvent.TYPE_VIEW_CONTEXT_CLICKED -> return "TYPE_VIEW_CONTEXT_CLICKED"
            AccessibilityEvent.TYPE_VIEW_TARGETED_BY_SCROLL -> return "TYPE_VIEW_TARGETED_BY_SCROLL"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> return "TYPE_WINDOWS_CHANGED"
        }
        return "default"
    }

    override fun shutdown() {
        // nothing to do
    }
}