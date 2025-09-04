package de.mimuc.senseeverything.service.esm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.api.model.ema.QuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.RandomEMAQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.helpers.goAsync
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class RandomNotificationReceiver: BroadcastReceiver() {
    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context?, intent: Intent?) = goAsync {
        val scheduleNotificationService = context?.let { NotificationPushHelper(it) }
        if (intent == null) {
            return@goAsync
        }

        val title = intent.getStringExtra(EsmHandler.INTENT_TITLE)
        val triggerId = intent.getIntExtra(EsmHandler.INTENT_TRIGGER_ID, 0)
        val triggerJson = intent.getStringExtra(EsmHandler.INTENT_TRIGGER_JSON)
        val trigger = triggerJson?.let { fullQuestionnaireJson.decodeFromString<QuestionnaireTrigger>(it) as RandomEMAQuestionnaireTrigger }
        val questionnaireName = intent.getStringExtra(EsmHandler.INTENT_QUESTIONNAIRE_NAME)
        val untilTimestamp = intent.getLongExtra(EsmHandler.INTENT_NOTIFY_PHASE_UNTIL_TIMESTAMP, 0)

        // deliver notification to user
        if (triggerId != 0 && trigger != null) {
            val pendingQuestionnaireId = PendingQuestionnaire.createEntry(database, dataStoreManager, trigger)
            scheduleNotificationService?.sendReminderNotification(triggerId, pendingQuestionnaireId, title, questionnaireName)
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