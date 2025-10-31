package de.mimuc.senseeverything.service.esm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.api.model.ema.Action
import de.mimuc.senseeverything.api.model.ema.OpenQuestionnaire
import de.mimuc.senseeverything.api.model.ema.PutNotificationTrigger
import de.mimuc.senseeverything.api.model.ema.UpdateNextNotificationTrigger
import de.mimuc.senseeverything.api.model.ema.ruleJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.helpers.goAsync
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.serialization.encodeToString
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class QuestionnaireRuleActionReceiver : BroadcastReceiver() {
    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context?, intent: Intent?) = goAsync {
        if (context == null) {
            WHALELog.e("QuestionnaireRuleActionReceiver", "Context is null")
            return@goAsync
        }

        val action = intent?.getStringExtra("action")?.let { ruleJson.decodeFromString<Action>(it) }
        if (action == null) {
            WHALELog.e("QuestionnaireRuleActionReceiver", "Action is null")
            return@goAsync
        }

        val sourceId = intent.getStringExtra("sourceId")
        val sourceUuid = if (sourceId != null) UUID.fromString(sourceId) else null

        WHALELog.i("QuestionnaireRuleActionReceiver", "Handling action of type ${action.javaClass.name} for sourceId=$sourceId")

        when (action) {
            is OpenQuestionnaire -> {
                EsmHandler.handleEvent(
                    eventName = "open_questionnaire",
                    sourceId = sourceUuid,
                    triggerId = action.eventQuestionnaireTriggerId,
                    context.applicationContext,
                    dataStoreManager,
                    database
                )
            }
            is PutNotificationTrigger -> {
                EsmHandler.handleEvent(
                    eventName = "put_notification_trigger",
                    sourceId = sourceUuid,
                    triggerId = action.triggerId,
                    context.applicationContext,
                    dataStoreManager,
                    database
                )
            }
            is UpdateNextNotificationTrigger -> {
                EsmHandler.handleUpdateNextNotificationTrigger(
                    action,
                    sourceUuid,
                    context.applicationContext,
                    database
                )
            }
        }
    }

    companion object {
        fun sendBroadcast(context: Context, action: Action, source: PendingQuestionnaire? = null) {
            val intent = Intent(context.applicationContext, QuestionnaireRuleActionReceiver::class.java)
            intent.apply {
                putExtra("action", ruleJson.encodeToString<Action>(action))
                if (source != null) {
                    putExtra("sourceId", source.uid.toString())
                }
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context.applicationContext,
                101,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )

            pendingIntent.send()
        }
    }
}