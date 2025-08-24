package de.mimuc.senseeverything.service.esm

import de.mimuc.senseeverything.api.model.ema.RandomEMAQuestionnaireTrigger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import java.util.Calendar

class EsmHandlerTest {
    @Test
    fun testConsecutiveNotificationDatesStartSameDay() {
        val startCalendar = Calendar.getInstance()
        startCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }

        val handler = EsmHandler()
        val scheduleHour = 19
        val scheduleMinute = 0

        var nextCalendar = handler.calculateNextNotificationTime(startCalendar, scheduleHour, scheduleMinute)

        for (i in 1..7) {
            assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH) + i, nextCalendar.get(Calendar.DAY_OF_MONTH))
            assertEquals(scheduleHour, nextCalendar.get(Calendar.HOUR_OF_DAY))
            assertEquals(scheduleMinute, nextCalendar.get(Calendar.MINUTE))
            nextCalendar = handler.calculateNextNotificationTime(nextCalendar, scheduleHour, scheduleMinute)
        }
    }

    @Test
    fun testScheduleOnSameDayTrue() {
        val startCalendar = Calendar.getInstance()
        startCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }

        val handler = EsmHandler()
        val scheduleHour = 19
        val scheduleMinute = 0

        assertTrue(handler.shouldScheduleOnSameDay(startCalendar, scheduleHour, scheduleMinute))
    }

    @Test
    fun testScheduleOnSameDayFalse() {
        val startCalendar = Calendar.getInstance()
        startCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
        }

        val handler = EsmHandler()
        val scheduleHour = 19
        val scheduleMinute = 0

        assertFalse(handler.shouldScheduleOnSameDay(startCalendar, scheduleHour, scheduleMinute))
    }

    @Test
    fun testScheduleSevenDayStudyStartSameDay() {
        val startCalendar = Calendar.getInstance()
        startCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }

        var remainingStudyDays = 7

        val scheduleHour = 19
        val scheduleMinute = 0

        val handler = EsmHandler()

        var nextSchedule = startCalendar

        var scheduledDays = 0

        if (handler.shouldScheduleOnSameDay(startCalendar, scheduleHour, scheduleMinute)) {
            nextSchedule = handler.calculateNextNotificationTime((startCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) }, scheduleHour, scheduleMinute)
            assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH), nextSchedule.get(Calendar.DAY_OF_MONTH))
            remainingStudyDays -= 1
            scheduledDays += 1
        }

        for (i in 1..remainingStudyDays) {
            nextSchedule = handler.calculateNextNotificationTime(nextSchedule, scheduleHour, scheduleMinute)
            remainingStudyDays -= 1
            scheduledDays += 1

            assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH) + i, nextSchedule.get(Calendar.DAY_OF_MONTH))
            assertEquals(scheduleHour, nextSchedule.get(Calendar.HOUR_OF_DAY))
            assertEquals(scheduleMinute, nextSchedule.get(Calendar.MINUTE))
        }

        assertEquals(7, scheduledDays)
        assertEquals(0, remainingStudyDays)
    }

    @Test
    fun testGetNextNotificationSevenDayStudyStartSameDay() {
        val startCalendar = Calendar.getInstance()
        startCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }

        val remainingStudyDays = 7

        val scheduleHour = 19
        val scheduleMinute = 0

        val handler = EsmHandler()

        var nextSchedule = handler.getNextNotification(startCalendar.clone() as Calendar, scheduleHour, scheduleMinute, remainingStudyDays, remainingStudyDays)
        assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH), nextSchedule.calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(remainingStudyDays -1, nextSchedule.remainingDays)

        for (i in 1 until remainingStudyDays) {
            nextSchedule = handler.getNextNotification(nextSchedule.calendar, scheduleHour, scheduleMinute, remainingStudyDays, nextSchedule.remainingDays)
            assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH) + i, nextSchedule.calendar.get(Calendar.DAY_OF_MONTH))
            assertEquals(remainingStudyDays - i - 1, nextSchedule.remainingDays)
        }

        assertEquals(0, nextSchedule.remainingDays)
    }

    @Test
    fun testGetNextNotificationSevenDayStudyStartNextDay() {
        val startCalendar = Calendar.getInstance()
        startCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
        }

        val remainingStudyDays = 7

        val scheduleHour = 19
        val scheduleMinute = 0

        val handler = EsmHandler()

        var nextSchedule = handler.getNextNotification(startCalendar.clone() as Calendar, scheduleHour, scheduleMinute, remainingStudyDays, remainingStudyDays)
        assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH) + 1, nextSchedule.calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(remainingStudyDays -1, nextSchedule.remainingDays)

        for (i in 1 until remainingStudyDays) {
            nextSchedule = handler.getNextNotification(nextSchedule.calendar, scheduleHour, scheduleMinute, remainingStudyDays, nextSchedule.remainingDays)
            assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH) + i + 1, nextSchedule.calendar.get(Calendar.DAY_OF_MONTH))
            assertEquals(remainingStudyDays - i - 1, nextSchedule.remainingDays)
        }

        assertEquals(0, nextSchedule.remainingDays)
    }

    @Test
    fun testScheduleSevenDayStudyStartNextDay() {
        val startCalendar = Calendar.getInstance()
        startCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
        }

        var remainingStudyDays = 7

        val scheduleHour = 19
        val scheduleMinute = 0

        val handler = EsmHandler()

        var nextSchedule = startCalendar

        var scheduledDays = 0

        if (handler.shouldScheduleOnSameDay(startCalendar, scheduleHour, scheduleMinute)) {
            nextSchedule = handler.calculateNextNotificationTime((startCalendar.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -1) }, scheduleHour, scheduleMinute)
            assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH), nextSchedule.get(Calendar.DAY_OF_MONTH))
            remainingStudyDays -= 1
            scheduledDays += 1
        }

        for (i in 1..remainingStudyDays) {
            nextSchedule = handler.calculateNextNotificationTime(nextSchedule, scheduleHour, scheduleMinute)
            remainingStudyDays -= 1
            scheduledDays += 1

            assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH) + i, nextSchedule.get(Calendar.DAY_OF_MONTH))
            assertEquals(scheduleHour, nextSchedule.get(Calendar.HOUR_OF_DAY))
            assertEquals(scheduleMinute, nextSchedule.get(Calendar.MINUTE))
        }

        assertEquals(7, scheduledDays)
        assertEquals(0, remainingStudyDays)
    }

    // Random Notifications

    @Test
    fun testInsideTimeBucket() {
        val timeBucket = "08:00-23:00"
        val handler = EsmHandler()

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

        assertTrue(handler.isInTimeBucket(time1, timeBucket))
        assertTrue(handler.isInTimeBucket(time2, timeBucket))
    }

    @Test
    fun testOutsideTimeBucket() {
        val timeBucket = "08:00-23:00"
        val handler = EsmHandler()

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

        assertFalse(handler.isInTimeBucket(time1, timeBucket))
        assertFalse(handler.isInTimeBucket(time2, timeBucket))
    }

    @Test
    fun testNextRandomNotificationOnSameDayNoRandomTolerance() {
        val trigger = RandomEMAQuestionnaireTrigger(0,0,  15,"", 60, 0, 0, "08:00-23:00", "Phase 1")

        val startCalendar = Calendar.getInstance()
        startCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }

        val handler = EsmHandler()
        val nextNotification = handler.getCalendarForNextRandomNotification(trigger, startCalendar)

        assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH), nextNotification.get(Calendar.DAY_OF_MONTH))
        assertEquals(11, nextNotification.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, nextNotification.get(Calendar.MINUTE))
    }

    @Test
    fun testNextRandomNotificationOnNextDayNoRandomTolerance() {
        val trigger = RandomEMAQuestionnaireTrigger(0,0, 15, "", 60, 0, 0, "08:00-23:00", "Phase 1")

        val startCalendar = Calendar.getInstance()
        startCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
        }

        val handler = EsmHandler()
        val nextNotification = handler.getCalendarForNextRandomNotification(trigger, startCalendar)

        assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH) + 1, nextNotification.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, nextNotification.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, nextNotification.get(Calendar.MINUTE))
    }

    @RepeatedTest(10)
    fun testNextRandomNotificationOnSameDayWithRandomTolerance() {
        val trigger = RandomEMAQuestionnaireTrigger(0,0, 15, "", 60, 10, 0, "08:00-23:00", "Phase 1")

        val startCalendar = Calendar.getInstance()
        startCalendar.apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }

        val handler = EsmHandler()
        val nextNotification = handler.getCalendarForNextRandomNotification(trigger, startCalendar)

        assertEquals(startCalendar.get(Calendar.DAY_OF_MONTH), nextNotification.get(Calendar.DAY_OF_MONTH))
        val minimumTimeInMilis = startCalendar.timeInMillis + (trigger.distanceMinutes * 60 * 1000 - (trigger.randomToleranceMinutes/2) * 60 * 1000)
        val maximumTimeInMilis = startCalendar.timeInMillis + (trigger.distanceMinutes * 60 * 1000 + (trigger.randomToleranceMinutes/2) * 60 * 1000)
        assertTrue(nextNotification.timeInMillis in minimumTimeInMilis..maximumTimeInMilis)
    }
}

