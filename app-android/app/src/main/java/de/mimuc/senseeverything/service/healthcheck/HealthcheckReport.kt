package de.mimuc.senseeverything.service.healthcheck

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The result of one healthcheck, written as a "Healthcheck" row so the server can count
 * problems per participant: every component with whether it is healthy and, if not, why, and
 * every permission by its manifest id.
 */
@Serializable
data class HealthcheckReport(
    /** what ran the check: periodic, log_service or boot */
    val trigger: String,
    val process: String,
    /** NotificationService, AccessibilityService, LogService */
    val components: Map<String, ComponentStatus>,
    /** permission id without the "android.permission." prefix -> granted */
    val permissions: Map<String, Boolean>
) {
    /** a healthy component's problem (null, the default) is left out */
    fun toJson(): String = Json.encodeToString(this)

    companion object {
        const val SENSOR_NAME = "Healthcheck"
        private const val PERMISSION_PREFIX = "android.permission."

        fun of(
            trigger: String,
            process: String,
            notificationService: ComponentStatus,
            accessibilityService: ComponentStatus,
            logService: ComponentStatus,
            permissions: Map<String, Boolean>
        ) = HealthcheckReport(
            trigger = trigger,
            process = process,
            components = linkedMapOf(
                "NotificationService" to notificationService,
                "AccessibilityService" to accessibilityService,
                "LogService" to logService
            ),
            permissions = permissions.mapKeys { it.key.removePrefix(PERMISSION_PREFIX) }
        )
    }
}

/** [problem] is a fixed code (e.g. "not_running") and only set when not [healthy]. */
@Serializable
data class ComponentStatus(val healthy: Boolean, val problem: String? = null) {
    companion object {
        val OK = ComponentStatus(true)
        fun failing(problem: String) = ComponentStatus(false, problem)
    }
}
