package de.mimuc.senseeverything.service.esm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.mimuc.senseeverything.api.model.RandomEMAQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.makeTriggerFromJson
import org.json.JSONObject
import java.util.Calendar

class RandomNotificationReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val scheduleNotificationService = context?.let { ReminderNotification(it) }
        if (intent == null) {
            return
        }

        val title = intent.getStringExtra("title")
        val id = intent.getIntExtra("id", 0)
        val triggerJson = intent.getStringExtra("triggerJson")
        val trigger = triggerJson?.let { makeTriggerFromJson(JSONObject(it)) as RandomEMAQuestionnaireTrigger }
        val questionnaireName = intent.getStringExtra("questionnaireName")
        val untilTimestamp = intent.getLongExtra("untilTimestamp", 0)

        // deliver notification to user
        if (id != 0) {
            scheduleNotificationService?.sendReminderNotification(id, title)
        }

        // schedule next notification
        if (trigger != null && context != null && questionnaireName != null) {
            EsmHandler().scheduleRandomEMANotificationForTrigger(
                trigger,
                Calendar.getInstance(),
                questionnaireName,
                untilTimestamp,
                context
            )
        } else {
            Log.e("PeriodicNotificationReceiver", "Failed to schedule next notification, missing information c:${context} t:${trigger} n:${questionnaireName}")
        }
    }
}