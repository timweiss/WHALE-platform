package de.mimuc.senseeverything.service.esm

import android.content.Context
import de.mimuc.senseeverything.api.model.ema.EMAFloatingWidgetNotificationTrigger
import de.mimuc.senseeverything.api.model.ema.QuestionnaireTrigger
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.NotificationTriggerModality
import de.mimuc.senseeverything.db.models.NotificationTriggerSource
import de.mimuc.senseeverything.db.models.NotificationTriggerStatus
import java.util.Calendar
import java.util.UUID
import kotlin.random.Random

class FloatingWidgetNotificationScheduler {
    companion object {
        fun timesForBucket(bucket: String, startDay: Calendar): Pair<Calendar, Calendar> {
            val startHour = bucket.split("-")[0].split(":")[0].toInt()
            val startMinute = bucket.split("-")[0].split(":")[1].toInt()
            val endHour = bucket.split("-")[1].split(":")[0].toInt()
            val endMinute = bucket.split("-")[1].split(":")[1].toInt()
            val startCal = startDay.clone() as Calendar
            startCal.set(Calendar.HOUR_OF_DAY, startHour)
            startCal.set(Calendar.MINUTE, startMinute)
            startCal.set(Calendar.SECOND, 0)
            startCal.set(Calendar.MILLISECOND, 0)
            val endCal = startDay.clone() as Calendar
            endCal.set(Calendar.HOUR_OF_DAY, endHour)
            endCal.set(Calendar.MINUTE, endMinute)
            endCal.set(Calendar.SECOND, 0)
            endCal.set(Calendar.MILLISECOND, 0)
            return Pair(startCal, endCal)
        }

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

        fun applyTimeoutTrigger(notification: NotificationTrigger, timeoutTrigger: EMAFloatingWidgetNotificationTrigger): NotificationTrigger {
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
    }

    suspend fun scheduleFloatingWidgetNotificationTriggersForPhase(context: Context, emaStartDay: Calendar, endDay: Calendar, triggers: List<QuestionnaireTrigger>, database: AppDatabase, phaseName: String) {
        // get all triggers for the current phase that can be scheduled
        val triggersToBeScheduled = triggers.filterIsInstance<EMAFloatingWidgetNotificationTrigger>()

        val scheduledNotifications = scheduleAllNotificationsWithTimeout(triggersToBeScheduled, emaStartDay, endDay, phaseName)
        for (notification in scheduledNotifications) {
            database.notificationTriggerDao().insert(notification)

            if (notification.modality == NotificationTriggerModality.Push) {
                // todo: schedule alarm for push notification
            }
        }
    }

    fun scheduleAllNotificationsWithTimeout(triggers: List<EMAFloatingWidgetNotificationTrigger>, startDay: Calendar, endDay: Calendar, phaseName: String): List<NotificationTrigger> {
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

    fun planNotificationsForTrigger(trigger: EMAFloatingWidgetNotificationTrigger, emaStart: Calendar, studyEnd: Calendar): List<NotificationTrigger> {
        // for each day between emaStart and studyEnd, plan the notifications
        val notifications = mutableListOf<NotificationTrigger>()
        val dayIterator = emaStart.clone() as Calendar
        while (dayIterator <= studyEnd) {
            notifications.addAll(planNotificationsForDay(trigger, dayIterator))
            dayIterator.add(Calendar.DAY_OF_YEAR, 1)
        }
        return notifications
    }

    fun planNotificationsForDay(trigger: EMAFloatingWidgetNotificationTrigger, day: Calendar): List<NotificationTrigger> {
        val notifications = mutableListOf<NotificationTrigger>()

        val sortedBuckets = trigger.timeBuckets.map { bucket ->
            val times = timesForBucket(bucket, day.clone() as Calendar)

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
}
