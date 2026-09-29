package de.mimuc.senseeverything.sensor.implementation

import android.app.ActivityManager
import android.content.Context
import android.icu.util.TimeZone
import android.os.Build
import de.mimuc.senseeverything.BuildConfig
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.sensor.AbstractSensor
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DeviceInfoSensor(val context: Context, database: AppDatabase) :
    AbstractSensor(context, database) {

    init {
        SENSOR_NAME = "Device Info"
    }

    @Serializable
    private class DeviceInfo(
        val deviceName: String,
        val manufacturer: String,
        val model: String,
        val sdkLevel: Int,
        val appBuildVersionCode: Int,
        val appBuildVersionName: String,
        val appBuildDebug: Boolean,
        val totalMemMb: Long,
        val isLowRamDevice: Boolean,
        /** Java heap limit per app process */
        val memoryClassMb: Int
    )

    override fun start(context: Context?) {
        val activityManager = this.context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }

        val info = DeviceInfo(
            Build.DEVICE,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.VERSION.SDK_INT,
            BuildConfig.VERSION_CODE,
            BuildConfig.VERSION_NAME,
            BuildConfig.DEBUG,
            memoryInfo.totalMem / (1024 * 1024),
            activityManager.isLowRamDevice,
            activityManager.memoryClass
        )
        onLogDataItem(System.currentTimeMillis(), Json.encodeToString(info), "Version")

        val timeZone = TimeZone.getDefault()
        onLogDataItem(System.currentTimeMillis(), timeZone.id, "Timezone")
    }

    override fun isAvailable(context: Context?): Boolean {
        return true
    }

    override fun availableForPeriodicSampling(): Boolean {
        return false
    }

    override fun availableForContinuousSampling(): Boolean {
        return true
    }

    override fun stop() {
        // no need to do anything
    }
}