package de.mimuc.senseeverything.activity.esm.screenshot

import de.mimuc.senseeverything.service.accessibility.ScreenshotStore
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A sampled screenshot as shown for rating. Only refers to the image, which is decoded when displayed. */
data class ScreenshotReference(
    /** identifies the UI tree of the same screen, the key of its rating */
    val snapshotId: String,
    val packageName: String,
    val timestamp: Long,
    val image: File
) {
    /** with the date, as a period can span midnight */
    fun label(zone: ZoneId = ZoneId.systemDefault(), locale: Locale = Locale.getDefault()): String {
        val time = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale)
            .format(Instant.ofEpochMilli(timestamp).atZone(zone))
        return "$time · $packageName"
    }

    companion object {
        /** The screenshots of one period, oldest first; reads the disk. */
        fun ofPeriod(root: File, periodEnd: Long): List<ScreenshotReference> =
            ScreenshotStore.readPeriod(root, periodEnd)
                .map { ScreenshotReference(it.sample.snapshotId, it.packageName, it.sample.timestamp, it.image) }
                .sortedBy { it.timestamp }
    }
}
