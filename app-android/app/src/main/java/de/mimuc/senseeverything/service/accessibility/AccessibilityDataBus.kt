package de.mimuc.senseeverything.service.accessibility

/**
 * In-process handoff from the accessibility consumers to the sensors that persist their data.
 *
 * This replaces the former system broadcasts (and, for UI trees, the Room staging table). It only
 * works because [AccessibilityLogService] and the LogService (which owns the sensors) both run in
 * the `:remote` process, see AndroidManifest.xml. If one of them moves to another process, this
 * handoff silently stops delivering data.
 *
 * If no listener is registered (the sensor is not running), data is dropped, matching the former
 * behaviour where nobody received the broadcast.
 */
object AccessibilityDataBus {
    fun interface NameEventListener {
        fun onNameEvent(line: String)
    }

    fun interface SnapshotBatchListener {
        /** @param payload the batch JSON, gzip-compressed and Base64 encoded (see [SnapshotBatchEncoder]) */
        fun onBatch(payload: String, count: Int, timestamp: Long)
    }

    @Volatile
    @JvmStatic
    var nameListener: NameEventListener? = null

    @Volatile
    @JvmStatic
    var snapshotBatchListener: SnapshotBatchListener? = null
}
