package de.mimuc.senseeverything.service.esm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.api.model.OneTimeQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.PeriodicQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.makeTriggerFromJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.PendingQuestionnaire
import de.mimuc.senseeverything.helpers.goAsync
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class OneTimeNotificationReceiver: BroadcastReceiver() {
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
        val id = intent.getIntExtra("id", 0)
        val triggerJson = intent.getStringExtra("triggerJson")
        val trigger = triggerJson?.let { makeTriggerFromJson(JSONObject(it)) as OneTimeQuestionnaireTrigger }
        val questionnaireName = intent.getStringExtra("questionnaireName")

        // deliver notification to user
        if (id != 0 && trigger != null) {
            val pendingQuestionnaireId = PendingQuestionnaire.createEntry(database, dataStoreManager, id, trigger)
            scheduleNotificationService?.sendReminderNotification(id, pendingQuestionnaireId, title)
        }
    }
}