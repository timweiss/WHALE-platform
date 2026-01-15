package de.mimuc.senseeverything.service.esm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.api.model.ema.OneTimeQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.QuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.db.models.PendingQuestionnaireStatus
import de.mimuc.senseeverything.db.models.validDistance
import de.mimuc.senseeverything.helpers.goAsync
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class OneTimeNotificationReceiver: BroadcastReceiver() {
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
        val id = intent.getIntExtra(EsmHandler.INTENT_TRIGGER_ID, 0)
        val triggerJson = intent.getStringExtra(EsmHandler.INTENT_TRIGGER_JSON)
        val trigger = triggerJson?.let { fullQuestionnaireJson.decodeFromString<QuestionnaireTrigger>(it) as OneTimeQuestionnaireTrigger }
        val questionnaireName = intent.getStringExtra(EsmHandler.INTENT_QUESTIONNAIRE_NAME)
        val pendingQuestionnaireId = intent.getStringExtra(EsmHandler.INTENT_PENDING_QUESTIONNAIRE_ID)

        // deliver notification to user
        if (id != 0 && trigger != null) {
            val pendingQuestionnaire = if (pendingQuestionnaireId != null) {
                database.pendingQuestionnaireDao().getById(UUID.fromString(pendingQuestionnaireId))
            } else {
                PendingQuestionnaire.createEntry(database, dataStoreManager, trigger)
            }

            if (pendingQuestionnaire != null && pendingQuestionnaire.status != PendingQuestionnaireStatus.COMPLETED) {
                pendingQuestionnaire.markNotified(database)
                scheduleNotificationService?.sendReminderNotification(
                    id,
                    pendingQuestionnaire.uid,
                    title,
                    questionnaireName,
                    pendingQuestionnaire.validDistance
                )

                if (trigger.configuration.reminder != null) {
                    scheduleReminderNotification(
                        context!!,
                        database,
                        pendingQuestionnaire,
                        trigger.configuration.reminder,
                        trigger,
                        questionnaireName ?: "",
                        System.currentTimeMillis()
                    )
                }
            }
        }
    }
}