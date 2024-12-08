package de.mimuc.senseeverything.helpers

import java.util.Calendar
import kotlin.random.Random

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
    "22:00-00:00"
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

fun shouldDisplayFromRandomDiceThrow(): Boolean {
    val random = Random.nextInt(1, 3)
    return random == 1
}