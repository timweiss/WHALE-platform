package de.mimuc.senseeverything.service.healthcheck

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HealthcheckReportTest {
    private fun report(accessibility: ComponentStatus = ComponentStatus.OK) = HealthcheckReport.of(
        trigger = "periodic",
        process = "de.mimuc.whale:remote",
        notificationService = ComponentStatus.OK,
        accessibilityService = accessibility,
        logService = ComponentStatus.failing("not_running"),
        permissions = mapOf(
            "android.permission.POST_NOTIFICATIONS" to true,
            "android.permission.PACKAGE_USAGE_STATS" to false,
            "custom.permission" to true
        )
    )

    @Test
    fun stripsThePermissionPrefix() {
        assertEquals(
            mapOf("POST_NOTIFICATIONS" to true, "PACKAGE_USAGE_STATS" to false, "custom.permission" to true),
            report().permissions
        )
    }

    @Test
    fun keepsTheComponentOrder() {
        assertEquals(
            listOf("NotificationService", "AccessibilityService", "LogService"),
            report().components.keys.toList()
        )
    }

    @Test
    fun writesProblemsOnlyForFailingComponents() {
        val json = Json.parseToJsonElement(report(ComponentStatus.failing("accessibility_off")).toJson()).jsonObject
        val components = json["components"]!!.jsonObject

        val notification = components["NotificationService"]!!.jsonObject
        assert(notification["healthy"]!!.jsonPrimitive.boolean)
        assertNull(notification["problem"])

        val accessibility = components["AccessibilityService"]!!.jsonObject
        assertFalse(accessibility["healthy"]!!.jsonPrimitive.boolean)
        assertEquals("accessibility_off", accessibility["problem"]!!.jsonPrimitive.content)

        assertEquals("periodic", json["trigger"]!!.jsonPrimitive.content)
        assertFalse(json["permissions"]!!.jsonObject["PACKAGE_USAGE_STATS"]!!.jsonPrimitive.boolean)
    }
}
