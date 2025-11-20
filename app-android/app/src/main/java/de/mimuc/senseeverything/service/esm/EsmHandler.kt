package de.mimuc.senseeverything.service.esm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import de.mimuc.senseeverything.activity.esm.QuestionnaireActivity
import de.mimuc.senseeverything.api.model.ExperimentalGroupPhase
import de.mimuc.senseeverything.api.model.ema.EMAFloatingWidgetNotificationTrigger
import de.mimuc.senseeverything.api.model.ema.EventQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.EventTriggerModality
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.ema.OneTimeQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.PeriodicQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.QuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.RandomEMAQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.UpdateNextNotificationTrigger
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.NotificationTriggerModality
import de.mimuc.senseeverything.db.models.NotificationTriggerSource
import de.mimuc.senseeverything.db.models.NotificationTriggerStatus
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.db.models.ScheduledAlarm
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.invoke
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.util.Calendar
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class EsmHandler {
    companion object {
        const val INTENT_TITLE = "title"
        const val INTENT_TRIGGER_ID = "id"
        const val INTENT_TRIGGER_JSON = "triggerJson"
        const val INTENT_REMINDER_JSON = "reminderJson"
        const val INTENT_PENDING_QUESTIONNAIRE_ID = "pendingQuestionnaireId"
        const val INTENT_QUESTIONNAIRE_NAME = "questionnaireName"
        const val INTENT_NOTIFY_PHASE_UNTIL_TIMESTAMP = "untilTimestamp"
        const val INTENT_REMAINING_STUDY_DAYS = "remainingDays"
        const val INTENT_TOTAL_STUDY_DAYS = "totalDays"
        const val INTENT_TRIGGER_NOTIFICATION_ID = "notificationId"
        const val INTENT_NOTIFICATION_TRIGGER_VALID_FROM = "validFrom"

        suspend fun rescheduleQuestionnaires(
            context: Context,
            dataStoreManager: DataStoreManager,
            database: AppDatabase
        ) {
            WHALELog.i("EsmHandler", "Rescheduling questionnaires")

            schedulePeriodicQuestionnaires(context, dataStoreManager, database)
            scheduleOneTimeQuestionnaires(context, dataStoreManager, database)

            // also reschedule already scheduled floating widget notifications
            val floatingWidgetNotificationScheduler = FloatingWidgetNotificationScheduler()
            floatingWidgetNotificationScheduler.schedulePlannedNotificationTriggers(context, database)
        }

        suspend fun schedulePeriodicQuestionnaires(
            context: Context,
            dataStoreManager: DataStoreManager,
            database: AppDatabase
        ) {
            coroutineScope {
                WHALELog.i("EsmHandler", "Scheduling periodic questionnaires")

                val questionnaires = (Dispatchers.IO) {
                    dataStoreManager.questionnairesFlow.first()
                }
                val studyStartTimestamp = (Dispatchers.IO) {
                    dataStoreManager.timestampStudyStartedFlow.first()
                }

                val studyDays = (Dispatchers.IO) {
                    dataStoreManager.studyDaysFlow.first()
                }

                val studyEndTimestamp = studyStartTimestamp + TimeUnit.DAYS.toMillis(studyDays.toLong())

                val periodicTriggers =
                    questionnaires.flatMap { it.triggers }.filter { it.type == "periodic" }

                for (trigger in periodicTriggers) {
                    val periodicTrigger = trigger as PeriodicQuestionnaireTrigger
                    val questionnaireName = questionnaires.find {
                        it.questionnaire.id == trigger.questionnaireId
                    }?.questionnaire?.name ?: "Unknown"

                    scheduleNextPeriodicNotificationStateless(
                        context,
                        periodicTrigger,
                        studyStartTimestamp,
                        studyEndTimestamp,
                        questionnaireName,
                        database
                    )
                }
            }
        }

        suspend fun scheduleNextPeriodicNotificationStateless(
            context: Context,
            trigger: PeriodicQuestionnaireTrigger,
            studyStartTimestamp: Long,
            studyEndTimestamp: Long,
            questionnaireName: String,
            database: AppDatabase
        ) {
            val scheduleHour = trigger.configuration.time.split(":")[0].toInt()
            val scheduleMinute = trigger.configuration.time.split(":")[1].toInt()

            val nextNotificationTime = calculateNextPeriodicNotificationTime(
                Calendar.getInstance(),
                studyEndTimestamp,
                scheduleHour,
                scheduleMinute
            )

            if (nextNotificationTime == null || nextNotificationTime > studyEndTimestamp) {
                WHALELog.i("EsmHandler", "No more periodic notifications to schedule for trigger ${trigger.id}")
                return
            }

            // Calculate study day based on notification time and study start
            val studyDay = calculateStudyDay(studyStartTimestamp, nextNotificationTime)

            val scheduledAlarm = ScheduledAlarm.getOrCreateScheduledAlarm(
                database,
                "PeriodicNotificationReceiver",
                "periodic_${trigger.id}_day${studyDay}",
                nextNotificationTime
            )

            val intent = Intent(context.applicationContext, PeriodicNotificationReceiver::class.java)
            intent.apply {
                putExtra(INTENT_TITLE, trigger.configuration.notificationText)
                putExtra(INTENT_TRIGGER_ID, trigger.id)
                putExtra(INTENT_TRIGGER_JSON, fullQuestionnaireJson.encodeToString<QuestionnaireTrigger>(trigger))
                putExtra(INTENT_QUESTIONNAIRE_NAME, questionnaireName)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context.applicationContext,
                scheduledAlarm.requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextNotificationTime,
                pendingIntent
            )

            WHALELog.i(
                "EsmHandler",
                "Scheduled stateless periodic questionnaire for $questionnaireName at $nextNotificationTime (study day $studyDay)"
            )
        }


        fun calculateStudyDay(studyStartTimestamp: Long, notificationTimestamp: Long): Int {
            val studyStartCalendar = Calendar.getInstance().apply {
                timeInMillis = studyStartTimestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val notificationCalendar = Calendar.getInstance().apply {
                timeInMillis = notificationTimestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val daysDifference = TimeUnit.MILLISECONDS.toDays(
                notificationCalendar.timeInMillis - studyStartCalendar.timeInMillis
            )

            // Study days are 1-indexed (day 1, day 2, etc.)
            return (daysDifference + 1).toInt()
        }

        fun calculateNextPeriodicNotificationTime(
            currentCalendar: Calendar,
            studyEndTimestamp: Long,
            scheduleHour: Int,
            scheduleMinute: Int
        ): Long? {
            val currentTime = currentCalendar.timeInMillis

            // Start from the next day after current time
            val nextNotificationCalendar = Calendar.getInstance().apply {
                timeInMillis = currentTime
                set(Calendar.HOUR_OF_DAY, scheduleHour)
                set(Calendar.MINUTE, scheduleMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

                // If the time has passed today, schedule for tomorrow
                if (timeInMillis <= currentTime) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            return if (nextNotificationCalendar.timeInMillis <= studyEndTimestamp) {
                nextNotificationCalendar.timeInMillis
            } else {
                null
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
            val triggers = dataStoreManager.questionnairesFlow.first().flatMap { it.triggers }
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
                            sourceId,
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
            sourceId: UUID?,
            context: Context,
            dataStoreManager: DataStoreManager,
            database: AppDatabase
        ) {
            val pendingQuestionnaire = coroutineScope {
                withContext(Dispatchers.IO) {
                    PendingQuestionnaire.createEntry(
                        database,
                        dataStoreManager,
                        trigger,
                        notificationTriggerUid = null,
                        sourcePendingNotificationId = sourceId
                    )
                }
            }

            val pendingId = pendingQuestionnaire?.uid

            if (pendingId == null) {
                WHALELog.e("EsmHandler", "Failed to create pending questionnaire entry for trigger ${trigger.id}")
                return
            }

            if (trigger.configuration.modality == EventTriggerModality.Push) {
                val notificationHelper = NotificationPushHelper(context)
                notificationHelper.sendReminderNotification(
                    trigger.id,
                    pendingId,
                    trigger.configuration.notificationText,
                    questionnaire.questionnaire.name
                )

                if (trigger.configuration.reminder != null) {
                    scheduleReminderNotification(
                        context,
                        database,
                        pendingQuestionnaire,
                        trigger.configuration.reminder,
                        trigger,
                        questionnaire.questionnaire.name,
                        System.currentTimeMillis()
                    )
                }
            } else if (trigger.configuration.modality == EventTriggerModality.Open) {
                // open questionnaire
                val intent = Intent(context, QuestionnaireActivity::class.java)
                intent.putExtra(QuestionnaireActivity.INTENT_QUESTIONNAIRE, fullQuestionnaireJson.encodeToString(questionnaire))
                intent.putExtra(QuestionnaireActivity.INTENT_PENDING_QUESTIONNAIRE_ID, pendingId.toString())

                // this will make it appear but not go back to the MainActivity afterwards
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

                context.startActivity(intent)
            }
        }

        suspend fun handleEventPushedNotificationTrigger(
            trigger: EMAFloatingWidgetNotificationTrigger,
            pendingQuestionnaireId: UUID?,
            context: Context,
            dataStoreManager: DataStoreManager,
            database: AppDatabase
        ) {
            val pendingQuestionnaireId = pendingQuestionnaireId ?: return

            coroutineScope {
                withContext(Dispatchers.IO) {
                    val sourcePendingQuestionnaire = database.pendingQuestionnaireDao().getById(pendingQuestionnaireId)

                    val sourceNotificationTriggerId = sourcePendingQuestionnaire?.notificationTriggerUid
                    if (sourceNotificationTriggerId == null) {
                        WHALELog.e("EsmHandler", "Pending questionnaire does not have a notification trigger UID")
                        return@withContext
                    }

                    val sourceNotificationTrigger = database.notificationTriggerDao().getById(sourceNotificationTriggerId)

                    if (sourceNotificationTrigger == null) {
                        WHALELog.e("EsmHandler", "Source notification trigger not found in database")
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
                        triggerJson = fullQuestionnaireJson.encodeToString<QuestionnaireTrigger>(trigger),
                        updatedAt = System.currentTimeMillis()
                    )

                    database.notificationTriggerDao().insert(notificationTrigger)

                    // If it's a push notification, push it immediately; otherwise it will be handled by EventContingent modality
                    if (trigger.configuration.modality == NotificationTriggerModality.Push) {
                        val notificationHelper = NotificationPushHelper(context)
                        notificationHelper.pushNotificationTrigger(notificationTrigger)
                    }

                    WHALELog.i("EsmHandler", "Created notification trigger for trigger ${trigger.id} with modality ${trigger.configuration.modality}")
                }
            }
        }

        suspend fun handleUpdateNextNotificationTrigger(
            action: UpdateNextNotificationTrigger,
            pendingQuestionnaireId: UUID?,
            context: Context,
            database: AppDatabase
        ) {
            withContext(Dispatchers.IO) {
                val pendingQuestionnaire = database.pendingQuestionnaireDao().getById(pendingQuestionnaireId!!)

                if (pendingQuestionnaire == null) {
                    WHALELog.e("EsmHandler", "Pending questionnaire not found for UpdateNextNotificationTrigger")
                    return@withContext
                }

                val notificationTriggerId = pendingQuestionnaire.notificationTriggerUid
                if (notificationTriggerId == null) {
                    WHALELog.e("EsmHandler", "Pending questionnaire does not have a notification trigger UID for UpdateNextNotificationTrigger")
                    return@withContext
                }

                val currentNotificationTrigger = database.notificationTriggerDao().getById(notificationTriggerId)
                if (currentNotificationTrigger == null) {
                    WHALELog.e("EsmHandler", "Notification trigger not found in database for UpdateNextNotificationTrigger")
                    return@withContext
                }

                val nextNotificationTriggers = database.notificationTriggerDao().getNextForName(action.triggerName, currentNotificationTrigger.validFrom)
                if (nextNotificationTriggers.isEmpty()) {
                    WHALELog.e("EsmHandler", "Next notification trigger not found in database for UpdateNextNotificationTrigger")
                    return@withContext
                }

                val nextNotificationTrigger = nextNotificationTriggers.first()

                // check if in same time bucket
                val sameTimeBucket = nextNotificationTrigger.timeBucket == currentNotificationTrigger.timeBucket
                if (action.requireSameTimeBucket && !sameTimeBucket) {
                    return@withContext
                }

                // check if next trigger is too far away
                if (action.maxDistanceMinutes != -1) {
                    val distanceMillis = nextNotificationTrigger.validFrom - currentNotificationTrigger.validFrom
                    val exceedsDistance = distanceMillis.milliseconds.inWholeMinutes > action.maxDistanceMinutes
                    if (exceedsDistance) return@withContext
                }

                nextNotificationTrigger.status = action.toStatus
                nextNotificationTrigger.updatedAt = System.currentTimeMillis()
                database.notificationTriggerDao().update(nextNotificationTrigger)
                WHALELog.i("EsmHandler", "Updated notification trigger ${nextNotificationTrigger.uid} to status ${action.toStatus}")
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
                    WHALELog.e("EsmHandler", "Questionnaire not found for trigger ${trigger.id}")
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

            WHALELog.i(
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
                WHALELog.i("EsmHandler", "Scheduling one time questionnaires")

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
                        add(Calendar.DATE, trigger.configuration.studyDay - 1)
                    }

                    // don't schedule if the time is in the past
                    if (calendar.timeInMillis < System.currentTimeMillis()) {
                        WHALELog.i("EsmHandler", "Not scheduling one time questionnaire for ${questionnaire.name} in the past")
                        continue
                    }

                    val scheduledAlarm = ScheduledAlarm.getOrCreateScheduledAlarm(
                        database,
                        "OneTimeNotificationReceiver",
                        "one_time_${trigger.id}",
                        calendar.timeInMillis
                    )

                    val intent = Intent(context.applicationContext, OneTimeNotificationReceiver::class.java)
                    intent.apply {
                        putExtra(INTENT_TITLE, trigger.configuration.notificationText)
                        putExtra(INTENT_TRIGGER_ID, trigger.id)
                        putExtra(INTENT_TRIGGER_JSON, fullQuestionnaireJson.encodeToString<QuestionnaireTrigger>(trigger))
                        putExtra(INTENT_QUESTIONNAIRE_NAME, questionnaire.name)
                    }

                    val pendingIntent = PendingIntent.getBroadcast(
                        context.applicationContext,
                        scheduledAlarm.requestCode,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
                    )

                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )

                    WHALELog.i(
                        "EsmHandler",
                        "Scheduled one time questionnaire for ${questionnaire.name}, on study day: ${trigger.configuration.studyDay} at ${calendar.timeInMillis}"
                    )
                }
            }
        }
    }
}
