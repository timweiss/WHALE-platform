package de.mimuc.senseeverything.service.accessibility

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.PendingQuestionnaireStatus
import de.mimuc.senseeverything.db.models.ScheduledAlarm
import de.mimuc.senseeverything.helpers.goAsync
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.esm.EsmHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

/** What a rating questionnaire refers to, stored as the pending questionnaire's context. */
@Serializable
data class ScreenshotRatingContext(val screenshotPeriodEnd: Long) {
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Null if there is no context or it is another module's. */
        fun fromJson(contextJson: String?): ScreenshotRatingContext? = try {
            contextJson?.let { json.decodeFromString<ScreenshotRatingContext>(it) }
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}

/** The rating state of one pending questionnaire that refers to a period. */
data class ScreenshotRating(val completed: Boolean, val validUntil: Long) {
    fun isOpen(now: Long) = !completed && (validUntil == -1L || validUntil > now)
}

/**
 * Ended periods are kept this long before they may be deleted, so a capture still in flight at the
 * rating time can't write into a deleted period.
 */
const val SCREENSHOT_DELETE_GRACE_MS = 5 * 60_000L

/**
 * Prompted periods that may be deleted: no rating is open for them anymore (rated, expired, or never
 * created because no questionnaire listens for the event). Screenshots don't carry over to the next rating.
 */
fun screenshotPeriodsToDelete(
    requested: List<Long>,
    ratings: Map<Long, List<ScreenshotRating>>,
    now: Long
): List<Long> = requested.filter { end ->
    end <= now - SCREENSHOT_DELETE_GRACE_MS && ratings[end].orEmpty().none { it.isOpen(now) }
}

/** When the next period ends, which is when its rating has to be requested. */
fun nextScreenshotPeriodEnd(schedule: List<ScreenshotPeriod>?, now: Long): Long? =
    schedule?.map { it.end }?.filter { it > now }?.minOrNull()

/**
 * When a prompted period may become deletable without anything else waking the receiver: an open
 * rating expires, or a grace time ends. A completed rating is only noticed on the next run.
 */
fun nextScreenshotDeletionCheck(
    requested: List<Long>,
    ratings: Map<Long, List<ScreenshotRating>>,
    now: Long
): Long? = listOfNotNull(
    requested.flatMap { ratings[it].orEmpty() }
        .filter { it.isOpen(now) && it.validUntil != -1L }
        .minOfOrNull { it.validUntil },
    requested.filter { it > now - SCREENSHOT_DELETE_GRACE_MS }.minOfOrNull { it + SCREENSHOT_DELETE_GRACE_MS }
).minOrNull()

/**
 * Two steps, each telling when it has to run next; the receiver schedules itself for the earlier time.
 *
 * 1. [requestRatings], at every period end (see [planScreenshotPeriods]), so the rating questionnaire
 *    and the period share one clock. For each ended period not prompted yet, it emits [EVENT] if the
 *    period has samples; a questionnaire with an event trigger for it rates exactly that period, valid
 *    from its end. Its [ScreenshotRatingContext] names the period.
 * 2. [deleteClosedPeriods], when a rating expires or a grace time ends: deletes the prompted periods
 *    whose rating is no longer open (see [screenshotPeriodsToDelete]).
 *
 * Runs in the main process. Idempotent, so it also catches up after a reboot.
 */
@AndroidEntryPoint
class ScreenshotPeriodEndReceiver : BroadcastReceiver() {
    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context?, intent: Intent?) = goAsync {
        val applicationContext = context?.applicationContext ?: return@goAsync
        handle(applicationContext, database, dataStoreManager)
    }

    companion object {
        private const val TAG = "ScreenshotPeriodEnd"
        const val EVENT = "screenshot_period_ended"

        // one fixed alarm: scheduling the next one replaces it
        val REQUEST_CODE = ScheduledAlarm.generateRequestCode("ScreenshotPeriodEndReceiver", "next")

        suspend fun handle(context: Context, database: AppDatabase, dataStoreManager: DataStoreManager) {
            val root = ScreenshotStore.root(context)
            val schedule = dataStoreManager.screenshotScheduleFlow.first()
            val now = System.currentTimeMillis()

            requestRatings(context, root, database, dataStoreManager, now)
            val nextDeletionCheck = withContext(Dispatchers.IO) { deleteClosedPeriods(root, schedule, database, now) }

            val next = listOfNotNull(nextScreenshotPeriodEnd(schedule, now), nextDeletionCheck).minOrNull()
            if (next != null) {
                setAlarm(context, next)
            } else {
                WHALELog.i(TAG, "No further period end or rating to wait for")
            }
        }

        /** Step 1: emits [EVENT] once for every ended period. */
        private suspend fun requestRatings(
            context: Context,
            root: File,
            database: AppDatabase,
            dataStoreManager: DataStoreManager,
            now: Long
        ) {
            val unprompted = withContext(Dispatchers.IO) {
                ScreenshotStore.periodEnds(root).filter { it <= now && !ScreenshotStore.isRatingRequested(root, it) }
            }
            for (end in unprompted) {
                val samples = withContext(Dispatchers.IO) { ScreenshotStore.sampleCount(root, end) }
                if (samples > 0) {
                    WHALELog.i(TAG, "Requesting rating of period $end with $samples samples")
                    EsmHandler.handleEvent(
                        EVENT, null, null, context, dataStoreManager, database,
                        validFrom = end,
                        contextJson = ScreenshotRatingContext(end).toJson()
                    )
                } else {
                    WHALELog.i(TAG, "Not requesting rating of period $end, it has no samples")
                }
                // after the event: a crash in between prompts twice rather than never
                withContext(Dispatchers.IO) { ScreenshotStore.markRatingRequested(root, end) }
            }
        }

        /**
         * Step 2: deletes the prompted periods whose rating is no longer open. Only prompted ones, so
         * a period is never deleted before its rating was requested. Returns when to check again.
         */
        private fun deleteClosedPeriods(
            root: File,
            schedule: List<ScreenshotPeriod>?,
            database: AppDatabase,
            now: Long
        ): Long? {
            val ratings = ratingsByPeriod(database)
            val requested = ScreenshotStore.periodEnds(root).filter { ScreenshotStore.isRatingRequested(root, it) }
            val deleted = screenshotPeriodsToDelete(requested, ratings, now)
            deleted.forEach {
                WHALELog.i(TAG, "Deleting period $it, its rating is no longer open")
                ScreenshotStore.deletePeriod(root, it, schedule)
            }
            return nextScreenshotDeletionCheck(requested - deleted.toSet(), ratings, now)
        }

        /** Ratings by period end; questionnaires with another module's context are skipped. */
        private fun ratingsByPeriod(database: AppDatabase): Map<Long, List<ScreenshotRating>> =
            database.pendingQuestionnaireDao().getWithContext().mapNotNull { pending ->
                val ratingContext = ScreenshotRatingContext.fromJson(pending.contextJson) ?: return@mapNotNull null
                ratingContext.screenshotPeriodEnd to ScreenshotRating(
                    pending.status == PendingQuestionnaireStatus.COMPLETED,
                    pending.validUntil
                )
            }.groupBy({ it.first }, { it.second })

        private fun setAlarm(context: Context, timestamp: Long) {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, ScreenshotPeriodEndReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pendingIntent)
            }
            WHALELog.i(TAG, "Scheduled next run at $timestamp")
        }
    }
}
