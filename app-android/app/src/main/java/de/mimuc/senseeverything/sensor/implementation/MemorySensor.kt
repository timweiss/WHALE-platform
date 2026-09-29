package de.mimuc.senseeverything.sensor.implementation

import android.content.Context
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.logging.MemorySnapshot
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.sensor.AbstractSensor

/**
 * Records the device's memory headroom and the sampling process' footprint while sampling, so
 * the memory use before a low-memory kill can be followed. Started with every sampling cycle but
 * writes at most one [MemorySnapshot] an hour.
 */
class MemorySensor(context: Context, database: AppDatabase) : AbstractSensor(context, database) {

    init {
        TAG = javaClass.name
        SENSOR_NAME = MemorySnapshot.SENSOR_NAME
    }

    override fun start(context: Context) {
        super.start(context)
        if (!m_isSensorAvailable || !MemorySnapshot.claimIfDue(MIN_INTERVAL_MS)) return

        // reads /proc, keep it off the main thread the sampling cycles run on
        Thread({
            try {
                onLogDataItem(System.currentTimeMillis(), MemorySnapshot.collect(context, "sampling").toJson())
            } catch (e: Exception) {
                WHALELog.e(TAG, "Failed to collect memory snapshot: ${e.message}", e)
            }
        }, "MemorySensor").start()
    }

    override fun isAvailable(context: Context): Boolean = true

    override fun availableForPeriodicSampling(): Boolean = true

    override fun stop() {
        // nothing is kept running between cycles
    }

    companion object {
        private const val MIN_INTERVAL_MS = 60 * 60 * 1000L
    }
}
