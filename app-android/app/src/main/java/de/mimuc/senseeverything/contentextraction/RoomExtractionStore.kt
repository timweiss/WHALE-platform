package de.mimuc.senseeverything.contentextraction

import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.LogData
import de.mimuc.senseeverything.sensor.implementation.UITreeSensor

/** [ExtractionStore] on the app database. Blocking, call it off the main thread. */
class RoomExtractionStore(database: AppDatabase) : ExtractionStore {
    private val dao = database.logDataDao()

    override fun nextUnhandledRow(): StoredRow? =
        dao.nextWithoutFlag(UITreeSensor.NAME, FLAG_EXTRACTION_HANDLED)
            ?.let { StoredRow(it.id, it.data.orEmpty()) }

    override fun saveHandled(rowId: Int, readings: List<LogData>) =
        dao.insertAndFlag(readings, rowId, FLAG_EXTRACTION_HANDLED)

    override fun flagAllUnhandled(): Int {
        val count = dao.countWithoutFlag(UITreeSensor.NAME, FLAG_EXTRACTION_HANDLED)
        dao.flagAll(UITreeSensor.NAME, FLAG_EXTRACTION_HANDLED)
        return count
    }
}
