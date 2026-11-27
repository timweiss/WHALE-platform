package de.mimuc.senseeverything.contentextraction

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import java.io.File

/** Runtime measurements of the process a run executes in (:extraction). */
object Measurements {
    /** CPU time of this process (all threads), ms */
    fun cpuMs(): Long = Process.getElapsedCpuTime()

    /**
     * Resets the peak RSS (VmHWM) to the current RSS, so it covers only this run (Linux >= 4.0:
     * writing 5 to clear_refs). False if the kernel or SELinux doesn't allow it; then the peak may
     * include an earlier run in the same process.
     */
    fun resetPeakRss(): Boolean = runCatching {
        File("/proc/self/clear_refs").writeText("5")
        true
    }.getOrDefault(false)

    fun peakRssKb(): Long? = runCatching {
        File("/proc/self/status").readLines()
            .firstOrNull { it.startsWith("VmHWM:") }
            ?.split(Regex("\\s+"))?.getOrNull(1)?.toLongOrNull()
    }.getOrNull()

    fun battery(context: Context): BatteryState {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val counter = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        return BatteryState(
            levelPercent = level.takeIf { it in 0..100 },
            chargeCounterMicroAh = counter.takeIf { it != Int.MIN_VALUE && it > 0 }?.toLong(),
            charging = bm.isCharging
        )
    }

    fun thermalStatus(context: Context): Int =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus

    fun device(context: Context): DeviceDescription {
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        return DeviceDescription(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            socManufacturer = Build.SOC_MANUFACTURER,
            socModel = Build.SOC_MODEL,
            sdk = Build.VERSION.SDK_INT,
            totalRamBytes = memory.totalMem
        )
    }
}
