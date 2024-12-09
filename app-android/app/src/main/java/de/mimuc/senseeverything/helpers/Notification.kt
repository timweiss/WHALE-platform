package de.mimuc.senseeverything.helpers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import de.mimuc.senseeverything.R

fun backgroundWorkForegroundInfo(
    notificationId: Int,
    context: Context,
    notificationManager: NotificationManager
): ForegroundInfo {
    return makeForegroundInfo(
        notificationId,
        context.getString(R.string.background_work_channel_id),
        context.getString(R.string.background_work_channel_name),
        context.getString(R.string.background_work_title),
        context.getString(R.string.background_work_detail),
        context,
        notificationManager
    )
}

fun makeForegroundInfo(
    notificationId: Int,
    channelId: String,
    channelName: String,
    title: String,
    detail: String,
    context: Context,
    notificationManager: NotificationManager
): ForegroundInfo {
    createChannel(channelId, channelName, notificationManager)

    val notification = NotificationCompat.Builder(context, channelId)
        .setContentTitle(title)
        .setTicker(title)
        .setContentText(detail)
        .setSmallIcon(R.drawable.ic_launcher)
        .setOngoing(true)
        .build()

    return ForegroundInfo(notificationId, notification)
}

private fun createChannel(
    id: String,
    channelName: String,
    notificationManager: NotificationManager
): String {
    val chan = NotificationChannel(id, channelName, NotificationManager.IMPORTANCE_NONE)
    chan.lightColor = Color.BLUE
    chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
    checkNotNull(notificationManager)
    notificationManager.createNotificationChannel(chan)

    return id
}