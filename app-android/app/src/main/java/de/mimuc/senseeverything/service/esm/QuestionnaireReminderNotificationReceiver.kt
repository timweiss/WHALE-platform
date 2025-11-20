package de.mimuc.senseeverything.service.esm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.api.model.ema.DurationQuestionnaireReminder
import de.mimuc.senseeverything.api.model.ema.QuestionnaireReminder
import de.mimuc.senseeverything.api.model.ema.QuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.TimeQuestionnaireReminder
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.db.models.PendingQuestionnaireStatus
import de.mimuc.senseeverything.db.models.ScheduledAlarm
import de.mimuc.senseeverything.helpers.applyTime
import de.mimuc.senseeverything.helpers.goAsync
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.esm.EsmHandler.Companion.INTENT_PENDING_QUESTIONNAIRE_ID
import de.mimuc.senseeverything.service.esm.EsmHandler.Companion.INTENT_QUESTIONNAIRE_NAME
import de.mimuc.senseeverything.service.esm.EsmHandler.Companion.INTENT_REMINDER_JSON
import de.mimuc.senseeverything.service.esm.EsmHandler.Companion.INTENT_TRIGGER_ID
import de.mimuc.senseeverything.study.clearAlarm
import kotlinx.serialization.encodeToString
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

@AndroidEntryPoint
class QuestionnaireReminderNotificationReceiver: BroadcastReceiver() {
    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context?, intent: Intent?) = goAsync {
        val scheduleNotificationService = context?.let { NotificationPushHelper(it) }
        if (intent == null) {
            return@goAsync
        }

        val triggerId = intent.getIntExtra(INTENT_TRIGGER_ID, -1)
        val reminderJson = intent.getStringExtra(INTENT_REMINDER_JSON)
        val reminder = reminderJson?.let { fullQuestionnaireJson.decodeFromString<QuestionnaireReminder>(it) }
        val questionnaireName = intent.getStringExtra(INTENT_QUESTIONNAIRE_NAME)
        val pendingQuestionnaireId = intent.getStringExtra(INTENT_PENDING_QUESTIONNAIRE_ID)?.let { UUID.fromString(it) }

        // deliver notification to user
        if (reminder != null && pendingQuestionnaireId != null) {
            val pendingQuestionnaire = database.pendingQuestionnaireDao().getById(pendingQuestionnaireId)

            if (pendingQuestionnaire != null) {
                if (pendingQuestionnaire.status != PendingQuestionnaireStatus.COMPLETED) {
                    scheduleNotificationService?.sendReminderNotification(
                        triggerId,
                        pendingQuestionnaire.uid,
                        reminder.reminderText,
                        questionnaireName,
                        null
                    )
                } else {
                    WHALELog.i("QuestionnaireReminderNotificationReceiver", "Not sending reminder notification for ${pendingQuestionnaireId}, is already answered")
                }
            } else {
                WHALELog.w("QuestionnaireReminderNotificationReceiver", "Cannot send reminder notification for pending questionnaire ${pendingQuestionnaireId}, is not found in database")
            }
        }
    }
}

fun scheduleReminderNotification(
    context: Context,
    database: AppDatabase,
    pendingQuestionnaire: PendingQuestionnaire,
    reminder: QuestionnaireReminder,
    trigger: QuestionnaireTrigger,
    questionnaireName: String,
    notificationTimestamp: Long
) {
    val intent = Intent(context, QuestionnaireReminderNotificationReceiver::class.java)
    intent.apply {
        putExtra(INTENT_REMINDER_JSON, fullQuestionnaireJson.encodeToString(reminder))
        putExtra(INTENT_TRIGGER_ID, trigger.id)
        putExtra(INTENT_QUESTIONNAIRE_NAME, questionnaireName)
        putExtra(INTENT_PENDING_QUESTIONNAIRE_ID, pendingQuestionnaire.uid.toString())
    }

    val timestamp = getReminderNotificationTime(notificationTimestamp, reminder)

    val scheduledAlarm = ScheduledAlarm.createEntry("QuestionnaireReminderNotificationReceiver", pendingQuestionnaire.uid.toString(), timestamp)
    database.scheduledAlarmDao().insert(scheduledAlarm)

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    val pendingIntent = PendingIntent.getBroadcast(
        context.applicationContext,
        scheduledAlarm.requestCode,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
    )

    WHALELog.i(
        "QuestionnaireReminderNotificationReceiver",
        "Scheduling reminder notification for ${pendingQuestionnaire.uid} at $timestamp"
    )

    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        scheduledAlarm.timestamp,
        pendingIntent
    )
}

fun clearReminderNotification(context: Context, database: AppDatabase, pendingQuestionnaireId: UUID) {
    val scheduledAlarm = database.scheduledAlarmDao().getByIdentifier("QuestionnaireReminderNotificationReceiver", pendingQuestionnaireId.toString())
    if (scheduledAlarm != null) {
        clearAlarm(context, QuestionnaireReminderNotificationReceiver::class.java, scheduledAlarm.requestCode)
    }
}

fun getReminderNotificationTime(notificationTimestamp: Long, reminder: QuestionnaireReminder): Long {
    return when (reminder) {
        is TimeQuestionnaireReminder -> {
            val start = java.util.Calendar.getInstance().apply { timeInMillis = notificationTimestamp }
            applyTime(reminder.time, start).timeInMillis
        }
        is DurationQuestionnaireReminder -> {
            notificationTimestamp + reminder.afterMinutes.minutes.inWholeMilliseconds
        }
    }
}