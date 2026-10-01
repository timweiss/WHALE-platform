package de.mimuc.senseeverything.sensor.implementation

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.sensor.AbstractSensor
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Records the battery state with every sampling cycle and unlock, so the drain per hour can be
 * followed per participant. Reads the sticky ACTION_BATTERY_CHANGED broadcast, so it registers no
 * receiver and causes no wakeups of its own.
 */
class BatterySensor(context: Context, database: AppDatabase) : AbstractSensor(context, database) {

    init {
        TAG = javaClass.name
        SENSOR_NAME = "Battery"
    }

    override fun start(context: Context) {
        super.start(context)
        if (!m_isSensorAvailable) return

        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val batteryManager = context.getSystemService(BatteryManager::class.java)

        val reading = BatteryReading.of(
            level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
            temperatureDeciC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE),
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE),
            chargeCounterUah = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            currentNowUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        )
        onLogDataItem(System.currentTimeMillis(), reading.toJson())
    }

    override fun isAvailable(context: Context): Boolean = true

    override fun availableForPeriodicSampling(): Boolean = true

    override fun stop() {
        // nothing is kept running between cycles
    }
}

@Serializable
data class BatteryReading(
    /** null if the level is unknown */
    val percent: Float?,
    /** charging, discharging, full, not_charging or unknown */
    val status: String,
    /** ac, usb, wireless, dock, or null when on battery */
    val plugged: String?,
    val temperatureC: Float?,
    val voltageMv: Int?,
    /** remaining charge in µAh; finer than percent, but not reported by every device */
    val chargeCounterUah: Int?,
    /** instantaneous current in µA; sign and accuracy differ between manufacturers */
    val currentNowUa: Int?
) {
    fun toJson(): String = Json.encodeToString(this)

    companion object {
        /** BatteryManager reports unsupported properties as Int.MIN_VALUE (some devices use 0). */
        private fun supported(value: Int?): Int? = value?.takeIf { it != Int.MIN_VALUE && it != 0 }

        fun of(
            level: Int,
            scale: Int,
            status: Int,
            plugged: Int,
            temperatureDeciC: Int,
            voltageMv: Int,
            chargeCounterUah: Int?,
            currentNowUa: Int?
        ) = BatteryReading(
            percent = if (level >= 0 && scale > 0) level * 100f / scale else null,
            status = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
                else -> "unknown"
            },
            plugged = when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
                BatteryManager.BATTERY_PLUGGED_DOCK -> "dock"
                else -> null
            },
            temperatureC = temperatureDeciC.takeIf { it != Int.MIN_VALUE }?.let { it / 10f },
            voltageMv = voltageMv.takeIf { it != Int.MIN_VALUE },
            chargeCounterUah = supported(chargeCounterUah),
            currentNowUa = supported(currentNowUa)
        )
    }
}
