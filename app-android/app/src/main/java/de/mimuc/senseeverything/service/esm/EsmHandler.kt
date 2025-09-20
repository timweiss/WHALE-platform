package de.mimuc.senseeverything.service.esm

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.esm.QuestionnaireActivity
import de.mimuc.senseeverything.api.model.ExperimentalGroupPhase
import de.mimuc.senseeverything.api.model.ema.EMAFloatingWidgetNotificationTrigger
import de.mimuc.senseeverything.api.model.ema.EventQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.ema.OneTimeQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.PeriodicQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.QuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.RandomEMAQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.NotificationTriggerModality
import de.mimuc.senseeverything.db.models.NotificationTriggerSource
import de.mimuc.senseeverything.db.models.NotificationTriggerStatus
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.invoke
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit

class EsmHandler {
    companion object {
        const val INTENT_TITLE = "title"
        const val INTENT_TRIGGER_ID = "id"
        const val INTENT_TRIGGER_JSON = "triggerJson"
        const val INTENT_QUESTIONNAIRE_NAME = "questionnaireName"
        const val INTENT_NOTIFY_PHASE_UNTIL_TIMESTAMP = "untilTimestamp"
        const val INTENT_REMAINING_STUDY_DAYS = "remainingDays"
        const val INTENT_TOTAL_STUDY_DAYS = "totalDays"
        const val INTENT_TRIGGER_NOTIFICATION_ID = "notificationId"
        const val INTENT_NOTIFICATION_TRIGGER_VALID_FROM = "validFrom"
    }

    private var triggers: List<QuestionnaireTrigger> = emptyList()

    fun initializeTriggers(dataStoreManager: DataStoreManager) {
        if (triggers.isNotEmpty()) {
            return
        }

        dataStoreManager.getQuestionnairesSync { questionnaires ->
            triggers = questionnaires.flatMap { it.triggers }
        }
    }

    suspend fun handleEvent(
        eventName: String,
        sourceId: UUID?,
        triggerId: Int?,
        context: Context,
        dataStoreManager: DataStoreManager,
        database: AppDatabase
    ) {
        // Handle event triggers
        val eventTriggers = triggers.filterIsInstance<EventQuestionnaireTrigger>()
        if (eventTriggers.isNotEmpty()) {
            val matching = if (eventName == "open_questionnaire" && triggerId != null) {
                // For open_questionnaire events, find by specific trigger ID
                eventTriggers.find { it.id == triggerId }
            } else {
                // For other events, find by event name as before
                eventTriggers.find { it.configuration.eventName == eventName }
            }
            
            if (matching != null) {
                val trigger = matching
                val questionnaires = dataStoreManager.questionnairesFlow.first()

                val matchingQuestionnaire =
                    questionnaires.find { it.questionnaire.id == trigger.questionnaireId }
                if (matchingQuestionnaire != null) {
                    handleEventOnQuestionnaire(
                        matchingQuestionnaire,
                        trigger,
                        context,
                        dataStoreManager,
                        database
                    )
                }
            }
        }

        // Handle notification triggers 
        if (eventName == "put_notification_trigger" && triggerId != null) {
            val floatingWidgetTriggers = triggers.filterIsInstance<EMAFloatingWidgetNotificationTrigger>()
            val matchingTrigger = floatingWidgetTriggers.find { it.id == triggerId }
            
            if (matchingTrigger != null) {
                handleEventPushedNotificationTrigger(
                    matchingTrigger,
                    sourceId,
                    context,
                    dataStoreManager,
                    database
                )
            }
        }
    }

    suspend fun handleEventOnQuestionnaire(
        questionnaire: FullQuestionnaire,
        trigger: EventQuestionnaireTrigger,
        context: Context,
        dataStoreManager: DataStoreManager,
        database: AppDatabase
    ) {
        var pendingId: UUID?

        coroutineScope {
            withContext(Dispatchers.IO) {
                pendingId = PendingQuestionnaire.createEntry(
                    database,
                    dataStoreManager,
                    trigger
                )
            }
        }

        if (pendingId == null) {
            Log.e("EsmHandler", "Failed to create pending questionnaire entry for trigger ${trigger.id}")
            return
        }

        // open questionnaire
        val intent = Intent(context, QuestionnaireActivity::class.java)
        intent.putExtra(QuestionnaireActivity.INTENT_QUESTIONNAIRE, fullQuestionnaireJson.encodeToString(questionnaire))
        intent.putExtra(QuestionnaireActivity.INTENT_PENDING_QUESTIONNAIRE_ID, pendingId.toString())

        // this will make it appear but not go back to the MainActivity afterwards
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

        context.startActivity(intent)
    }

    suspend fun handleEventPushedNotificationTrigger(
        trigger: EMAFloatingWidgetNotificationTrigger,
        pendingQuestionnaireId: UUID?,
        context: Context,
        dataStoreManager: DataStoreManager,
        database: AppDatabase
    ) {
        val pendingQuestionnaireId = pendingQuestionnaireId
        if (pendingQuestionnaireId == null) {
            return
        }

        coroutineScope {
            withContext(Dispatchers.IO) {
                val sourcePendingQuestionnaire = database.pendingQuestionnaireDao().getById(pendingQuestionnaireId)

                val sourceNotificationTriggerId = sourcePendingQuestionnaire?.notificationTriggerUid
                if (sourceNotificationTriggerId == null) {
                    Log.e("EsmHandler", "Pending questionnaire does not have a notification trigger UID")
                    return@withContext
                }

                val sourceNotificationTrigger = database.notificationTriggerDao().getById(sourceNotificationTriggerId)

                if (sourceNotificationTrigger == null) {
                    Log.e("EsmHandler", "Source notification trigger not found in database")
                    return@withContext
                }

                val notificationTrigger = NotificationTrigger(
                    uid = UUID.randomUUID(),
                    addedAt = System.currentTimeMillis(),
                    name = trigger.configuration.name,
                    status = NotificationTriggerStatus.Planned,
                    validFrom = System.currentTimeMillis(),
                    priority = trigger.configuration.priority,
                    timeBucket = sourceNotificationTrigger.timeBucket,
                    modality = trigger.configuration.modality,
                    source = NotificationTriggerSource.RuleBased,
                    questionnaireId = trigger.questionnaireId.toLong(),
                    triggerJson = fullQuestionnaireJson.encodeToString(trigger),
                    updatedAt = System.currentTimeMillis()
                )
                
                database.notificationTriggerDao().insert(notificationTrigger)
                
                // If it's a push notification, push it immediately; otherwise it will be handled by EventContingent modality
                if (trigger.configuration.modality == NotificationTriggerModality.Push) {
                    val notificationHelper = NotificationPushHelper(context)
                    notificationHelper.pushNotificationTrigger(notificationTrigger)
                }
                
                Log.d("EsmHandler", "Created notification trigger for trigger ${trigger.id} with modality ${trigger.configuration.modality}")
            }
        }
    }

    suspend fun scheduleFloatingWidgetNotifications(
        phase: ExperimentalGroupPhase,
        fromTime: Calendar,
        context: Context,
        dataStoreManager: DataStoreManager,
        database: AppDatabase
    ) {
        val triggers = dataStoreManager.questionnairesFlow.first().flatMap { it.triggers }

        val floatingWidgetNotificationScheduler = FloatingWidgetNotificationScheduler()

        val endTime = fromTime.clone() as Calendar
        endTime.add(Calendar.DAY_OF_YEAR, phase.durationDays)

        floatingWidgetNotificationScheduler.scheduleFloatingWidgetNotificationTriggersForPhase(
            context,
            fromTime,
            endTime,
            triggers,
            database,
            phase.name
        )
    }

    suspend fun scheduleRandomEMANotificationsForPhase(
        phase: ExperimentalGroupPhase,
        fromTime: Calendar,
        context: Context,
        dataStoreManager: DataStoreManager
    ) {
        val triggers = dataStoreManager.questionnairesFlow.first().flatMap { it.triggers }
        val emaTriggers =
            triggers.filter { it.type == "random_ema" } as List<RandomEMAQuestionnaireTrigger>
        val phaseTriggers = emaTriggers.filter { it.configuration.phaseName == phase.name }

        for (trigger in phaseTriggers) {
            val initialCalendar = Calendar.getInstance()
            initialCalendar.add(Calendar.MINUTE, trigger.configuration.delayMinutes)

            val questionnaire = dataStoreManager.questionnairesFlow.first()
                .find { it.questionnaire.id == trigger.questionnaireId }?.questionnaire
            if (questionnaire == null) {
                Log.e("EsmHandler", "Questionnaire not found for trigger ${trigger.id}")
                return
            }

            val untilTimestamp =
                fromTime.timeInMillis + TimeUnit.DAYS.toMillis(phase.durationDays.toLong())

            scheduleRandomEMANotificationForTrigger(
                trigger,
                initialCalendar,
                questionnaire.name,
                untilTimestamp,
                context
            )
        }
    }

    fun scheduleRandomEMANotificationForTrigger(
        trigger: RandomEMAQuestionnaireTrigger,
        calendar: Calendar,
        questionnaireName: String,
        untilTimestamp: Long,
        context: Context
    ) {
        val intent = Intent(context, RandomNotificationReceiver::class.java)
        intent.apply {
            putExtra(INTENT_TITLE, "Es ist Zeit für $questionnaireName")
            putExtra(INTENT_TRIGGER_ID, trigger.id)
            putExtra(INTENT_TRIGGER_JSON, fullQuestionnaireJson.encodeToString<QuestionnaireTrigger>(trigger))
            putExtra(INTENT_QUESTIONNAIRE_NAME, questionnaireName)
            putExtra(INTENT_NOTIFY_PHASE_UNTIL_TIMESTAMP, untilTimestamp)
        }

        val nextNotificationTime = getCalendarForNextRandomNotification(trigger, calendar)

        // if the next notification time is after the end of the phase, don't schedule it
        if (nextNotificationTime.timeInMillis > untilTimestamp) {
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val pendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            trigger.id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        Log.i(
            "EsmHandler",
            "Scheduling random EMA notification for ${questionnaireName} at ${nextNotificationTime.timeInMillis}"
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextNotificationTime.timeInMillis,
            pendingIntent
        )
    }

    fun getCalendarForNextRandomNotification(
        trigger: RandomEMAQuestionnaireTrigger,
        currentTime: Calendar
    ): Calendar {
        val randomMinutes =
            (-(trigger.configuration.randomToleranceMinutes / 2)..(trigger.configuration.randomToleranceMinutes / 2)).random()
        val distanceMinutes = trigger.configuration.distanceMinutes

        val timeToAdd = distanceMinutes + randomMinutes

        // calculate next notification time
        val nextNotificationTime = currentTime.clone() as Calendar
        nextNotificationTime.add(Calendar.MINUTE, timeToAdd)

        // check if the notification is in the correct time bucket
        if (!isInTimeBucket(nextNotificationTime, trigger.configuration.timeBucket)) {
            // remove the minutes and try again
            nextNotificationTime.add(Calendar.MINUTE, -(timeToAdd))
            // calculate the next notification time starting from the next day
            nextNotificationTime.add(Calendar.DATE, 1)
            nextNotificationTime.set(Calendar.HOUR_OF_DAY, trigger.configuration.timeBucket.split(":")[0].toInt())
            nextNotificationTime.set(
                Calendar.MINUTE,
                trigger.configuration.timeBucket.split(":")[1].split("-")[0].toInt()
            )
            return getCalendarForNextRandomNotification(trigger, nextNotificationTime)
        }

        return nextNotificationTime
    }

    fun isInTimeBucket(currentTime: Calendar, timeBucket: String): Boolean {
        val start = timeBucket.split("-")[0].split(":")[0].toInt()
        val end = timeBucket.split("-")[1].split(":")[0].toInt()
        val hour = currentTime.get(Calendar.HOUR_OF_DAY)
        return hour in start until end
    }

    suspend fun scheduleOneTimeQuestionnaires(
        context: Context,
        dataStoreManager: DataStoreManager,
        database: AppDatabase
    ) {
        coroutineScope {
            val questionnaires = (Dispatchers.IO) {
                dataStoreManager.questionnairesFlow.first()
            }
            val oneTimeTriggers =
                questionnaires.flatMap { it.triggers }.filter { it.type == "one_time" }

            for (ot in oneTimeTriggers) {
                val trigger = ot as OneTimeQuestionnaireTrigger
                val questionnaire = dataStoreManager.questionnairesFlow.first()
                    .find { it.questionnaire.id == trigger.questionnaireId }?.questionnaire

                if (questionnaire == null) return@coroutineScope

                val scheduleHour = trigger.configuration.time.split(":")[0].toInt()
                val scheduleMinute = trigger.configuration.time.split(":")[1].toInt()

                val studyStart =
                    (Dispatchers.IO) { dataStoreManager.timestampStudyStartedFlow.first() }

                val calendar = Calendar.getInstance()
                calendar.timeInMillis = studyStart
                calendar.apply {
                    set(Calendar.HOUR_OF_DAY, scheduleHour)
                    set(Calendar.MINUTE, scheduleMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.DATE, trigger.configuration.studyDay)
                }

                val intent = Intent(context.applicationContext, OneTimeNotificationReceiver::class.java)
                intent.apply {
                    putExtra(INTENT_TITLE, trigger.configuration.notificationText)
                    putExtra(INTENT_TRIGGER_ID, trigger.id)
                    putExtra(INTENT_TRIGGER_JSON, fullQuestionnaireJson.encodeToString<QuestionnaireTrigger>(trigger))
                    putExtra(INTENT_QUESTIONNAIRE_NAME, questionnaire.name)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context.applicationContext,
                    trigger.id,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                )

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )

                Log.d(
                    "EsmHandler",
                    "Scheduled one time questionnaire for ${questionnaire.name}, on study day: ${trigger.configuration.studyDay} at ${calendar.timeInMillis}"
                )
            }
        }
    }

    suspend fun schedulePeriodicQuestionnaires(
        context: Context,
        dataStoreManager: DataStoreManager,
        database: AppDatabase
    ) {
        coroutineScope {
            val questionnaires = (Dispatchers.IO) {
                dataStoreManager.questionnairesFlow.first()
            }
            val remainingStudyDays = (Dispatchers.IO) {
                dataStoreManager.remainingStudyDaysFlow.first()
            }
            val periodicTriggers =
                questionnaires.flatMap { it.triggers }.filter { it.type == "periodic" }
            for (trigger in periodicTriggers) {
                // schedule periodic questionnaire
                val periodicTrigger = trigger as PeriodicQuestionnaireTrigger

                val nextRemainingDays = remainingStudyDays - 1
                withContext(Dispatchers.IO) {
                    dataStoreManager.saveRemainingStudyDays(nextRemainingDays)
                }

                scheduleNextPeriodicNotification(
                    context,
                    periodicTrigger,
                    remainingStudyDays,
                    remainingStudyDays,
                    questionnaires.find { it.questionnaire.id == trigger.questionnaireId }?.questionnaire?.name
                        ?: "Unknown"
                )
            }
        }
    }

    fun scheduleNextPeriodicNotification(
        context: Context,
        trigger: PeriodicQuestionnaireTrigger,
        totalDays: Int,
        remainingDays: Int,
        title: String
    ) {
        val sendNextNotification = remainingDays > 0
        val nextRemainingDays = remainingDays - 1

        if (!sendNextNotification) {
            return
        }

        val scheduleHour = trigger.configuration.time.split(":")[0].toInt()
        val scheduleMinute = trigger.configuration.time.split(":")[1].toInt()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val nextNotification = getNextNotification(
            Calendar.getInstance(),
            scheduleHour,
            scheduleMinute,
            totalDays,
            remainingDays
        )

        val intent = Intent(context.applicationContext, PeriodicNotificationReceiver::class.java)
        intent.apply {
            putExtra(INTENT_TITLE, "Es ist Zeit für ${title}")
            putExtra(INTENT_TRIGGER_ID, trigger.id)
            putExtra(INTENT_TRIGGER_JSON, fullQuestionnaireJson.encodeToString<QuestionnaireTrigger>(trigger))
            putExtra(INTENT_QUESTIONNAIRE_NAME, title)
            putExtra(INTENT_REMAINING_STUDY_DAYS, nextNotification.remainingDays)
            putExtra(INTENT_TOTAL_STUDY_DAYS, totalDays)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            trigger.id,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextNotification.calendar.timeInMillis,
            pendingIntent
        )

        Log.d(
            "EsmHandler",
            "Scheduled periodic questionnaire for ${title}, remaining days: ${nextRemainingDays}"
        )
    }

    data class NextNotification(val calendar: Calendar, val remainingDays: Int)

    fun getNextNotification(
        calendar: Calendar,
        scheduleHour: Int,
        scheduleMinute: Int,
        totalDays: Int,
        remainingDays: Int
    ): NextNotification {
        val newCalendar = if (totalDays == remainingDays && shouldScheduleOnSameDay(
                calendar,
                scheduleHour,
                scheduleMinute
            )
        ) {
            calculateNextNotificationTime(
                calendar.apply { add(Calendar.DATE, -1) },
                scheduleHour,
                scheduleMinute
            )
        } else {
            calculateNextNotificationTime(calendar, scheduleHour, scheduleMinute)
        }

        return NextNotification(newCalendar, remainingDays - 1)
    }

    fun calculateNextNotificationTime(
        calendar: Calendar,
        scheduleHour: Int,
        scheduleMinute: Int
    ): Calendar {
        val selectedDate = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, scheduleHour)
            set(Calendar.MINUTE, scheduleMinute)
            add(Calendar.DATE, 1)
        }

        val year = selectedDate.get(Calendar.YEAR)
        val month = selectedDate.get(Calendar.MONTH)
        val day = selectedDate.get(Calendar.DAY_OF_MONTH)
        val hour = selectedDate.get(Calendar.HOUR_OF_DAY)
        val minute = selectedDate.get(Calendar.MINUTE)

        val newCalendar = Calendar.getInstance()
        newCalendar.set(year, month, day, hour, minute)

        return newCalendar
    }

    fun shouldScheduleOnSameDay(
        calendar: Calendar,
        scheduleHour: Int,
        scheduleMinute: Int
    ): Boolean {
        val selectedDate = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, scheduleHour)
            set(Calendar.MINUTE, scheduleMinute)
        }

        return selectedDate.timeInMillis > calendar.timeInMillis
    }
}

class NotificationPushHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun sendReminderNotification(triggerId: Int, pendingQuestionnaireId: UUID?, title: String?, questionnaireName: String? = "") {
        val intent = Intent(context, QuestionnaireActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(QuestionnaireActivity.INTENT_TRIGGER_ID, triggerId)
            putExtra(QuestionnaireActivity.INTENT_PENDING_QUESTIONNAIRE_ID,
                pendingQuestionnaireId?.toString()
            )
        }

        val notification = buildNotification("It's time for $questionnaireName", intent)

        notificationManager.notify(triggerId, notification)
    }

    fun pushNotificationTrigger(notificationTrigger: NotificationTrigger) {
        try {
            val trigger: EMAFloatingWidgetNotificationTrigger =
                notificationTrigger.triggerJson.let { fullQuestionnaireJson.decodeFromString<EMAFloatingWidgetNotificationTrigger>(it) }

            val notification = buildNotification(trigger.configuration.notificationText, Intent())

            notificationManager.notify(notificationTrigger.uid.hashCode(), notification)
        } catch (e: Exception) {
            Log.e(NotificationPushHelper::class.simpleName, e.message ?: "Error pushing notification trigger")
        }
    }

    private fun buildNotification(title: String, intent: Intent): Notification {
        return NotificationCompat.Builder(context, "SEChannel")
            .setContentText(context.getString(R.string.app_name))
            .setContentTitle(title)
            .setSmallIcon(R.drawable.notification_whale)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.ic_launcher_whale_foreground
                )
            )
            .setPriority(NotificationManager.IMPORTANCE_HIGH)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(title)
            )
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
    }

    private fun Context.bitmapFromResource(
        @DrawableRes resId: Int
    ) = BitmapFactory.decodeResource(
        resources,
        resId
    )
}