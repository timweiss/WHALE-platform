package de.mimuc.senseeverything.service.esm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.api.model.RandomEMAQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.makeTriggerFromJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.PendingQuestionnaire
import de.mimuc.senseeverything.helpers.goAsync
import org.json.JSONObject
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class RandomNotificationReceiver: BroadcastReceiver() {
    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context?, intent: Intent?) = goAsync {
        val scheduleNotificationService = context?.let { ReminderNotification(it) }
        if (intent == null) {
            return@goAsync
        }

        val title = intent.getStringExtra("title")
        val triggerId = intent.getIntExtra("id", 0)
        val triggerJson = intent.getStringExtra("triggerJson")
        val trigger = triggerJson?.let { makeTriggerFromJson(JSONObject(it)) as RandomEMAQuestionnaireTrigger }
        val questionnaireName = intent.getStringExtra("questionnaireName")
        val questionnaireId = intent.getIntExtra("questionnaireId", 0)
        val untilTimestamp = intent.getLongExtra("untilTimestamp", 0)

        // deliver notification to user
        if (triggerId != 0 && trigger != null) {
            val pendingQuestionnaireId = PendingQuestionnaire.createEntry(database, dataStoreManager, questionnaireId, trigger)
            scheduleNotificationService?.sendReminderNotification(triggerId, pendingQuestionnaireId, title)
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