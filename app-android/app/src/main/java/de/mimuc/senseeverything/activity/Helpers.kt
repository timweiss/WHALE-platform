package de.mimuc.senseeverything.activity

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.time.Instant
import java.time.ZoneId


fun Context.getActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

fun dateFromTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
    val date = java.util.Date(timestamp)
    return sdf.format(date)
}

fun calculateCurrentStudyDay(unixStarted: Long): Int {
    val date = Instant.ofEpochMilli(unixStarted).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
    val days = today.toEpochDay() - date.toEpochDay() + 1
    return days.toInt()
}