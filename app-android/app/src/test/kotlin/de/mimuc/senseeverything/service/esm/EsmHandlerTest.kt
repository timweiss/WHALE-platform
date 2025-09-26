package de.mimuc.senseeverything.service.esm

import de.mimuc.senseeverything.api.model.ema.RandomEMAQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.RandomEMATriggerConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.Calendar

class EsmHandlerTest {
    // Random Notifications
    @Test
    fun testInsideTimeBucket() {
        val timeBucket = "08:00-23:00"

        val time1 = Calendar.getInstance()
        time1.apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }

        val time2 = Calendar.getInstance()
        time2.apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
        }

        assertTrue(EsmHandler.isInTimeBucket(time1, timeBucket))
        assertTrue(EsmHandler.isInTimeBucket(time2, timeBucket))
    }

    @Test
    fun testOutsideTimeBucket() {
        val timeBucket = "08:00-23:00"

        val time1 = Calendar.getInstance()
        time1.apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 59)
        }

        val time2 = Calendar.getInstance()
        time2.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 1)
        }

        assertFalse(EsmHandler.isInTimeBucket(time1, timeBucket))
        assertFalse(EsmHandler.isInTimeBucket(time2, timeBucket))
    }

    @Test
    fun testNextRandomNotificationOnSameDayNoRandomTolerance() {
        val trigger = RandomEMAQuestionnaireTrigger(0,0,  15, true, RandomEMATriggerConfiguration(60, 0, 0, "08:00-23:00", "Phase 1"))

        val startCalendar = Calendar.getInstance()
        startCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }

        val nextNotification = EsmHandler.getCalendarForNextRandomNotification(trigger, startCalendar)

        assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH), nextNotification.get(Calendar.DAY_OF_MONTH))
        assertEquals(11, nextNotification.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, nextNotification.get(Calendar.MINUTE))
    }

    @Test
    fun testNextRandomNotificationOnNextDayNoRandomTolerance() {
        val trigger = RandomEMAQuestionnaireTrigger(0,0, 15, true, RandomEMATriggerConfiguration(60, 0, 0, "08:00-23:00", "Phase 1"))

        val startCalendar = Calendar.getInstance()
        startCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
        }

        val nextNotification = EsmHandler.getCalendarForNextRandomNotification(trigger, startCalendar)

        assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH) + 1, nextNotification.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, nextNotification.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, nextNotification.get(Calendar.MINUTE))
    }

    @RepeatedTest(10)
    fun testNextRandomNotificationOnSameDayWithRandomTolerance() {
        val trigger = RandomEMAQuestionnaireTrigger(0,0, 15, true, RandomEMATriggerConfiguration(60, 10, 0, "08:00-23:00", "Phase 1"))

        val startCalendar = Calendar.getInstance()
        startCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }

        val nextNotification = EsmHandler.getCalendarForNextRandomNotification(trigger, startCalendar)

        assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH), nextNotification.get(Calendar.DAY_OF_MONTH))
        val minimumTimeInMilis = startCalendar.timeInMillis + (trigger.configuration.distanceMinutes * 60 * 1000 - (trigger.configuration.randomToleranceMinutes/2) * 60 * 1000)
        val maximumTimeInMilis = startCalendar.timeInMillis + (trigger.configuration.distanceMinutes * 60 * 1000 + (trigger.configuration.randomToleranceMinutes/2) * 60 * 1000)
        assertTrue(nextNotification.timeInMillis in minimumTimeInMilis..maximumTimeInMilis)
    }

    // Stateless Periodic Notification Tests

    @Test
    fun testCalculateNextPeriodicNotificationTimeToday() {
        val studyEndTimestamp = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 2) // Study ends in 2 days
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.timeInMillis

        // Current time is 10 AM, schedule at 7 PM today
        val currentTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }

        val result = EsmHandler.calculateNextPeriodicNotificationTime(
            currentTime,
            studyEndTimestamp,
            19, // 7 PM
            0   // 0 minutes
        )

        assertNotNull(result)

        val resultCalendar = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(19, resultCalendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCalendar.get(Calendar.MINUTE))

        // Should be today since current time is 10 AM and schedule is 7 PM
        assertEquals(currentTime.get(Calendar.DAY_OF_MONTH), resultCalendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testCalculateNextPeriodicNotificationTimeTomorrow() {
        val studyEndTimestamp = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 2) // Study ends in 2 days
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.timeInMillis

        // Current time is 8 PM, schedule at 7 PM (should be tomorrow)
        val currentTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
        }

        val result = EsmHandler.calculateNextPeriodicNotificationTime(
            currentTime,
            studyEndTimestamp,
            19, // 7 PM
            0   // 0 minutes
        )

        assertNotNull(result)

        val resultCalendar = Calendar.getInstance().apply { timeInMillis = result!! }
        assertEquals(19, resultCalendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, resultCalendar.get(Calendar.MINUTE))

        // Should be tomorrow since current time is 8 PM and schedule is 7 PM
        assertEquals(currentTime.get(Calendar.DAY_OF_MONTH) + 1, resultCalendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testCalculateNextPeriodicNotificationTimeAfterStudyEnd() {
        val studyEndTimestamp = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, -1) // Study ended yesterday
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.timeInMillis

        val result = EsmHandler.calculateNextPeriodicNotificationTime(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 0)
            },
            studyEndTimestamp,
            19, // 7 PM
            0   // 0 minutes
        )

        // Should return null since study has ended
        assertEquals(null, result)
    }

    @Test
    fun testCalculateNextPeriodicNotificationTimeExactlyAtEndTime() {
        val studyEndTimestamp = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1) // Study ends in 1 hour
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // Schedule notification for after study ends
        val result = EsmHandler.calculateNextPeriodicNotificationTime(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 0)
            },
            studyEndTimestamp,
            23, // 11 PM (after study ends)
            0   // 0 minutes
        )

        // Should return null since next notification would be after study end
        assertEquals(null, result)
    }
}

