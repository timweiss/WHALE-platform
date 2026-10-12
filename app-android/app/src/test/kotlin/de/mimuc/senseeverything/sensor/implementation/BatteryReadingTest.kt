package de.mimuc.senseeverything.sensor.implementation

import android.os.BatteryManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BatteryReadingTest {
    private fun reading(
        level: Int = 57,
        scale: Int = 100,
        status: Int = BatteryManager.BATTERY_STATUS_DISCHARGING,
        plugged: Int = 0,
        chargeCounterUah: Int? = 2_850_000,
        currentNowUa: Int? = -312_000
    ) = BatteryReading.of(
        level = level,
        scale = scale,
        status = status,
        plugged = plugged,
        temperatureDeciC = 291,
        voltageMv = 3_912,
        chargeCounterUah = chargeCounterUah,
        currentNowUa = currentNowUa
    )

    @Test
    fun convertsLevelAndUnits() {
        val r = reading(level = 150, scale = 200)
        assertEquals(75f, r.percent)
        assertEquals("discharging", r.status)
        assertNull(r.plugged)
        assertEquals(29.1f, r.temperatureC)
        assertEquals(3_912, r.voltageMv)
    }

    @Test
    fun unknownLevelIsNull() {
        assertNull(reading(level = -1).percent)
        assertNull(reading(scale = 0).percent)
    }

    @Test
    fun namesChargingState() {
        val r = reading(status = BatteryManager.BATTERY_STATUS_CHARGING, plugged = BatteryManager.BATTERY_PLUGGED_USB)
        assertEquals("charging", r.status)
        assertEquals("usb", r.plugged)
    }

    @Test
    fun unsupportedPropertiesAreNull() {
        val r = reading(chargeCounterUah = Int.MIN_VALUE, currentNowUa = 0)
        assertNull(r.chargeCounterUah)
        assertNull(r.currentNowUa)
    }

    @Test
    fun encodesJson() {
        assertEquals(
            """{"percent":57.0,"status":"discharging","plugged":null,"temperatureC":29.1,"voltageMv":3912,"chargeCounterUah":2850000,"currentNowUa":-312000}""",
            reading().toJson()
        )
    }
}
