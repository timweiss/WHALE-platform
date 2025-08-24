package de.mimuc.senseeverything.service.esm

import android.content.Context
import de.mimuc.senseeverything.api.model.ema.EMAFloatingWidgetNotificationTrigger
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.NotificationTrigger
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
    }

    suspend fun scheduleFloatingWidgetNotificationTriggersForPhase(context: Context, dataStoreManager: DataStoreManager, database: AppDatabase, phaseName: String) {
        // get all triggers for the current phase that can be scheduled

        // for each trigger, plan the schedule based on the time buckets and distance (+randomness)
        // the amount of notifications is based on the time buckets, each time bucket gets one notification
        // if the trigger has a delay, the first notification is scheduled after the delay
        // --> in case the trigger also has a timeout trigger, copy the schedule and add the delay
        // insert each trigger into the notification_trigger table
        // if it is a pushed trigger, create a notification
    }

    fun planNotificationsForTrigger(trigger: EMAFloatingWidgetNotificationTrigger, emaStart: Calendar, studyEnd: Calendar): List<NotificationTrigger> {
        return emptyList<NotificationTrigger>()
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
                triggerJson = ""
            )

            notifications.add(notificationTrigger)
            lastNotificationTime = finalNotificationTime
        }

        return notifications
    }
}
