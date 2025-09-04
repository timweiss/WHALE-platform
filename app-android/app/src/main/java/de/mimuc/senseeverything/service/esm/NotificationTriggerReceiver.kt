package de.mimuc.senseeverything.service.esm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.NotificationTriggerModality
import de.mimuc.senseeverything.db.models.NotificationTriggerStatus
import de.mimuc.senseeverything.helpers.goAsync
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class NotificationTriggerReceiver: BroadcastReceiver() {
    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context?, intent: Intent?) = goAsync {
        val notificationPushHelper = context?.let { NotificationPushHelper(it) }
        if (intent == null) {
            return@goAsync
        }

        val calendar = Calendar.getInstance()

        // check latest valid trigger
        val trigger = FloatingWidgetNotificationScheduler.getLatestValidTriggerForTime(calendar, database)

        // deliver notification to user
        if (trigger != null && shouldSendPush(trigger)) {
            notificationPushHelper?.pushNotificationTrigger(trigger)
            setPushed(trigger)
        }
    }

    private fun setPushed(trigger: NotificationTrigger) {
        trigger.status = NotificationTriggerStatus.Pushed
        trigger.pushedAt = System.currentTimeMillis()
        trigger.updatedAt = System.currentTimeMillis()
        database.notificationTriggerDao().update(trigger)
    }

    private fun shouldSendPush(trigger: NotificationTrigger): Boolean {
        return trigger.modality == NotificationTriggerModality.Push && trigger.status == NotificationTriggerStatus.Planned
    }
}