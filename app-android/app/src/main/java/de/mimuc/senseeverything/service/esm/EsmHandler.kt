package de.mimuc.senseeverything.service.esm

import android.app.AlarmManager
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
import de.mimuc.senseeverything.api.model.EventQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ExperimentalGroupPhase
import de.mimuc.senseeverything.api.model.FullQuestionnaire
import de.mimuc.senseeverything.api.model.PeriodicQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.QuestionnaireTrigger
import de.mimuc.senseeverything.api.model.RandomEMAQuestionnaireTrigger
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.PendingQuestionnaire
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.invoke
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

class EsmHandler {
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
        context: Context,
        dataStoreManager: DataStoreManager,
        database: AppDatabase
    ) {
        val eventTriggers = triggers.filter { it.type == "event" }
        if (eventTriggers.isNotEmpty()) {
            // Handle event
            val matching =
                eventTriggers.find { (it as EventQuestionnaireTrigger).eventName == eventName }
            if (matching != null) {
                val trigger = matching as EventQuestionnaireTrigger
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
    }

    suspend fun handleEventOnQuestionnaire(
        questionnaire: FullQuestionnaire,
        trigger: EventQuestionnaireTrigger,
        context: Context,
        dataStoreManager: DataStoreManager,
        database: AppDatabase
    ) {
        var pendingId: Long

        coroutineScope {
            withContext(Dispatchers.IO) {
                pendingId = database.pendingQuestionnaireDao().insert(
                    PendingQuestionnaire(
                        0,
                        System.currentTimeMillis(),
                        System.currentTimeMillis() + 1000 * 60 * 15,
                        questionnaire.toJson().toString()
                    )
                )
            }
        }

        // open questionnaire
        val intent = Intent(context, QuestionnaireActivity::class.java)
        intent.putExtra("questionnaire", questionnaire.toJson().toString())
        intent.putExtra("pendingId", pendingId)

        // this will make it appear but not go back to the MainActivity afterwards
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

        context.startActivity(intent)
    }

    suspend fun scheduleRandomEMANotificationsForPhase(
        phase: ExperimentalGroupPhase,
        fromTime: Calendar,
        context: Context,
        dataStoreManager: DataStoreManager
    ) {
        val triggers = triggers.filter { it.type == "random_ema" } as List<RandomEMAQuestionnaireTrigger>
        val phaseTriggers = triggers.filter { it.phaseName == phase.name }

        for (trigger in phaseTriggers) {
            val initialCalendar = Calendar.getInstance()
            initialCalendar.add(Calendar.MINUTE, trigger.delayMinutes)

            val questionnaire = dataStoreManager.questionnairesFlow.first().find { it.questionnaire.id == trigger.questionnaireId }?.questionnaire
            if (questionnaire == null) {
                Log.e("EsmHandler", "Questionnaire not found for trigger ${trigger.id}")
                return
            }

            val untilTimestamp = fromTime.timeInMillis + TimeUnit.DAYS.toMillis(phase.durationDays.toLong())

            scheduleRandomEMANotificationForTrigger(trigger, initialCalendar, questionnaire.name, untilTimestamp, context)
        }
    }

    fun scheduleRandomEMANotificationForTrigger(trigger: RandomEMAQuestionnaireTrigger, calendar: Calendar, questionnaireName: String, untilTimestamp: Long, context: Context) {
        val intent = Intent(context, RandomNotificationReceiver::class.java)
        intent.apply {
            putExtra("title", "Es ist Zeit für $questionnaireName")
            putExtra("id", trigger.id)
            putExtra("triggerJson", trigger.toJson().toString())
            putExtra("questionnaireId", trigger.questionnaireId)
            putExtra("questionnaireName", questionnaireName)
            putExtra("untilTimestamp", untilTimestamp)
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

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextNotificationTime.timeInMillis,
            pendingIntent
        )
    }

    fun getCalendarForNextRandomNotification(trigger: RandomEMAQuestionnaireTrigger, currentTime: Calendar): Calendar {
        val randomMinutes = (-(trigger.randomToleranceMinutes/2)..(trigger.randomToleranceMinutes/2)).random()
        val distanceMinutes = trigger.distanceMinutes

        val timeToAdd = distanceMinutes + randomMinutes

        // calculate next notification time
        val nextNotificationTime = currentTime.clone() as Calendar
        nextNotificationTime.add(Calendar.MINUTE, timeToAdd)

        // check if the notification is in the correct time bucket
        if (!isInTimeBucket(nextNotificationTime, trigger.timeBucket)) {
            // remove the minutes and try again
            nextNotificationTime.add(Calendar.MINUTE, -(timeToAdd))
            // calculate the next notification time starting from the next day
            nextNotificationTime.add(Calendar.DATE, 1)
            nextNotificationTime.set(Calendar.HOUR_OF_DAY, trigger.timeBucket.split(":")[0].toInt())
            nextNotificationTime.set(Calendar.MINUTE, trigger.timeBucket.split(":")[1].split("-")[0].toInt())
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

        val scheduleHour = trigger.time.split(":")[0].toInt()
        val scheduleMinute = trigger.time.split(":")[1].toInt()

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
            putExtra("title", "Es ist Zeit für ${title}")
            putExtra("id", trigger.id)
            putExtra("triggerJson", trigger.toJson().toString())
            putExtra("questionnaireId", trigger.questionnaireId)
            putExtra("questionnaireName", title)
            putExtra("remainingDays", nextNotification.remainingDays)
            putExtra("totalDays", totalDays)
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

class ReminderNotification(private val context: Context) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun sendReminderNotification(triggerId: Int, title: String?) {
        val intent = Intent(context, QuestionnaireActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("triggerId", triggerId)
        }

        val notification = NotificationCompat.Builder(context, "SEChannel")
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
                    .bigText("It's time for $title")
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

        notificationManager.notify(triggerId, notification)
    }

    private fun Context.bitmapFromResource(
        @DrawableRes resId: Int
    ) = BitmapFactory.decodeResource(
        resources,
        resId
    )

}