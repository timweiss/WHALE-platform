package de.mimuc.senseeverything.workers

import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class WorkerHelpers {
    companion object {
        fun hoursUntil(targetHour: Int): Long {
            val now = LocalDateTime.now()
            val todayTarget = now.toLocalDate().atTime(LocalTime.of(targetHour, 0))
            val targetTime = if (now.isAfter(todayTarget)) {
                // If the target time has already passed today, use tomorrow's target time
                todayTarget.plusDays(1)
            } else {
                todayTarget
            }
            return Duration.between(now, targetTime).toHours()
        }
    }
}