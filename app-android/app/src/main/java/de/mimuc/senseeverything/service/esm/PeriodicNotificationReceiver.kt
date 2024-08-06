package de.mimuc.senseeverything.service.esm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PeriodicNotificationReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val scheduleNotificationService = context?.let { ReminderNotification(it) }
        val title: String? = intent?.getStringExtra("title")
        val id = intent?.getIntExtra("id", 0)
        if (id != null) {
            scheduleNotificationService?.sendReminderNotification(id, title)
        }
    }
}