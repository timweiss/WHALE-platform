package de.mimuc.senseeverything.service.esm

import de.mimuc.senseeverything.api.model.ema.EMAFloatingWidgetNotificationTrigger
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.NotificationTriggerModality
import de.mimuc.senseeverything.db.models.NotificationTriggerPriority
import de.mimuc.senseeverything.db.models.NotificationTriggerSource
import de.mimuc.senseeverything.db.models.NotificationTriggerStatus
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

    @Test
    fun eachDayHasRequiredNotifications() {
        val trigger = defaultStudyTrigger

        val studyStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val studyEnd = studyStart.clone() as Calendar
        studyEnd.add(Calendar.DAY_OF_YEAR, 14)

        val scheduler = FloatingWidgetNotificationScheduler()
        val notifications = scheduler.planNotificationsForTrigger(trigger, studyStart, studyEnd)
        println(FloatingWidgetNotificationScheduler.schedulePrint(notifications))
        val days = notifications.groupBy { notif ->
            val cal = Calendar.getInstance().apply { timeInMillis = notif.validFrom }
            String.format("%04d-%02d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        }
        assert(days.size == 15) { "Expected 15 days of notifications, but got ${days.size}" }
        for ((day, notifs) in days) {
            assert(notifs.size == trigger.timeBuckets.size) {
                "Expected ${trigger.timeBuckets.size} notifications on $day, but got ${notifs.size}"
            }
        }
    }

    @Test
    fun hasTimeoutNotificationInScheduleAfterDelay() {
        val trigger = EMAFloatingWidgetNotificationTrigger(
            id = 1,
            questionnaireId = 1,
            validUntil = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000,
            distanceMinutes = 0,
            randomToleranceMinutes = 0,
            delayMinutes = 15,
            timeBuckets = listOf("00:00-23:59"),
            phaseName = "test",
            configuration = emptyMap<String, String>(),
            name = "Test",
            priority = NotificationTriggerPriority.Default,
            notificationText = "Test",
            timeoutNotificationTriggerId = 2,
            source = NotificationTriggerSource.Scheduled,
            modality = NotificationTriggerModality.Push
        )
        val timeoutTrigger = EMAFloatingWidgetNotificationTrigger(
            id = 2,
            questionnaireId = 1,
            validUntil = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000,
            distanceMinutes = 0,
            randomToleranceMinutes = 0,
            delayMinutes = 15,
            timeBuckets = listOf(),
            phaseName = "test",
            configuration = emptyMap<String, String>(),
            name = "Timeout",
            priority = NotificationTriggerPriority.Default,
            notificationText = "Timeout",
            timeoutNotificationTriggerId = null,
            source = NotificationTriggerSource.Scheduled,
            modality = NotificationTriggerModality.EventContingent
        )

        val scheduler = FloatingWidgetNotificationScheduler()
        val notifications = scheduler.scheduleAllNotificationsWithTimeout(listOf(trigger, timeoutTrigger), Calendar.getInstance(), Calendar.getInstance(), "test")
        println(FloatingWidgetNotificationScheduler.schedulePrint(notifications))
        assert(notifications.size == 2) { "Expected 2 notifications 2x(original + timeout), but got ${notifications.size}" }
        val original = notifications.find { it.name == "Test" }
        val timeout = notifications.find { it.name == "Timeout" }
        assert(original != null) { "Original notification not found" }
        assert(timeout != null) { "Timeout notification not found" }
        if (original != null && timeout != null) {
            val diff = (timeout.validFrom - original.validFrom) / (60 * 1000)
            assert(diff == trigger.delayMinutes.toLong()) { "Timeout notification is scheduled $diff minutes after original, but should be ${trigger.delayMinutes} minutes" }
        }
    }

    private fun normalTriggerRun(today: Calendar): List<NotificationTrigger> {
        val firstTriggerTime = today.clone() as Calendar
        firstTriggerTime.set(Calendar.HOUR_OF_DAY, 9)
        firstTriggerTime.set(Calendar.MINUTE, 0)
        firstTriggerTime.set(Calendar.SECOND, 0)
        firstTriggerTime.set(Calendar.MILLISECOND, 0)

        val currentTriggerTime = today.clone() as Calendar
        currentTriggerTime.set(Calendar.HOUR_OF_DAY, 11)
        currentTriggerTime.set(Calendar.MINUTE, 59)
        currentTriggerTime.set(Calendar.SECOND, 0)
        currentTriggerTime.set(Calendar.MILLISECOND, 0)

        val nextTriggerTime = today.clone() as Calendar
        nextTriggerTime.set(Calendar.HOUR_OF_DAY, 14)
        nextTriggerTime.set(Calendar.MINUTE, 30)
        nextTriggerTime.set(Calendar.SECOND, 0)
        nextTriggerTime.set(Calendar.MILLISECOND, 0)

        return listOf(NotificationTrigger(
            uid = java.util.UUID.randomUUID(),
            addedAt = System.currentTimeMillis(),
            name = "Last Bucket Trigger",
            status = NotificationTriggerStatus.Answered,
            validFrom = firstTriggerTime.timeInMillis,
            priority = NotificationTriggerPriority.Default,
            timeBucket = "09:00-11:29",
            modality = NotificationTriggerModality.Push,
            source = NotificationTriggerSource.Scheduled,
            questionnaireId = 1,
            triggerJson = "{}"
        ), NotificationTrigger(
            uid = java.util.UUID.randomUUID(),
            addedAt = System.currentTimeMillis(),
            name = "Current Bucket Trigger",
            status = NotificationTriggerStatus.Planned,
            validFrom = currentTriggerTime.timeInMillis,
            priority = NotificationTriggerPriority.Default,
            timeBucket = "11:30-13:59",
            modality = NotificationTriggerModality.Push,
            source = NotificationTriggerSource.Scheduled,
            questionnaireId = 1,
            triggerJson = "{}"
        ), NotificationTrigger(
            uid = java.util.UUID.randomUUID(),
            addedAt = System.currentTimeMillis(),
            name = "Future Bucket Trigger",
            status = NotificationTriggerStatus.Planned,
            validFrom = nextTriggerTime.timeInMillis,
            priority = NotificationTriggerPriority.Default,
            timeBucket = "14:00-16:29",
            modality = NotificationTriggerModality.Push,
            source = NotificationTriggerSource.Scheduled,
            questionnaireId = 1,
            triggerJson = "{}"
        ))
    }

    private fun triggerRunWithUnansweredWaveBreaking(today: Calendar): List<NotificationTrigger> {
        val morningTriggerTime = today.clone() as Calendar
        morningTriggerTime.set(Calendar.HOUR_OF_DAY, 8)
        morningTriggerTime.set(Calendar.MINUTE, 30)
        morningTriggerTime.set(Calendar.SECOND, 0)
        morningTriggerTime.set(Calendar.MILLISECOND, 0)

        val firstTriggerTime = today.clone() as Calendar
        firstTriggerTime.set(Calendar.HOUR_OF_DAY, 9)
        firstTriggerTime.set(Calendar.MINUTE, 0)
        firstTriggerTime.set(Calendar.SECOND, 0)
        firstTriggerTime.set(Calendar.MILLISECOND, 0)

        val currentTriggerTime = today.clone() as Calendar
        currentTriggerTime.set(Calendar.HOUR_OF_DAY, 11)
        currentTriggerTime.set(Calendar.MINUTE, 59)
        currentTriggerTime.set(Calendar.SECOND, 0)
        currentTriggerTime.set(Calendar.MILLISECOND, 0)

        val nextTriggerTime = today.clone() as Calendar
        nextTriggerTime.set(Calendar.HOUR_OF_DAY, 14)
        nextTriggerTime.set(Calendar.MINUTE, 30)
        nextTriggerTime.set(Calendar.SECOND, 0)
        nextTriggerTime.set(Calendar.MILLISECOND, 0)

        return listOf(
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "Morning Bucket Trigger",
                status = NotificationTriggerStatus.Displayed,
                validFrom = morningTriggerTime.timeInMillis,
                priority = NotificationTriggerPriority.WaveBreaking,
                timeBucket = "07:00-8:59",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}"
            ),
            NotificationTrigger(
            uid = java.util.UUID.randomUUID(),
            addedAt = System.currentTimeMillis(),
            name = "Last Bucket Trigger",
            status = NotificationTriggerStatus.Displayed,
            validFrom = firstTriggerTime.timeInMillis,
            priority = NotificationTriggerPriority.WaveBreaking,
            timeBucket = "09:00-11:29",
            modality = NotificationTriggerModality.Push,
            source = NotificationTriggerSource.Scheduled,
            questionnaireId = 1,
            triggerJson = "{}"
        ), NotificationTrigger(
            uid = java.util.UUID.randomUUID(),
            addedAt = System.currentTimeMillis(),
            name = "Current Bucket Trigger",
            status = NotificationTriggerStatus.Planned,
            validFrom = currentTriggerTime.timeInMillis,
            priority = NotificationTriggerPriority.Default,
            timeBucket = "11:30-13:59",
            modality = NotificationTriggerModality.Push,
            source = NotificationTriggerSource.Scheduled,
            questionnaireId = 1,
            triggerJson = "{}"
        ), NotificationTrigger(
            uid = java.util.UUID.randomUUID(),
            addedAt = System.currentTimeMillis(),
            name = "Future Bucket Trigger",
            status = NotificationTriggerStatus.Planned,
            validFrom = nextTriggerTime.timeInMillis,
            priority = NotificationTriggerPriority.Default,
            timeBucket = "14:00-16:29",
            modality = NotificationTriggerModality.Push,
            source = NotificationTriggerSource.Scheduled,
            questionnaireId = 1,
            triggerJson = "{}"
        ))
    }

    @Test
    fun getsLatestNotificationTriggerInCurrentBucket() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val triggers = normalTriggerRun(calendar)
        val latest = FloatingWidgetNotificationScheduler.selectLastValidTrigger(triggers, calendar)

        assert(latest != null) { "Expected to find a valid trigger, but got null" }
        if (latest != null) {
            assert(latest.name == "Current Bucket Trigger") { "Expected 'Current Bucket Trigger', but got '${latest.name}'" }
        }
    }

    /**
     * Even though we are in the time range of the "Current Bucket Trigger", we have to select the "Last Bucket Trigger", as it is wave-breaking but was not answered yet.
     */
    @Test
    fun getsLatestNotificationTriggerWaveBreakingFromLastBucket() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val triggers = triggerRunWithUnansweredWaveBreaking(calendar)
        val latest = FloatingWidgetNotificationScheduler.selectLastValidTrigger(triggers, calendar)

        assert(latest != null) { "Expected to find a valid trigger, but got null" }
        if (latest != null) {
            assert(latest.name == "Last Bucket Trigger") { "Expected 'Last Bucket Trigger', but got '${latest.name}'" }
        }
    }
}