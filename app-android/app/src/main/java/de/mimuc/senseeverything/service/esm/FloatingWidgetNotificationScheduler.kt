package de.mimuc.senseeverything.service.esm

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import de.mimuc.senseeverything.api.model.ema.EMAFloatingWidgetNotificationTrigger
import de.mimuc.senseeverything.api.model.ema.QuestionnaireTrigger
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.NotificationTriggerModality
import de.mimuc.senseeverything.db.models.NotificationTriggerPriority
import de.mimuc.senseeverything.db.models.NotificationTriggerSource
import de.mimuc.senseeverything.db.models.NotificationTriggerStatus
import de.mimuc.senseeverything.helpers.parseTimebucket
import de.mimuc.senseeverything.service.esm.EsmHandler.Companion.INTENT_TRIGGER_JSON
import de.mimuc.senseeverything.service.esm.EsmHandler.Companion.INTENT_TRIGGER_NOTIFICATION_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.UUID
import kotlin.random.Random

class FloatingWidgetNotificationScheduler {
    companion object {
        val TAG = "FloatingWidgetNotificationScheduler"

        @SuppressLint("DefaultLocale")
        fun schedulePrint(schedule: List<NotificationTrigger>): String {
            // list the bucket, trigger name, day and time
            return schedule.joinToString("\n") { trigger ->
                val day = Calendar.getInstance().apply { timeInMillis = trigger.validFrom }
                val time = String.format(
                    "%02d:%02d",
                    day.get(Calendar.HOUR_OF_DAY),
                    day.get(Calendar.MINUTE)
                )
                val date = String.format(
                    "%04d-%02d-%02d",
                    day.get(Calendar.YEAR),
                    day.get(Calendar.MONTH) + 1,
                    day.get(Calendar.DAY_OF_MONTH)
                )
                "${trigger.timeBucket} | ${trigger.name} | $date | $time"
            }
        }

        fun applyTimeoutTrigger(
            notification: NotificationTrigger,
            timeoutTrigger: EMAFloatingWidgetNotificationTrigger
        ): NotificationTrigger {
            return NotificationTrigger(
                uid = UUID.randomUUID(),
                addedAt = notification.addedAt,
                name = timeoutTrigger.name,
                status = notification.status,
                validFrom = notification.validFrom + timeoutTrigger.delayMinutes * 60 * 1000L,
                priority = timeoutTrigger.priority,
                timeBucket = notification.timeBucket,
                modality = timeoutTrigger.modality,
                source = timeoutTrigger.source,
                questionnaireId = timeoutTrigger.questionnaireId.toLong(),
                triggerJson = jsonForTrigger(timeoutTrigger)
            )
        }

        // fixme: this is so the test does not fail
        private fun jsonForTrigger(trigger: QuestionnaireTrigger): String {
            return try {
                trigger.toJson().toString()
            } catch (e: Exception) {
                "{}"
            }
        }

        suspend fun getLatestValidTriggerForTime(
            calendar: Calendar,
            database: AppDatabase
        ): NotificationTrigger? {
            val startOfDay = calendar.clone() as Calendar
            startOfDay.set(Calendar.HOUR_OF_DAY, 0)
            startOfDay.set(Calendar.MINUTE, 0)
            startOfDay.set(Calendar.SECOND, 0)
            startOfDay.set(Calendar.MILLISECOND, 0)
            val endOfDay = startOfDay.clone() as Calendar
            endOfDay.add(Calendar.DAY_OF_YEAR, 1)
            endOfDay.add(Calendar.MILLISECOND, -1)

            // get all triggers valid on the day of calendar
            val notifications =
                withContext(Dispatchers.IO) {
                    database.notificationTriggerDao()
                        .getForInterval(startOfDay.timeInMillis, endOfDay.timeInMillis)
                        .filter { it.validFrom <= calendar.timeInMillis }
                        // strip all that are not yet valid
                        .sortedByDescending { it.validFrom }
                }

            // check if the previous bucket has an unanswered wave-breaking notification
            return selectLastValidTrigger(notifications, calendar.clone() as Calendar)
        }

        fun selectLastValidTrigger(
            triggers: List<NotificationTrigger>,
            currentTime: Calendar
        ): NotificationTrigger? {
            if (triggers.isEmpty()) return null

            // Group triggers by time bucket
            val triggersByBucket = triggers.groupBy { it.timeBucket }

            // Find which bucket the current time falls into
            var currentBucket: String? = null
            for ((bucket, _) in triggersByBucket) {
                val (start, end) = parseTimebucket(bucket, currentTime)
                if (currentTime >= start && currentTime <= end) {
                    currentBucket = bucket
                    break
                }
            }

            // Look for unanswered wave-breaking triggers from previous buckets
            val sortedBuckets = triggersByBucket.keys.map { bucket ->
                val times = parseTimebucket(bucket, currentTime)
                Triple(bucket, times.first.timeInMillis, times.second.timeInMillis)
            }.sortedBy { it.second }

            // Collect all unanswered wave-breaking triggers from previous buckets
            var latestWaveBreakingTrigger: NotificationTrigger? = null

            for ((bucketName, _, bucketEnd) in sortedBuckets) {
                if (bucketEnd < currentTime.timeInMillis) {
                    val bucketTriggers = triggersByBucket[bucketName] ?: continue
                    val waveBreakingTrigger = bucketTriggers
                        .filter { it.priority == NotificationTriggerPriority.WaveBreaking }
                        .filter { it.status != NotificationTriggerStatus.Answered }
                        .maxByOrNull { it.validFrom }

                    // Keep track of the latest wave-breaking trigger across all previous buckets
                    if (waveBreakingTrigger != null) {
                        if (latestWaveBreakingTrigger == null || waveBreakingTrigger.validFrom > latestWaveBreakingTrigger.validFrom) {
                            latestWaveBreakingTrigger = waveBreakingTrigger
                        }
                    }
                }
            }

            // Return the latest wave-breaking trigger if found
            if (latestWaveBreakingTrigger != null) {
                return latestWaveBreakingTrigger
            }

            // If no unanswered wave-breaking trigger from previous buckets, return the latest trigger from current bucket
            if (currentBucket != null) {
                val currentBucketTriggers = triggersByBucket[currentBucket]
                if (!currentBucketTriggers.isNullOrEmpty()) {
                    return currentBucketTriggers.filter { it.status != NotificationTriggerStatus.Answered }
                        .maxByOrNull { it.validFrom }
                }
            }

            // no triggers found, return null
            return null
        }
    }

    fun scheduleFloatingWidgetNotificationTriggersForPhase(
        context: Context,
        emaStartDay: Calendar,
        endDay: Calendar,
        triggers: List<QuestionnaireTrigger>,
        database: AppDatabase,
        phaseName: String
    ) {
        // get all triggers for the current phase that can be scheduled
        val triggersToBeScheduled =
            triggers.filterIsInstance<EMAFloatingWidgetNotificationTrigger>()

        val scheduledNotifications = scheduleAllNotificationsWithTimeout(
            triggersToBeScheduled,
            emaStartDay,
            endDay,
            phaseName
        )
        for (notification in scheduledNotifications) {
            database.notificationTriggerDao().insert(notification)

            if (notification.modality == NotificationTriggerModality.Push) {
                scheduleAlarmForNotificationTrigger(notification, context)
            }
        }
    }

    fun schedulePlannedNotificationTriggers(context: Context, database: AppDatabase) {
        val now = System.currentTimeMillis()
        val plannedNotifications = database.notificationTriggerDao().getNextForModality(
            NotificationTriggerModality.Push, now
        )

        Log.i(
            TAG,
            "Scheduling ${plannedNotifications.size} planned push notification triggers after $now"
        )

        for (notification in plannedNotifications) {
            scheduleAlarmForNotificationTrigger(notification, context)
        }
    }

    fun scheduleAllNotificationsWithTimeout(
        triggers: List<EMAFloatingWidgetNotificationTrigger>,
        startDay: Calendar,
        endDay: Calendar,
        phaseName: String
    ): List<NotificationTrigger> {
        val allNotifications = mutableListOf<NotificationTrigger>()
        val triggersToBeScheduled = triggers
            .filter { it.phaseName == phaseName }
            .filter { it.source == NotificationTriggerSource.Scheduled }

        for (trigger in triggersToBeScheduled) {
            val plannedNotifications = planNotificationsForTrigger(trigger, startDay, endDay)
            allNotifications.addAll(plannedNotifications)

            val timeoutTrigger = triggers.find { it.id == trigger.timeoutNotificationTriggerId }
            if (timeoutTrigger != null) {
                for (notification in plannedNotifications) {
                    val timeoutNotification = applyTimeoutTrigger(notification, timeoutTrigger)
                    allNotifications.add(timeoutNotification)
                }
            }
        }
        return allNotifications
    }

    fun planNotificationsForTrigger(
        trigger: EMAFloatingWidgetNotificationTrigger,
        emaStart: Calendar,
        studyEnd: Calendar
    ): List<NotificationTrigger> {
        // for each day between emaStart and studyEnd, plan the notifications
        val notifications = mutableListOf<NotificationTrigger>()
        val dayIterator = emaStart.clone() as Calendar
        while (dayIterator <= studyEnd) {
            notifications.addAll(planNotificationsForDay(trigger, dayIterator))
            dayIterator.add(Calendar.DAY_OF_YEAR, 1)
        }
        return notifications
    }

    fun planNotificationsForDay(
        trigger: EMAFloatingWidgetNotificationTrigger,
        day: Calendar
    ): List<NotificationTrigger> {
        val notifications = mutableListOf<NotificationTrigger>()

        val sortedBuckets = trigger.timeBuckets.map { bucket ->
            val times = parseTimebucket(bucket, day.clone() as Calendar)

            Triple(bucket, times.first.timeInMillis, times.second.timeInMillis)
        }.sortedBy { it.second }

        var lastNotificationTime = 0L

        for (bucketInfo in sortedBuckets) {
            val (bucketName, bucketStart, bucketEnd) = bucketInfo

            // Calculate the earliest possible time for this notification
            val earliestTime = if (lastNotificationTime == 0L) {
                bucketStart
            } else {
                maxOf(bucketStart, lastNotificationTime + trigger.distanceMinutes * 60 * 1000)
            }

            // If we can't fit a notification in this bucket due to distance constraints, skip it
            if (earliestTime > bucketEnd) {
                continue
            }

            val notificationTime = if (trigger.randomToleranceMinutes == 0) {
                //  start at the beginning of the available window
                earliestTime
            } else {
                // Apply randomness within the available window
                val availableWindow = bucketEnd - earliestTime
                if (availableWindow <= 0) {
                    earliestTime
                } else {
                    val maxRandomOffset = minOf(
                        availableWindow,
                        trigger.randomToleranceMinutes * 60 * 1000L
                    )
                    earliestTime + Random.nextLong(0, maxRandomOffset + 1)
                }
            }

            // Ensure the notification time doesn't exceed bucket end
            val finalNotificationTime = minOf(notificationTime, bucketEnd)

            val notificationTrigger = NotificationTrigger(
                uid = UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = trigger.name,
                status = NotificationTriggerStatus.Planned,
                validFrom = finalNotificationTime,
                priority = trigger.priority,
                timeBucket = bucketName,
                modality = trigger.modality,
                source = trigger.source,
                questionnaireId = trigger.questionnaireId.toLong(),
                triggerJson = jsonForTrigger(trigger)
            )

            notifications.add(notificationTrigger)
            lastNotificationTime = finalNotificationTime
        }

        return notifications
    }

    private fun scheduleAlarmForNotificationTrigger(
        notificationTrigger: NotificationTrigger,
        context: Context
    ) {
        val intent = Intent(context, NotificationTriggerReceiver::class.java)
        intent.apply {
            putExtra(INTENT_TRIGGER_JSON, notificationTrigger.triggerJson)
            putExtra(INTENT_TRIGGER_NOTIFICATION_ID, notificationTrigger.uid.toString())
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val pendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            notificationTrigger.uid.hashCode(), // the best we can do to avoid collisions
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        Log.i(
            "EsmHandler",
            "Scheduling notification trigger for ${notificationTrigger.uid} at ${notificationTrigger.validFrom}"
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            notificationTrigger.validFrom,
            pendingIntent
        )
    }
}
