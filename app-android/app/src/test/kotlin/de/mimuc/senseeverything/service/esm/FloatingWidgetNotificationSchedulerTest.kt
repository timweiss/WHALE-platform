package de.mimuc.senseeverything.service.esm

import de.mimuc.senseeverything.api.model.ema.EMAFloatingWidgetNotificationTrigger
import de.mimuc.senseeverything.api.model.ema.EMAFloatingWidgetTriggerConfiguration
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.NotificationTriggerModality
import de.mimuc.senseeverything.db.models.NotificationTriggerPriority
import de.mimuc.senseeverything.db.models.NotificationTriggerSource
import de.mimuc.senseeverything.db.models.NotificationTriggerStatus
import de.mimuc.senseeverything.helpers.parseTimebucket
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.Calendar

class FloatingWidgetNotificationSchedulerTest {
    private fun triggerWithBuckets(
        buckets: List<String>,
        distanceMinutes: Int,
        randomTolerance: Int
    ) = EMAFloatingWidgetNotificationTrigger(
        id = 1,
        questionnaireId = 1,
        validDuration = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000,
        enabled = true,
        configuration = EMAFloatingWidgetTriggerConfiguration(
            distanceMinutes = distanceMinutes,
            randomToleranceMinutes = randomTolerance,
            delayMinutes = 0,
            timeBuckets = buckets,
            phaseName = "test",
            name = "T1",
            priority = NotificationTriggerPriority.Default,
            notificationText = "Test",
            timeoutNotificationTriggerId = 0,
            source = NotificationTriggerSource.Scheduled,
            modality = NotificationTriggerModality.Push
        )
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
        assert(notifications.size == defaultStudyTrigger.configuration.timeBuckets.size)
        print(FloatingWidgetNotificationScheduler.schedulePrint(notifications))

        val scheduledBuckets = notifications.map { it.timeBucket }.toSet()
        assert(scheduledBuckets.containsAll(trigger.configuration.timeBuckets))

        // check if timestamp is in bucket time, including minutes
        for (notification in notifications) {
            val bucket = notification.timeBucket
            val (start, end) = parseTimebucket(bucket, Calendar.getInstance())
            val notifTime = Calendar.getInstance().apply { timeInMillis = notification.validFrom }
            assert(notifTime >= start && notifTime <= end) { "Notification time ${notifTime.time} is not in bucket $bucket ($start - $end)" }
        }
    }

    @RepeatedTest(100)
    fun testDistanceIsRespected() {
        val trigger = defaultStudyTrigger

        val scheduler = FloatingWidgetNotificationScheduler()
        val notifications = scheduler.planNotificationsForDay(trigger, Calendar.getInstance())
        assert(notifications.size == defaultStudyTrigger.configuration.timeBuckets.size)

        println(FloatingWidgetNotificationScheduler.schedulePrint(notifications))
        println("----")

        // check if distance is respected
        val sortedNotifications = notifications.sortedBy { it.validFrom }
        for (i in 1 until sortedNotifications.size) {
            val diff =
                (sortedNotifications[i].validFrom - sortedNotifications[i - 1].validFrom) / (60 * 1000)
            assert(diff >= trigger.configuration.distanceMinutes) { "Distance between notifications is $diff minutes, but should be at least ${trigger.configuration.distanceMinutes} minutes" }
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
            String.format(
                "%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }
        assert(days.size == 15) { "Expected 15 days of notifications, but got ${days.size}" }
        for ((day, notifs) in days) {
            assert(notifs.size == trigger.configuration.timeBuckets.size) {
                "Expected ${trigger.configuration.timeBuckets.size} notifications on $day, but got ${notifs.size}"
            }
        }
    }

    @Test
    fun hasTimeoutNotificationInScheduleAfterDelay() {
        val trigger = EMAFloatingWidgetNotificationTrigger(
            id = 1,
            questionnaireId = 1,
            validDuration = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000,
            enabled = true,
            configuration = EMAFloatingWidgetTriggerConfiguration(
                distanceMinutes = 0,
                randomToleranceMinutes = 0,
                delayMinutes = 15,
                timeBuckets = listOf("00:00-23:59"),
                phaseName = "test",
                name = "Test",
                priority = NotificationTriggerPriority.Default,
                notificationText = "Test",
                timeoutNotificationTriggerId = 2,
                source = NotificationTriggerSource.Scheduled,
                modality = NotificationTriggerModality.Push
            )
        )
        val timeoutTrigger = EMAFloatingWidgetNotificationTrigger(
            id = 2,
            questionnaireId = 1,
            validDuration = System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000,
            enabled = true,
            configuration = EMAFloatingWidgetTriggerConfiguration(
                distanceMinutes = 0,
                randomToleranceMinutes = 0,
                delayMinutes = 15,
                timeBuckets = listOf(),
                phaseName = "test",
                name = "Timeout",
                priority = NotificationTriggerPriority.Default,
                notificationText = "Timeout",
                timeoutNotificationTriggerId = null,
                source = NotificationTriggerSource.Scheduled,
                modality = NotificationTriggerModality.EventContingent
            )
        )

        val scheduler = FloatingWidgetNotificationScheduler()
        val notifications = scheduler.scheduleAllNotificationsWithTimeout(
            listOf(trigger, timeoutTrigger),
            Calendar.getInstance(),
            Calendar.getInstance(),
            "test"
        )
        println(FloatingWidgetNotificationScheduler.schedulePrint(notifications))
        assert(notifications.size == 2) { "Expected 2 notifications 2x(original + timeout), but got ${notifications.size}" }
        val original = notifications.find { it.name == "Test" }
        val timeout = notifications.find { it.name == "Timeout" }
        assert(original != null) { "Original notification not found" }
        assert(timeout != null) { "Timeout notification not found" }
        if (original != null && timeout != null) {
            val diff = (timeout.validFrom - original.validFrom) / (60 * 1000)
            assert(diff == trigger.configuration.delayMinutes.toLong()) { "Timeout notification is scheduled $diff minutes after original, but should be ${trigger.configuration.delayMinutes} minutes" }
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

        return listOf(
            NotificationTrigger(
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
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
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
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
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
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            )
        )
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
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
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
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
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
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
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
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            )
        )
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

    @Test
    fun getsNotificationTriggerInCurrentBucketNoneToAnswer() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val triggers =
            normalTriggerRun(calendar).map { if (it.name == "Current Bucket Trigger") it.copy(status = NotificationTriggerStatus.Answered) else it }
        val latest = FloatingWidgetNotificationScheduler.selectLastValidTrigger(triggers, calendar)

        assert(latest == null) { "Expected to find null but got a trigger" }
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

    /**
     * When there are two triggers in the same time bucket:
     * 1. An unanswered non-wave breaking trigger
     * 2. An answered non-wave breaking trigger that is valid 5 minutes after the first one
     *
     * The wave should be considered "completed" because a later trigger in the same bucket
     * has been answered. Therefore, selectLastValidTrigger should return null.
     */
    @Test
    fun returnsNullWhenLaterTriggerInSameBucketIsAnswered() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val firstTriggerTime = calendar.clone() as Calendar
        firstTriggerTime.set(Calendar.HOUR_OF_DAY, 11)
        firstTriggerTime.set(Calendar.MINUTE, 30)
        firstTriggerTime.set(Calendar.SECOND, 0)
        firstTriggerTime.set(Calendar.MILLISECOND, 0)

        val secondTriggerTime = calendar.clone() as Calendar
        secondTriggerTime.set(Calendar.HOUR_OF_DAY, 11)
        secondTriggerTime.set(Calendar.MINUTE, 35)
        secondTriggerTime.set(Calendar.SECOND, 0)
        secondTriggerTime.set(Calendar.MILLISECOND, 0)

        val triggers = listOf(
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "First Trigger (Unanswered)",
                status = NotificationTriggerStatus.Displayed,
                validFrom = firstTriggerTime.timeInMillis,
                priority = NotificationTriggerPriority.Default,
                timeBucket = "11:30-13:59",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "Second Trigger (Answered)",
                status = NotificationTriggerStatus.Answered,
                validFrom = secondTriggerTime.timeInMillis,
                priority = NotificationTriggerPriority.Default,
                timeBucket = "11:30-13:59",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            )
        )

        val latest = FloatingWidgetNotificationScheduler.selectLastValidTrigger(triggers, calendar)

        assert(latest == null) {
            "Expected null because wave is completed (later trigger in same bucket was answered), but got '${latest?.name}'"
        }
    }

    /**
     * Scenario:
     * Time Bucket 1 (9:00-11:29) contains:
     * - N1: Unanswered pushed normal trigger
     * - N3: Answered event contingent normal trigger
     * - N4a: Answered wave-breaking trigger (timeBucket=Bucket 1, but validFrom falls in Bucket 2)
     * - N4b: Answered wave-breaking trigger
     *
     * Time Bucket 2 (11:30-13:59) contains:
     * - N2: Unanswered normal trigger
     * - N4a also appears here by validFrom time
     *
     * Check time: After the unanswered normal trigger from bucket 2.
     * Expected: lastValidTrigger should be null because the wave from bucket 1 overrode
     * the wave from bucket 2, even though it was completed before any items from bucket 2.
     */
    @Test
    fun returnsNullWhenWaveBreakingFromBucket1OverridesBucket2Wave() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 12)
        calendar.set(Calendar.MINUTE, 30)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Time Bucket 1: 9:00-11:29
        // N1: Unanswered pushed normal trigger in Bucket 1
        val n1Time = calendar.clone() as Calendar
        n1Time.set(Calendar.HOUR_OF_DAY, 10)
        n1Time.set(Calendar.MINUTE, 0)

        // N3: Answered event contingent normal trigger in Bucket 1
        val n3Time = calendar.clone() as Calendar
        n3Time.set(Calendar.HOUR_OF_DAY, 10)
        n3Time.set(Calendar.MINUTE, 30)

        // N4a: Answered wave-breaking trigger with timeBucket=Bucket 1, but validFrom in Bucket 2
        val n4aTime = calendar.clone() as Calendar
        n4aTime.set(Calendar.HOUR_OF_DAY, 11)
        n4aTime.set(Calendar.MINUTE, 45)

        // N4b: Answered wave-breaking trigger in Bucket 1
        val n4bTime = calendar.clone() as Calendar
        n4bTime.set(Calendar.HOUR_OF_DAY, 11)
        n4bTime.set(Calendar.MINUTE, 0)

        // Time Bucket 2: 11:30-13:59
        // N2: Unanswered normal trigger in Bucket 2
        val n2Time = calendar.clone() as Calendar
        n2Time.set(Calendar.HOUR_OF_DAY, 12)
        n2Time.set(Calendar.MINUTE, 0)

        val triggers = listOf(
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N1 - Unanswered Normal Bucket1",
                status = NotificationTriggerStatus.Displayed,
                validFrom = n1Time.timeInMillis,
                priority = NotificationTriggerPriority.Default,
                timeBucket = "9:00-11:29",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N3 - Answered EventContingent Bucket1",
                status = NotificationTriggerStatus.Answered,
                validFrom = n3Time.timeInMillis,
                priority = NotificationTriggerPriority.Default,
                timeBucket = "9:00-11:29",
                modality = NotificationTriggerModality.EventContingent,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N4a - Answered WaveBreaking Bucket1 (validFrom in Bucket2)",
                status = NotificationTriggerStatus.Answered,
                validFrom = n4aTime.timeInMillis,
                priority = NotificationTriggerPriority.WaveBreaking,
                timeBucket = "9:00-11:29",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N4b - Answered WaveBreaking Bucket1",
                status = NotificationTriggerStatus.Answered,
                validFrom = n4bTime.timeInMillis,
                priority = NotificationTriggerPriority.WaveBreaking,
                timeBucket = "9:00-11:29",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N2 - Unanswered Normal Bucket2",
                status = NotificationTriggerStatus.Displayed,
                validFrom = n2Time.timeInMillis,
                priority = NotificationTriggerPriority.Default,
                timeBucket = "11:30-13:59",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            )
        )

        val latest = FloatingWidgetNotificationScheduler.selectLastValidTrigger(triggers, calendar)

        assert(latest == null) {
            "Expected null because wave from bucket 1 (with answered wave-breaking trigger) overrides bucket 2 wave, but got '${latest?.name}'"
        }
    }

    /**
     * Scenario (continuation of previous test):
     * Time Bucket 1 (9:00-11:29) contains:
     * - N1: Unanswered pushed normal trigger
     * - N3: Answered event contingent normal trigger
     * - N4a: Answered wave-breaking trigger (timeBucket=Bucket 1, but validFrom falls in Bucket 2)
     * - N4b: Answered wave-breaking trigger
     *
     * Time Bucket 2 (11:30-13:59) contains:
     * - N2: Unanswered normal trigger
     *
     * Time Bucket 3 (14:00-16:29) contains:
     * - N5: Unanswered normal trigger
     *
     * Check time: After the unanswered normal trigger from bucket 3.
     * Expected: lastValidTrigger should be N5 because the wave from bucket 1 has ended,
     * and bucket 3 starts a new wave with its unanswered normal trigger.
     */
    @Test
    fun returnsUnansweredTriggerFromBucket3AfterBucket1WaveEnds() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 14)
        calendar.set(Calendar.MINUTE, 30)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Time Bucket 1: 9:00-11:29
        // N1: Unanswered pushed normal trigger in Bucket 1
        val n1Time = calendar.clone() as Calendar
        n1Time.set(Calendar.HOUR_OF_DAY, 10)
        n1Time.set(Calendar.MINUTE, 0)

        // N3: Answered event contingent normal trigger in Bucket 1
        val n3Time = calendar.clone() as Calendar
        n3Time.set(Calendar.HOUR_OF_DAY, 10)
        n3Time.set(Calendar.MINUTE, 30)

        // N4a: Answered wave-breaking trigger with timeBucket=Bucket 1, but validFrom in Bucket 2
        val n4aTime = calendar.clone() as Calendar
        n4aTime.set(Calendar.HOUR_OF_DAY, 11)
        n4aTime.set(Calendar.MINUTE, 45)

        // N4b: Answered wave-breaking trigger in Bucket 1
        val n4bTime = calendar.clone() as Calendar
        n4bTime.set(Calendar.HOUR_OF_DAY, 11)
        n4bTime.set(Calendar.MINUTE, 0)

        // Time Bucket 2: 11:30-13:59
        // N2: Unanswered normal trigger in Bucket 2
        val n2Time = calendar.clone() as Calendar
        n2Time.set(Calendar.HOUR_OF_DAY, 12)
        n2Time.set(Calendar.MINUTE, 0)

        // Time Bucket 3: 14:00-16:29
        // N5: Unanswered normal trigger in Bucket 3
        val n5Time = calendar.clone() as Calendar
        n5Time.set(Calendar.HOUR_OF_DAY, 14)
        n5Time.set(Calendar.MINUTE, 15)

        val triggers = listOf(
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N1 - Unanswered Normal Bucket1",
                status = NotificationTriggerStatus.Displayed,
                validFrom = n1Time.timeInMillis,
                priority = NotificationTriggerPriority.Default,
                timeBucket = "9:00-11:29",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N3 - Answered EventContingent Bucket1",
                status = NotificationTriggerStatus.Answered,
                validFrom = n3Time.timeInMillis,
                priority = NotificationTriggerPriority.Default,
                timeBucket = "9:00-11:29",
                modality = NotificationTriggerModality.EventContingent,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N4a - Answered WaveBreaking Bucket1 (validFrom in Bucket2)",
                status = NotificationTriggerStatus.Answered,
                validFrom = n4aTime.timeInMillis,
                priority = NotificationTriggerPriority.WaveBreaking,
                timeBucket = "9:00-11:29",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N4b - Answered WaveBreaking Bucket1",
                status = NotificationTriggerStatus.Answered,
                validFrom = n4bTime.timeInMillis,
                priority = NotificationTriggerPriority.WaveBreaking,
                timeBucket = "9:00-11:29",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N2 - Unanswered Normal Bucket2",
                status = NotificationTriggerStatus.Displayed,
                validFrom = n2Time.timeInMillis,
                priority = NotificationTriggerPriority.Default,
                timeBucket = "11:30-13:59",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N5 - Unanswered Normal Bucket3",
                status = NotificationTriggerStatus.Displayed,
                validFrom = n5Time.timeInMillis,
                priority = NotificationTriggerPriority.Default,
                timeBucket = "14:00-16:29",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            )
        )

        val latest = FloatingWidgetNotificationScheduler.selectLastValidTrigger(triggers, calendar)

        assert(latest != null) { "Expected to find a valid trigger, but got null" }
        if (latest != null) {
            assert(latest.name == "N5 - Unanswered Normal Bucket3") {
                "Expected 'N5 - Unanswered Normal Bucket3' because wave from bucket 1 has ended and bucket 3 starts a new wave, but got '${latest.name}'"
            }
        }
    }

    /**
     * Scenario:
     * Time Bucket 1 (9:00-11:29) contains:
     * - N1: Unanswered pushed normal trigger
     * - N3: Answered event contingent normal trigger
     * - N4a: UNANSWERED wave-breaking trigger (timeBucket=Bucket 1, but validFrom falls in Bucket 2)
     * - N4b: Answered wave-breaking trigger
     *
     * Time Bucket 2 (11:30-13:59) contains:
     * - N2: Unanswered normal trigger
     *
     * Time Bucket 3 (14:00-16:29) contains:
     * - N5: Unanswered normal trigger
     *
     * Check time: After the unanswered normal trigger from bucket 3.
     * Expected: lastValidTrigger should be N4a because it's an unanswered wave-breaking trigger
     * from bucket 1, and wave-breaking triggers persist across buckets until answered.
     */
    @Test
    fun returnsUnansweredWaveBreakingN4FromBucket1EvenInBucket3() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 14)
        calendar.set(Calendar.MINUTE, 30)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Time Bucket 1: 9:00-11:29
        // N1: Unanswered pushed normal trigger in Bucket 1
        val n1Time = calendar.clone() as Calendar
        n1Time.set(Calendar.HOUR_OF_DAY, 10)
        n1Time.set(Calendar.MINUTE, 0)

        // N3: Answered event contingent normal trigger in Bucket 1
        val n3Time = calendar.clone() as Calendar
        n3Time.set(Calendar.HOUR_OF_DAY, 10)
        n3Time.set(Calendar.MINUTE, 30)

        // N4a: UNANSWERED wave-breaking trigger with timeBucket=Bucket 1, but validFrom in Bucket 2
        val n4aTime = calendar.clone() as Calendar
        n4aTime.set(Calendar.HOUR_OF_DAY, 11)
        n4aTime.set(Calendar.MINUTE, 45)

        // N4b: Answered wave-breaking trigger in Bucket 1
        val n4bTime = calendar.clone() as Calendar
        n4bTime.set(Calendar.HOUR_OF_DAY, 11)
        n4bTime.set(Calendar.MINUTE, 0)

        // Time Bucket 2: 11:30-13:59
        // N2: Unanswered normal trigger in Bucket 2
        val n2Time = calendar.clone() as Calendar
        n2Time.set(Calendar.HOUR_OF_DAY, 12)
        n2Time.set(Calendar.MINUTE, 0)

        // Time Bucket 3: 14:00-16:29
        // N5: Unanswered normal trigger in Bucket 3
        val n5Time = calendar.clone() as Calendar
        n5Time.set(Calendar.HOUR_OF_DAY, 14)
        n5Time.set(Calendar.MINUTE, 15)

        val triggers = listOf(
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N1 - Unanswered Normal Bucket1",
                status = NotificationTriggerStatus.Displayed,
                validFrom = n1Time.timeInMillis,
                priority = NotificationTriggerPriority.Default,
                timeBucket = "9:00-11:29",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N3 - Answered EventContingent Bucket1",
                status = NotificationTriggerStatus.Answered,
                validFrom = n3Time.timeInMillis,
                priority = NotificationTriggerPriority.Default,
                timeBucket = "9:00-11:29",
                modality = NotificationTriggerModality.EventContingent,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N4a - Unanswered WaveBreaking Bucket1 (validFrom in Bucket2)",
                status = NotificationTriggerStatus.Displayed,
                validFrom = n4aTime.timeInMillis,
                priority = NotificationTriggerPriority.WaveBreaking,
                timeBucket = "9:00-11:29",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N4b - Answered WaveBreaking Bucket1",
                status = NotificationTriggerStatus.Answered,
                validFrom = n4bTime.timeInMillis,
                priority = NotificationTriggerPriority.WaveBreaking,
                timeBucket = "9:00-11:29",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N2 - Unanswered Normal Bucket2",
                status = NotificationTriggerStatus.Displayed,
                validFrom = n2Time.timeInMillis,
                priority = NotificationTriggerPriority.Default,
                timeBucket = "11:30-13:59",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            ),
            NotificationTrigger(
                uid = java.util.UUID.randomUUID(),
                addedAt = System.currentTimeMillis(),
                name = "N5 - Unanswered Normal Bucket3",
                status = NotificationTriggerStatus.Displayed,
                validFrom = n5Time.timeInMillis,
                priority = NotificationTriggerPriority.Default,
                timeBucket = "14:00-16:29",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 1,
                triggerJson = "{}",
                updatedAt = System.currentTimeMillis()
            )
        )

        val latest = FloatingWidgetNotificationScheduler.selectLastValidTrigger(triggers, calendar)

        assert(latest != null) { "Expected to find a valid trigger, but got null" }
        if (latest != null) {
            assert(latest.name == "N4a - Unanswered WaveBreaking Bucket1 (validFrom in Bucket2)") {
                "Expected 'N4a - Unanswered WaveBreaking Bucket1 (validFrom in Bucket2)' because unanswered wave-breaking triggers persist across buckets, but got '${latest.name}'"
            }
        }
    }
}