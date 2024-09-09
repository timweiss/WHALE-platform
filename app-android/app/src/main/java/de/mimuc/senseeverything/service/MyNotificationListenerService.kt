package de.mimuc.senseeverything.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log


class MyNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.notification?.let {
            val category = it.category
            val packageName = sbn.packageName
            val channelId = sbn.notification.channelId
            val currentChannel = findChannel(channelId)

            if (currentChannel == null) {
                Log.d(TAG, "onNotificationPosted: channel not found")
                return
            }

            val shouldVibrate = currentChannel.shouldVibrate()
            val importance = currentChannel.importance

            Log.d(TAG, "onNotificationPosted: vibrate:${shouldVibrate} importance:${importance} category:${category} packageName:${packageName} channelId:${channelId}")
        }
    }

    private fun getChannels(): List<NotificationChannel> {
        val ranking = currentRanking
        val channelsList = ArrayList<NotificationChannel>()

        for (notification in activeNotifications) {
            val currentRanking = Ranking()
            ranking.getRanking(notification.key, currentRanking)
            channelsList.add(currentRanking.channel)
        }
        return channelsList
    }

    private fun findChannel(channelId: String): NotificationChannel? {
        val channels = getChannels()
        return channels.find { it.id == channelId }
    }

    companion object {
        private const val TAG = "MyNotificationListenerService"
    }
}