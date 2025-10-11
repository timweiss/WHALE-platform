package de.mimuc.senseeverything.sensor.implementation

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.sensor.AbstractSensor
import java.util.Calendar
import java.util.TreeMap

class UsageStatsSensor(applicationContext: Context?, database: AppDatabase?) :
    AbstractSensor(applicationContext, database) {
    init {
        m_IsRunning = false
        TAG = "UsageStatsSensor"
        SENSOR_NAME = "Usage Stats"
        FILE_NAME = "usage_stats.csv"
        m_FileHeader = "TimeUnix,Stats"
    }

    override fun isAvailable(context: Context): Boolean {
        return true
    }

    override fun availableForPeriodicSampling(): Boolean {
        return true
    }

    override fun start(context: Context) {
        super.start(context)

        val t = System.currentTimeMillis()
        val usm =
            context.applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val appList: List<UsageStats> = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            System.currentTimeMillis() - millisPassedForDay(),
            System.currentTimeMillis()
        )

        if (appList.isNotEmpty()) {
            val mySortedMap = TreeMap<String, Long>()
            for (usageStats in appList) {
                if (usageStats.totalTimeInForeground > 0) {
                    mySortedMap.put(usageStats.packageName, usageStats.totalTimeInForeground)
                }
            }

            onLogDataItem(t, mySortedMap.toString())
        }

        val standbyBucket = usm.appStandbyBucket
        val enum = when (standbyBucket) {
            UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "Active"
            UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "Working Set"
            UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "Frequent"
            UsageStatsManager.STANDBY_BUCKET_RARE -> "Rare"
            UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "Restricted"
            else -> "Unknown"
        }
        onLogDataItem(t, "App Standby Bucket: $standbyBucket ($enum)")

        m_IsRunning = true
    }

    private fun millisPassedForDay(): Long {
        val calendar = Calendar.getInstance()
        val now = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return now - calendar.timeInMillis
    }

    override fun stop() {
        if (m_IsRunning) {
            m_IsRunning = false
            closeDataSource()
        }
    }

    companion object {
        private const val serialVersionUID = 1L
    }
}
