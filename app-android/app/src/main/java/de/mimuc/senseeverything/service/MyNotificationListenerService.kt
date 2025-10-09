package de.mimuc.senseeverything.service

import android.app.Notification
import android.app.NotificationChannel
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import de.mimuc.senseeverything.logging.WHALELog

class MyNotificationListenerService : NotificationListenerService() {
    private val keyLastNotificationWhen = mutableMapOf<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.notification?.let {
            val category = it.category
            val packageName = sbn.packageName
            val channelId = sbn.notification.channelId
            val currentChannel = findChannel(channelId)
            val key = sbn.key
            val notifWhen = sbn.notification.`when`

            if ((sbn.notification.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_BUBBLE)) != 0) {
                //Ignore the notification
                return
            }

            val lastNotification = keyLastNotificationWhen[key]
            if (lastNotification != null && notifWhen != 0L && lastNotification >= notifWhen) {
                //Ignore the notification
                return
            }
            keyLastNotificationWhen[key] = notifWhen

            if (currentChannel == null) {
                WHALELog.w(TAG, "onNotificationPosted: channel not found")
                return
            }

            if (category == "service") {
                //Ignore the notification
                return
            }

            val shouldVibrate = currentChannel.shouldVibrate()
            val importance = currentChannel.importance

            WHALELog.i(TAG, "onNotificationPosted: key:${key} when:${sbn.notification.`when`} vibrate:${shouldVibrate} importance:${importance} category:${category} packageName:${packageName}")

            val data = "${notifWhen},${key},${packageName},${shouldVibrate},${importance},${category}"
            broadcastToSensor(data)
        }
    }

    private fun getChannels(): List<NotificationChannel> {
        val ranking = currentRanking
        val channelsList = ArrayList<NotificationChannel>()

        for (notification in activeNotifications) {
            val currentRanking = Ranking()
            ranking.getRanking(notification.key, currentRanking)
            val channel = currentRanking.channel
            if (channel != null) {
                channelsList.add(channel)
            }
        }
        return channelsList
    }

    private fun findChannel(channelId: String): NotificationChannel? {
        val channels = getChannels()
        return channels.find { it.id == channelId }
    }

    private fun broadcastToSensor(data: String) {
        val message = Intent(NOTIFICATION_SENSOR_BROADCAST_ACTION)
        message.putExtra(Intent.EXTRA_TEXT, data)
        sendBroadcast(message)
    }

    companion object {
        private const val TAG = "MyNotificationListenerService"

        @JvmField
        val NOTIFICATION_SENSOR_BROADCAST_ACTION = "NotificationSensorBroadcast"
    }
}