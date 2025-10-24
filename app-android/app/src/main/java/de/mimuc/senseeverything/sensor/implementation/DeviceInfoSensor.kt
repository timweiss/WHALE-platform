package de.mimuc.senseeverything.sensor.implementation

import android.content.Context
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
        val appBuildDebug: Boolean
    )

    override fun start(context: Context?) {
        val info = DeviceInfo(
            Build.DEVICE,
            Build.MANUFACTURER,
            Build.MODEL,
            Build.VERSION.SDK_INT,
            BuildConfig.VERSION_CODE,
            BuildConfig.VERSION_NAME,
            BuildConfig.DEBUG
        )

        onLogDataItem(System.currentTimeMillis(), Json.encodeToString(info))
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