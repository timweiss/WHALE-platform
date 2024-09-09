package de.mimuc.senseeverything.service

import android.app.Notification
import android.app.NotificationChannel
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Messenger
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import de.mimuc.senseeverything.sensor.implementation.NotificationSensor


class MyNotificationListenerService : NotificationListenerService() {
    private val keyLastNotificationWhen = mutableMapOf<String, Long>()

    private var logServiceMessenger: Messenger? = null
    private var boundToLogService: Boolean = false

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
                Log.d(TAG, "onNotificationPosted: channel not found")
                return
            }

            if (category == "service") {
                //Ignore the notification
                return
            }

            val shouldVibrate = currentChannel.shouldVibrate()
            val importance = currentChannel.importance

            Log.d(TAG, "onNotificationPosted: key:${key} when:${sbn.notification.`when`} vibrate:${shouldVibrate} importance:${importance} category:${category} packageName:${packageName}")
            sendToSensor("${notifWhen},${key},${packageName},${shouldVibrate},${importance},${category}")
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

    private fun sendToSensor(data: String) {
        if (!boundToLogService) {
            Log.d(TAG, "sendToSensor: not bound")
            return
        }

        val msg = android.os.Message.obtain(null, LogService.SEND_SENSOR_LOG_DATA)
        val bundle = Bundle()
        bundle.putString("sensorData", data)
        bundle.putString("sensorName", NotificationSensor::class.java.name)
        msg.data = bundle

        try {
            logServiceMessenger?.send(msg)
        } catch (e: Exception) {
            Log.e(TAG, "sendToSensor: error", e)
        }
    }

    private val mConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            logServiceMessenger = Messenger(service)
            boundToLogService = true
            Log.d(TAG, "connected to logservice")
        }

        override fun onServiceDisconnected(className: ComponentName) {
            logServiceMessenger = null
            boundToLogService = false
        }
    }

    override fun onCreate() {
        Intent(this, LogService::class.java).also { intent ->
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onDestroy() {
        if (boundToLogService) {
            unbindService(mConnection)
            boundToLogService = false
        }
    }

    companion object {
        private const val TAG = "MyNotificationListenerService"
    }
}