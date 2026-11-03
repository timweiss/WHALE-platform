package de.mimuc.senseeverything.service.accessibility

import android.content.Context
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * On-disk state of the per-app screenshot reservoirs of one sampling period (which ends at a
 * scheduled rating time). Holds data only; which item goes into which slot is decided by the caller.
 *
 * Layout: `<root>/<periodEnd>/<package>/state.json` plus one image file per filled slot. The state
 * file is the source of truth: an image is part of the sample only while the state references it, so
 * the slot table and the image files cannot disagree after a crash. Files the state does not reference
 * (a capture in flight when the process died, a replaced image not yet deleted) are removed when the
 * package's state is first loaded.
 *
 * Not thread-safe; use it from the capture worker only. Other processes (the rating UI) may read a
 * period once it has ended, since the store doesn't write to it then. The main process marks ended
 * periods as prompted and deletes them (see [ScreenshotPeriodEndReceiver]).
 */
class ScreenshotStore(
    root: File,
    /** the scheduled rating time that ends this period */
    val periodEnd: Long,
    private val log: (String) -> Unit = { WHALELog.w(TAG, it) }
) {
    companion object {
        const val TAG = "ScreenshotStore"

        private const val STATE_FILE = "state.json"
        private const val IMAGE_EXTENSION = ".jpg"
        private const val REPORTED_FILE = "reported"
        private const val RATING_REQUESTED_FILE = "rating_requested"
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Internal storage is private to the app. The manifest allows backups, but noBackupFilesDir is
         * excluded from Auto Backup and device-to-device transfer, so screenshots never leave the device.
         */
        fun root(context: Context) = File(context.noBackupFilesDir, "screenshot_samples")

        fun periodDir(root: File, periodEnd: Long) = File(root, periodEnd.toString())

        /** End times of all periods on disk, oldest first. */
        fun periodEnds(root: File): List<Long> =
            root.list()?.mapNotNull { it.toLongOrNull() }?.sorted() ?: emptyList()

        /** Reports the period first (see [reportEndedPeriods]), its counts are deleted with it. */
        fun deletePeriod(
            root: File,
            periodEnd: Long,
            schedule: List<ScreenshotPeriod>?,
            log: (String) -> Unit = { WHALELog.i(TAG, it) }
        ) {
            reportPeriod(root, periodEnd, System.currentTimeMillis(), schedule, log)
            periodDir(root, periodEnd).deleteRecursively()
        }

        /**
         * Reports all periods first, including the running one (`complete=false`). Call it before the
         * DataStore (and with it the schedule) is erased.
         */
        fun deleteAll(
            root: File,
            schedule: List<ScreenshotPeriod>?,
            log: (String) -> Unit = { WHALELog.i(TAG, it) }
        ) {
            val now = System.currentTimeMillis()
            periodEnds(root).forEach { reportPeriod(root, it, now, schedule, log) }
            root.deleteRecursively()
        }

        /**
         * Logs a summary line per app for every ended period not reported yet. The counts are needed
         * for the analysis but otherwise stay on the device, and the log is uploaded. Called whenever a
         * store is opened, so a period is also reported after a process restart, at the latest when the
         * next period samples its first screen.
         */
        fun reportEndedPeriods(
            root: File,
            /** for the phase of each period */
            schedule: List<ScreenshotPeriod>?,
            now: Long = System.currentTimeMillis(),
            log: (String) -> Unit = { WHALELog.i(TAG, it) }
        ) {
            periodEnds(root).filter { it <= now }.forEach { reportPeriod(root, it, now, schedule, log) }
        }

        /** Once per period: a marker file records that it was reported. */
        private fun reportPeriod(
            root: File,
            periodEnd: Long,
            now: Long,
            schedule: List<ScreenshotPeriod>?,
            log: (String) -> Unit
        ) {
            val dir = periodDir(root, periodEnd)
            val marker = File(dir, REPORTED_FILE)
            if (!dir.isDirectory || marker.exists()) return

            val phaseName = schedule?.firstOrNull { it.end == periodEnd }?.phaseName ?: "?"
            readReservoirs(dir).forEach { (packageName, reservoir) ->
                // phase last: the name may contain spaces, so it runs to the end of the line
                log("sampling summary: periodEnd=$periodEnd package=$packageName" +
                        " itemsSeen=${reservoir.itemsSeen} itemsDropped=${reservoir.itemsDropped}" +
                        " samples=${reservoir.slots.count { it != null }} complete=${periodEnd <= now}" +
                        " phase=$phaseName")
            }
            try {
                marker.createNewFile()
            } catch (e: IOException) {
                // the main process deleted the period meanwhile, after reporting it itself
            }
        }

        /** Whether the rating of the ended period was requested, i.e. its event was handled. */
        fun isRatingRequested(root: File, periodEnd: Long) =
            File(periodDir(root, periodEnd), RATING_REQUESTED_FILE).exists()

        fun markRatingRequested(root: File, periodEnd: Long) {
            File(periodDir(root, periodEnd), RATING_REQUESTED_FILE).createNewFile()
        }

        /** Filled slots of all apps in the period, read-only like [readAll]. */
        fun sampleCount(root: File, periodEnd: Long): Int =
            readReservoirs(periodDir(root, periodEnd)).sumOf { (_, reservoir) -> reservoir.slots.count { it != null } }

        /**
         * Lists the samples of all periods without opening a store, so it is safe to call from another
         * process (debug or rating UI): unlike [reservoir], it never deletes files. An image can still
         * disappear between listing and reading it, if sampling replaces it meanwhile.
         */
        fun readAll(root: File): List<StoredSample> = periodEnds(root).flatMap { periodEnd ->
            readReservoirs(periodDir(root, periodEnd)).flatMap { (packageName, reservoir) ->
                val packageDir = File(periodDir(root, periodEnd), packageName)
                reservoir.slots.mapIndexedNotNull { slot, sample ->
                    sample?.let { StoredSample(periodEnd, packageName, slot, it, File(packageDir, it.file)) }
                }
            }
        }

        /** Read-only, skips apps whose state can't be read. */
        private fun readReservoirs(periodDir: File): List<Pair<String, Reservoir>> {
            val packageDirs = periodDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
            return packageDirs.mapNotNull { packageDir ->
                try {
                    packageDir.name to json.decodeFromString<Reservoir>(File(packageDir, STATE_FILE).readText())
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    data class StoredSample(
        val periodEnd: Long,
        val packageName: String,
        val slot: Int,
        val sample: Sample,
        val image: File
    )

    @Serializable
    data class Sample(
        val file: String,
        /** the [de.mimuc.senseeverything.service.accessibility.model.ScreenSnapshot.timestamp] of the UI tree */
        val timestamp: Long,
        val signature: String
    )

    /**
     * Every UI tree of the package logged in the period is counted in exactly one of [itemsSeen] and
     * [itemsDropped].
     */
    @Serializable
    data class Reservoir(
        /** items of this package in the sampled stream of the period, t in Algorithm R */
        val itemsSeen: Int = 0,
        /** index = slot (0-based), null while a slot is empty */
        val slots: List<Sample?> = emptyList(),
        /** selected items whose screenshot failed; taken out of the stream, so they had no chance */
        val itemsDropped: Int = 0
    )

    private val periodDir = periodDir(root, periodEnd)
    private val reservoirs = HashMap<String, Reservoir>()

    /** Once true, nothing may be committed anymore: the rating UI may already read the period. */
    fun hasEnded(now: Long = System.currentTimeMillis()) = now >= periodEnd

    fun reservoir(packageName: String): Reservoir =
        reservoirs.getOrPut(packageName) { load(packageName) }

    fun setItemsSeen(packageName: String, itemsSeen: Int) {
        save(packageName, reservoir(packageName).copy(itemsSeen = itemsSeen))
    }

    /**
     * Moves one item from [Reservoir.itemsSeen] to [Reservoir.itemsDropped], for a selected item whose
     * screenshot failed. Relative to the current count, since later items may have been counted meanwhile.
     */
    fun recordDropped(packageName: String) {
        val current = reservoir(packageName)
        save(packageName, current.copy(itemsSeen = current.itemsSeen - 1, itemsDropped = current.itemsDropped + 1))
    }

    /** A new, unreferenced file for a capture; pass it to [commit] or [discard]. */
    fun newImageFile(packageName: String): File {
        // load first, loading deletes unreferenced files
        reservoir(packageName)
        val dir = packageDir(packageName)
        dir.mkdirs()
        return File(dir, UUID.randomUUID().toString() + IMAGE_EXTENSION)
    }

    /** Puts [image] (from [newImageFile]) into [slot], replacing and deleting the image there. */
    fun commit(packageName: String, slot: Int, image: File, timestamp: Long, signature: String) {
        val current = reservoir(packageName)
        val slots = current.slots.toMutableList()
        while (slots.size <= slot) slots.add(null)
        val replaced = slots[slot]
        slots[slot] = Sample(image.name, timestamp, signature)

        save(packageName, current.copy(slots = slots))
        replaced?.let { File(packageDir(packageName), it.file).delete() }
    }

    fun discard(image: File) {
        image.delete()
    }

    fun imageFile(packageName: String, sample: Sample) = File(packageDir(packageName), sample.file)

    private fun packageDir(packageName: String) = File(periodDir, packageName)

    private fun load(packageName: String): Reservoir {
        val dir = packageDir(packageName)
        val stateFile = File(dir, STATE_FILE)
        val reservoir = try {
            if (stateFile.exists()) json.decodeFromString<Reservoir>(stateFile.readText()) else Reservoir()
        } catch (e: Exception) {
            when (e) {
                is IOException, is SerializationException, is IllegalArgumentException -> {
                    // its images are deleted below, so a broken state never leaves unreferenced screenshots
                    log("Discarding unreadable reservoir state of $packageName: ${e.message}")
                    Reservoir()
                }
                else -> throw e
            }
        }

        val referenced = reservoir.slots.mapNotNullTo(HashSet()) { it?.file }
        dir.listFiles()?.forEach { file ->
            if (file.name != STATE_FILE && file.name !in referenced) file.delete()
        }
        return reservoir
    }

    /**
     * Written on every change: whether a later item may replace a slot depends on the exact count,
     * so a count lost to a process restart would bias the sample towards what came after it.
     * The rename makes the write atomic against process death; without an fsync, a power loss
     * right after a write can lose it, which [load] then treats like an empty reservoir.
     */
    private fun save(packageName: String, reservoir: Reservoir) {
        val dir = packageDir(packageName)
        dir.mkdirs()
        val tmp = File(dir, "$STATE_FILE.tmp")
        tmp.writeText(json.encodeToString(reservoir))
        Files.move(
            tmp.toPath(), File(dir, STATE_FILE).toPath(),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
        )
        reservoirs[packageName] = reservoir
    }
}
