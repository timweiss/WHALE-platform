package de.mimuc.senseeverything.helpers

import java.util.Calendar

val TIME_BUCKETS = listOf(
    "00:00-02:00",
    "02:00-04:00",
    "04:00-06:00",
    "06:00-08:00",
    "08:00-10:00",
    "10:00-12:00",
    "12:00-14:00",
    "14:00-16:00",
    "16:00-18:00",
    "18:00-20:00",
    "20:00-22:00",
    "22:00-23:59"
)

fun getCurrentTimeBucket(): String {
    val now = Calendar.getInstance()
    val hour = now.get(Calendar.HOUR_OF_DAY)

    // find the bucket that contains the current hour
    return TIME_BUCKETS.find { bucket ->
        val start = bucket.split("-")[0].split(":")[0].toInt()
        val end = bucket.split("-")[1].split(":")[0].toInt()
        hour in start until end
    } ?: "Unknown"
}


fun parseTimebucket(bucket: String, startDay: Calendar): Pair<Calendar, Calendar> {
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