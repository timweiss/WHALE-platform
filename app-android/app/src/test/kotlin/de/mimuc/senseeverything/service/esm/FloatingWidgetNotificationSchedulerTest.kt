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
        assert(days.size == 14) { "Expected 14 days of notifications, but got ${days.size}" }
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
            Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) },
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

    /**
     * Test for bug where EventContingent notification (N3) is incorrectly displayed
     * after WaveBreaking trigger (N4a) from previous bucket extends into current bucket.
     *
     * Real-world scenario from participant data:
     * Time Bucket 2 (11:30-13:59) contains:
     * - N1: Planned Push notification (validFrom: 11:41:21)
     * - N3: Planned EventContingent notification (validFrom: 11:46:21)
     * - N4a: Answered WaveBreaking EventContingent (validFrom: 13:58:32, answered at 14:35:23)
     *
     * Time Bucket 3 (14:00-16:29) contains:
     * - N1: Planned Push notification (validFrom: 14:02:41)
     * - N3: Planned EventContingent notification (validFrom: 14:07:41)
     *
     * Check time: 15:44:25 (when N3 was incorrectly displayed in Bucket 3)
     *
     * Expected: null - N3 should NOT be retrieved because N4a from Bucket 2 extended into Bucket 3
     * (N4a's validFrom 13:58:32 < Bucket 3 start 14:00, but answered at 14:35:23 which is in Bucket 3)
     *
     * Bug: Currently returns N3 (EventContingent) instead of null, even though the WaveBreaking
     * wave from Bucket 2 completed in Bucket 3, which should suppress all Bucket 2 notifications
     * including EventContingent ones.
     *
     * Note: In the actual data, N3 was displayed at 15:44:25 and answered at 15:44:39, but we test
     * at the display time before it was answered to verify the retrieval logic.
     */
    @Test
    fun doesNotReturnEventContingentWhenWaveBreakingFromPreviousBucketCompleted() {
        // Check time: 15:44:25 (November 15, 2025)
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, 2025)
        calendar.set(Calendar.MONTH, Calendar.NOVEMBER)
        calendar.set(Calendar.DAY_OF_MONTH, 15)
        calendar.set(Calendar.HOUR_OF_DAY, 15)
        calendar.set(Calendar.MINUTE, 44)
        calendar.set(Calendar.SECOND, 25)
        calendar.set(Calendar.MILLISECOND, 310)

        val triggers = listOf(
            // Bucket 2 triggers (11:30-13:59)
            NotificationTrigger(
                uid = java.util.UUID.fromString("1d8e63e3-4bb5-4cdd-8164-35a8bc31b611"),
                addedAt = 1763161200106L,
                name = "N1",
                status = NotificationTriggerStatus.Planned,
                validFrom = 1763203281935L, // 11:41:21
                priority = NotificationTriggerPriority.Default,
                timeBucket = "11:30-13:59",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 69,
                triggerJson = "{}",
                updatedAt = 1763161200107L
            ),
            NotificationTrigger(
                uid = java.util.UUID.fromString("f31f9f1b-41a9-4bda-b047-108478f3282f"),
                addedAt = 1763161200106L,
                name = "N3",
                status = NotificationTriggerStatus.Planned,
                validFrom = 1763203581935L, // 11:46:21
                priority = NotificationTriggerPriority.Default,
                timeBucket = "11:30-13:59",
                modality = NotificationTriggerModality.EventContingent,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 71,
                triggerJson = "{}",
                updatedAt = 1763161200114L
            ),
            NotificationTrigger(
                uid = java.util.UUID.fromString("c9ac296e-17f7-499c-8c0c-8c709b7e90a1"),
                addedAt = 1763211512445L,
                name = "N4a",
                status = NotificationTriggerStatus.Answered,
                validFrom = 1763211512445L, // 13:58:32
                priority = NotificationTriggerPriority.WaveBreaking,
                timeBucket = "11:30-13:59",
                modality = NotificationTriggerModality.EventContingent,
                source = NotificationTriggerSource.RuleBased,
                questionnaireId = 72,
                triggerJson = "{}",
                updatedAt = 1763213723664L, // answered at 14:35:23
                displayedAt = 1763213720766L,
                answeredAt = 1763213723664L
            ),
            // Bucket 3 triggers (14:00-16:29)
            NotificationTrigger(
                uid = java.util.UUID.fromString("c25c3264-c230-47da-a462-4b1fad7c221c"),
                addedAt = 1763161200107L,
                name = "N1",
                status = NotificationTriggerStatus.Planned,
                validFrom = 1763211761483L, // 14:02:41
                priority = NotificationTriggerPriority.Default,
                timeBucket = "14:00-16:29",
                modality = NotificationTriggerModality.Push,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 69,
                triggerJson = "{}",
                updatedAt = 1763161200107L
            ),
            NotificationTrigger(
                uid = java.util.UUID.fromString("87a6c4c0-fc23-4288-adaf-85c7791da67c"),
                addedAt = 1763161200107L,
                name = "N3",
                status = NotificationTriggerStatus.Planned,
                validFrom = 1763212061483L, // 14:07:41
                priority = NotificationTriggerPriority.Default,
                timeBucket = "14:00-16:29",
                modality = NotificationTriggerModality.EventContingent,
                source = NotificationTriggerSource.Scheduled,
                questionnaireId = 71,
                triggerJson = "{}",
                updatedAt = 1763161200107L,
            )
        )

        val latest = FloatingWidgetNotificationScheduler.selectLastValidTrigger(triggers, calendar)

        assert(latest == null) {
            "Expected null because N4a (WaveBreaking) from Bucket 2 extended into and completed in Bucket 3, " +
            "which should suppress all Bucket 2 notifications including EventContingent N3, but got '${latest?.name}'"
        }
    }
    /**
     * Test for multi-phase notification scheduling to verify that:
     * 1. Phase 1 (pseudo_randomized) notifications are scheduled for exactly 4 days (Dec 3-6)
     * 2. Phase 2 (event_contingent) notifications are scheduled for exactly 4 days (Dec 7-10)
     * 3. On Dec 7 (first day of Phase 2), there are NO Phase 1 notifications
     *
     * This test simulates a real study schedule with multiple phases that have different
     * notification patterns, ensuring that phases don't overlap.
     */
    @Test
    fun schedulesNotificationsForMultiplePhasesWithoutOverlap() {
        // Phase 1: Dec 3-6, 2025 (4 days)
        val phase1Start = Calendar.getInstance().apply {
            set(2025, Calendar.DECEMBER, 3, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val phase1End = Calendar.getInstance().apply {
            set(2025, Calendar.DECEMBER, 7, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Phase 2: Dec 7-10, 2025 (4 days)
        val phase2Start = Calendar.getInstance().apply {
            set(2025, Calendar.DECEMBER, 7, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val phase2End = Calendar.getInstance().apply {
            set(2025, Calendar.DECEMBER, 11, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Create Phase 1 trigger
        val phase1Trigger = EMAFloatingWidgetNotificationTrigger(
            id = 1,
            questionnaireId = 1,
            validDuration = System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000L,
            enabled = true,
            configuration = EMAFloatingWidgetTriggerConfiguration(
                name = "Phase1-N1",
                phaseName = "pseudo_randomized",
                timeBuckets = listOf("9:00-11:29", "11:30-13:59", "14:00-16:29"),
                distanceMinutes = 0,
                delayMinutes = 0,
                randomToleranceMinutes = 0,
                modality = NotificationTriggerModality.Push,
                priority = NotificationTriggerPriority.Default,
                source = NotificationTriggerSource.Scheduled,
                notificationText = "Phase 1 Notification",
                timeoutNotificationTriggerId = null
            )
        )

        // Create Phase 2 trigger
        val phase2Trigger = EMAFloatingWidgetNotificationTrigger(
            id = 2,
            questionnaireId = 2,
            validDuration = System.currentTimeMillis() + 30 * 24 * 60 * 60 * 1000L,
            enabled = true,
            configuration = EMAFloatingWidgetTriggerConfiguration(
                name = "Phase2-N1",
                phaseName = "event_contingent",
                timeBuckets = listOf("9:00-11:29", "11:30-13:59", "14:00-16:29"),
                distanceMinutes = 0,
                delayMinutes = 0,
                randomToleranceMinutes = 0,
                modality = NotificationTriggerModality.Push,
                priority = NotificationTriggerPriority.Default,
                source = NotificationTriggerSource.Scheduled,
                notificationText = "Phase 2 Notification",
                timeoutNotificationTriggerId = null
            )
        )

        val scheduler = FloatingWidgetNotificationScheduler()

        // Schedule notifications for both phases
        val phase1Notifications = scheduler.planNotificationsForTrigger(phase1Trigger, phase1Start, phase1End)
        val phase2Notifications = scheduler.planNotificationsForTrigger(phase2Trigger, phase2Start, phase2End)

        // Combine all notifications
        val allNotifications = phase1Notifications + phase2Notifications

        println("=== Phase 1 Notifications ===")
        println(FloatingWidgetNotificationScheduler.schedulePrint(phase1Notifications))
        println("\n=== Phase 2 Notifications ===")
        println(FloatingWidgetNotificationScheduler.schedulePrint(phase2Notifications))

        // Group notifications by date
        val notificationsByDate = allNotifications.groupBy { notif ->
            val cal = Calendar.getInstance().apply { timeInMillis = notif.validFrom }
            String.format(
                "%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        // Get Phase 1 days (Dec 3-6)
        val phase1Days = notificationsByDate.filterKeys { date ->
            date in listOf("2025-12-03", "2025-12-04", "2025-12-05", "2025-12-06")
        }

        // Get Phase 2 days (Dec 7-10)
        val phase2Days = notificationsByDate.filterKeys { date ->
            date in listOf("2025-12-07", "2025-12-08", "2025-12-09", "2025-12-10")
        }

        // Assertion 1: Phase 1 should have exactly 4 days of notifications
        assert(phase1Days.size == 4) {
            "Expected Phase 1 to have 4 days of notifications, but got ${phase1Days.size}"
        }

        // Assertion 2: Phase 2 should have exactly 4 days of notifications
        assert(phase2Days.size == 4) {
            "Expected Phase 2 to have 4 days of notifications, but got ${phase2Days.size}"
        }

        // Assertion 3: Each day in Phase 1 should have 3 notifications (one per time bucket)
        for ((date, notifs) in phase1Days) {
            assert(notifs.size == 3) {
                "Expected 3 notifications on $date (Phase 1), but got ${notifs.size}"
            }
            // All notifications on Phase 1 days should have Phase 1 name
            assert(notifs.all { it.name == "Phase1-N1" }) {
                "Expected all notifications on $date to be 'Phase1-N1', but found: ${notifs.map { it.name }}"
            }
        }


        // Assertion 5: Critical test - Dec 7 (first day of Phase 2) should have NO Phase 1 notifications
        val dec7Notifications = notificationsByDate["2025-12-07"] ?: emptyList()
        val phase1OnDec7 = dec7Notifications.filter { it.name == "Phase1-N1" }
        assert(phase1OnDec7.isEmpty()) {
            "Expected NO Phase 1 notifications on Dec 7, but found ${phase1OnDec7.size}: ${phase1OnDec7.map { it.name }}"
        }

        // Assertion 4: Each day in Phase 2 should have 3 notifications (one per time bucket)
        for ((date, notifs) in phase2Days) {
            assert(notifs.size == 3) {
                "Expected 3 notifications on $date (Phase 2), but got ${notifs.size}"
            }
            // All notifications on Phase 2 days should have Phase 2 name
            assert(notifs.all { it.name == "Phase2-N1" }) {
                "Expected all notifications on $date to be 'Phase2-N1', but found: ${notifs.map { it.name }}"
            }
        }

        // Assertion 6: Dec 7 should have Phase 2 notifications
        val phase2OnDec7 = dec7Notifications.filter { it.name == "Phase2-N1" }
        assert(phase2OnDec7.isNotEmpty()) {
            "Expected Phase 2 notifications on Dec 7, but found none"
        }

        println("\n=== Test Results ===")
        println("Phase 1 days: ${phase1Days.keys.sorted()}")
        println("Phase 2 days: ${phase2Days.keys.sorted()}")
        println("Dec 7 notifications: ${dec7Notifications.map { "${it.name} at ${Calendar.getInstance().apply { timeInMillis = it.validFrom }.get(Calendar.HOUR_OF_DAY)}:${Calendar.getInstance().apply { timeInMillis = it.validFrom }.get(Calendar.MINUTE)}" }}")
    }
}