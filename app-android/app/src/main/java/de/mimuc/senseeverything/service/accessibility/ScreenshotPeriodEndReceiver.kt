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
import javax.inject.Inject

/** What a rating questionnaire refers to, stored as the pending questionnaire's context. */
@Serializable
data class ScreenshotRatingContext(val screenshotPeriodEnd: Long)

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

/** When the receiver has to run next: the next period end, a rating's expiry, or the end of a grace time. */
fun nextScreenshotAlarm(
    schedule: List<ScreenshotPeriod>?,
    requested: List<Long>,
    ratings: Map<Long, List<ScreenshotRating>>,
    now: Long
): Long? = listOfNotNull(
    schedule?.map { it.end }?.filter { it > now }?.minOrNull(),
    requested.flatMap { ratings[it].orEmpty() }
        .filter { it.isOpen(now) && it.validUntil != -1L }
        .minOfOrNull { it.validUntil },
    requested.filter { it > now - SCREENSHOT_DELETE_GRACE_MS }.minOfOrNull { it + SCREENSHOT_DELETE_GRACE_MS }
).minOrNull()

/**
 * Runs at every period end (see [planScreenshotPeriods]), so the rating questionnaire and the
 * period share one clock. For each ended period not prompted yet, it emits [EVENT] if the period has
 * samples; a questionnaire with an event trigger for it rates exactly that period, valid from its end.
 * Afterwards it deletes the periods whose rating is no longer open and schedules itself again.
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

        private val json = Json { ignoreUnknownKeys = true }

        suspend fun handle(context: Context, database: AppDatabase, dataStoreManager: DataStoreManager) {
            val root = ScreenshotStore.root(context)
            val schedule = dataStoreManager.screenshotScheduleFlow.first()
            val now = System.currentTimeMillis()

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
                        contextJson = json.encodeToString(ScreenshotRatingContext(end))
                    )
                } else {
                    WHALELog.i(TAG, "Not requesting rating of period $end, it has no samples")
                }
                // after the event: a crash in between prompts twice rather than never
                withContext(Dispatchers.IO) { ScreenshotStore.markRatingRequested(root, end) }
            }

            val next = withContext(Dispatchers.IO) {
                val ratings = ratingsByPeriod(database)
                val requested = ScreenshotStore.periodEnds(root).filter { ScreenshotStore.isRatingRequested(root, it) }
                val deleted = screenshotPeriodsToDelete(requested, ratings, now)
                deleted.forEach {
                    WHALELog.i(TAG, "Deleting period $it, its rating is no longer open")
                    ScreenshotStore.deletePeriod(root, it, schedule)
                }
                nextScreenshotAlarm(schedule, requested - deleted.toSet(), ratings, now)
            }

            if (next != null) {
                setAlarm(context, next)
            } else {
                WHALELog.i(TAG, "No further period end or rating to wait for")
            }
        }

        /** Ratings by period end; questionnaires with another module's context are skipped. */
        private fun ratingsByPeriod(database: AppDatabase): Map<Long, List<ScreenshotRating>> =
            database.pendingQuestionnaireDao().getWithContext().mapNotNull { pending ->
                val ratingContext = try {
                    json.decodeFromString<ScreenshotRatingContext>(pending.contextJson!!)
                } catch (e: IllegalArgumentException) {
                    return@mapNotNull null
                }
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
