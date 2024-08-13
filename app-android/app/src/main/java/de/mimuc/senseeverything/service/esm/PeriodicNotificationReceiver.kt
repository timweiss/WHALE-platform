package de.mimuc.senseeverything.service.esm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.mimuc.senseeverything.api.model.PeriodicQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.makeTriggerFromJson
import org.json.JSONObject

class PeriodicNotificationReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val scheduleNotificationService = context?.let { ReminderNotification(it) }
        if (intent == null) {
            return
        }

        val title = intent.getStringExtra("title")
        val id = intent.getIntExtra("id", 0)
        val triggerJson = intent.getStringExtra("triggerJson")
        val trigger = triggerJson?.let { makeTriggerFromJson(JSONObject(it)) as PeriodicQuestionnaireTrigger }
        val questionnaireName = intent.getStringExtra("questionnaireName")
        val remainingDays = intent.getIntExtra("remainingDays", 0)

        // deliver notification to user
        if (id != 0) {
            scheduleNotificationService?.sendReminderNotification(id, title)
        }

        // schedule next notification
        if (trigger != null && context != null && questionnaireName != null) {
            EsmHandler().scheduleNextPeriodicNotification(context, trigger, remainingDays, questionnaireName)
        } else {
            Log.e("PeriodicNotificationReceiver", "Failed to schedule next notification, missing information c:${context} t:${trigger} n:${questionnaireName}")
        }
    }
}