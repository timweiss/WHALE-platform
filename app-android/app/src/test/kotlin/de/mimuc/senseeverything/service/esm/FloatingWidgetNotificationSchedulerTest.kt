package de.mimuc.senseeverything.service.esm

import de.mimuc.senseeverything.api.model.ema.EMAFloatingWidgetNotificationTrigger
import de.mimuc.senseeverything.db.models.NotificationTriggerModality
import de.mimuc.senseeverything.db.models.NotificationTriggerPriority
import de.mimuc.senseeverything.db.models.NotificationTriggerSource
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.Calendar

class FloatingWidgetNotificationSchedulerTest {
    private fun triggerWithBuckets(buckets: List<String>, distanceMinutes: Int, randomTolerance: Int) = EMAFloatingWidgetNotificationTrigger(
        id = 1,
        questionnaireId = 1,
        validUntil = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000,
        distanceMinutes = distanceMinutes,
        randomToleranceMinutes = randomTolerance,
        delayMinutes = 0,
        timeBuckets = buckets,
        phaseName = "test",
        configuration = emptyMap<String, String>(),
        name = "T1",
        priority = NotificationTriggerPriority.Default,
        notificationText = "Test",
        timeoutNotificationTriggerId = 0,
        source = NotificationTriggerSource.Scheduled,
        modality = NotificationTriggerModality.Push
    )

    private val defaultStudyTrigger = triggerWithBuckets(
        buckets = listOf("9:00-11:29", "11:30-13:59", "14:00-16:29", "16:30-18:59", "19:00-21:29"),
        distanceMinutes = 60,
        randomTolerance = 180
    )

    @Test
    fun testEachBucketShouldHaveOneNotification() {
        val trigger = defaultStudyTrigger

        val scheduler = FloatingWidgetNotificationScheduler()
        val notifications = scheduler.planNotificationsForDay(trigger, Calendar.getInstance())
        assert(notifications.size == defaultStudyTrigger.timeBuckets.size)
        print(FloatingWidgetNotificationScheduler.schedulePrint(notifications))

        val scheduledBuckets = notifications.map { it.timeBucket }.toSet()
        assert(scheduledBuckets.containsAll(trigger.timeBuckets))

        // check if timestamp is in bucket time, including minutes
        for (notification in notifications) {
            val bucket = notification.timeBucket
            val (start, end) = FloatingWidgetNotificationScheduler.timesForBucket(bucket, Calendar.getInstance())
            val notifTime = Calendar.getInstance().apply { timeInMillis = notification.validFrom }
            assert(notifTime >= start && notifTime <= end) { "Notification time ${notifTime.time} is not in bucket $bucket ($start - $end)" }
        }
    }

    @RepeatedTest(100)
    fun testDistanceIsRespected() {
        val trigger = defaultStudyTrigger

        val scheduler = FloatingWidgetNotificationScheduler()
        val notifications = scheduler.planNotificationsForDay(trigger, Calendar.getInstance())
        assert(notifications.size == defaultStudyTrigger.timeBuckets.size)

        println(FloatingWidgetNotificationScheduler.schedulePrint(notifications))
        println("----")

        // check if distance is respected
        val sortedNotifications = notifications.sortedBy { it.validFrom }
        for (i in 1 until sortedNotifications.size) {
            val diff = (sortedNotifications[i].validFrom - sortedNotifications[i - 1].validFrom) / (60 * 1000)
            assert(diff >= trigger.distanceMinutes) { "Distance between notifications is $diff minutes, but should be at least ${trigger.distanceMinutes} minutes" }
        }
    }
}