package de.mimuc.senseeverything.service.esm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.api.model.ema.EMAFloatingWidgetNotificationTrigger
import de.mimuc.senseeverything.api.model.ema.QuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.NotificationTriggerModality
import de.mimuc.senseeverything.db.models.NotificationTriggerStatus
import de.mimuc.senseeverything.helpers.goAsync
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit
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
        val notificationTrigger = FloatingWidgetNotificationScheduler.getLatestValidTriggerForTime(calendar, database)

        // deliver notification to user
        if (notificationTrigger != null && shouldSendPush(notificationTrigger)) {
            notificationPushHelper?.pushNotificationTrigger(notificationTrigger, getTimeout(notificationTrigger))
            setPushed(notificationTrigger)
        }
    }

    /**
     * Workaround to find a timeout, as the trigger responsible does not provide a duration, but the linked timeout trigger does.
     * Practically, this allows us to dismiss the notification once a new NotificationTrigger is valid.
     */
    private suspend fun getTimeout(notificationTrigger: NotificationTrigger): Long? {
        val trigger =
            fullQuestionnaireJson.decodeFromString<QuestionnaireTrigger>(notificationTrigger.triggerJson)

        if (trigger is EMAFloatingWidgetNotificationTrigger) {
            if (trigger.configuration.timeoutNotificationTriggerId != null) {
                val trigger = dataStoreManager.questionnairesFlow.first().flatMap { it.triggers }
                    .find { it.id == trigger.configuration.timeoutNotificationTriggerId }

                if (trigger != null && trigger is EMAFloatingWidgetNotificationTrigger) {
                    return TimeUnit.MINUTES.toMillis(trigger.configuration.delayMinutes.toLong())
                }
            }
        }

        return null
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