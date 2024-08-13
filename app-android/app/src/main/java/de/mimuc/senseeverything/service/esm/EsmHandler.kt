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
import de.mimuc.senseeverything.api.model.PeriodicQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.QuestionnaireTrigger
import de.mimuc.senseeverything.data.DataStoreManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.invoke
import kotlinx.coroutines.withContext
import java.util.Calendar

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

    fun handleEvent(eventName: String, context: Context, dataStoreManager: DataStoreManager) {
        val eventTriggers = triggers.filter { it.type == "event" }
        if (eventTriggers.isNotEmpty()) {
            // Handle event
            val matching = eventTriggers.find { (it as EventQuestionnaireTrigger).eventName == eventName }
            if (matching != null) {
                val trigger = matching as EventQuestionnaireTrigger
                dataStoreManager.getQuestionnairesSync { questionnaires ->
                    val matchingQuestionnaire = questionnaires.find { it.questionnaire.id == trigger.questionnaireId }
                    if (matchingQuestionnaire != null) {
                        // open questionnaire
                        val intent = Intent(context, QuestionnaireActivity::class.java)
                        intent.putExtra("questionnaire", matchingQuestionnaire.toJson().toString())
                        // this will make it appear but not go back to the MainActivity afterwards
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

                        context.startActivity(intent)
                    }
                }
            }
        }
    }

    suspend fun schedulePeriodicQuestionnaires(context: Context, dataStoreManager: DataStoreManager) {
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

                scheduleNextPeriodicNotification(context, periodicTrigger, remainingStudyDays, questionnaires.find { it.questionnaire.id == trigger.questionnaireId }?.questionnaire?.name ?: "Unknown")
            }
        }
    }

    fun scheduleNextPeriodicNotification(context: Context, trigger: PeriodicQuestionnaireTrigger, remainingDays: Int, title: String) {
        val sendNextNotification = remainingDays > 0
        val nextRemainingDays = remainingDays - 1

        if (!sendNextNotification) {
            return
        }

        val intent = Intent(context.applicationContext, PeriodicNotificationReceiver::class.java)
        intent.apply {
            putExtra("title", "Es ist Zeit für ${title}")
            putExtra("id", trigger.id)
            putExtra("triggerJson", trigger.toJson().toString())
            putExtra("questionnaireId", trigger.questionnaireId)
            putExtra("questionnaireName", title)
            putExtra("remainingDays", nextRemainingDays)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            trigger.id,
            intent,
            PendingIntent.FLAG_MUTABLE
        )

        val scheduleHour = trigger.time.split(":")[0].toInt()
        val scheduleMinute = trigger.time.split(":")[1].toInt()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val selectedDate = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.MINUTE, 1)
            // set(Calendar.HOUR_OF_DAY, scheduleHour)
            // set(Calendar.MINUTE, scheduleMinute)
        }

        val year = selectedDate.get(Calendar.YEAR)
        val month = selectedDate.get(Calendar.MONTH)
        val day = selectedDate.get(Calendar.DAY_OF_MONTH)
        val hour = selectedDate.get(Calendar.HOUR_OF_DAY)
        val minute = selectedDate.get(Calendar.MINUTE)

        val calendar = Calendar.getInstance()
        calendar.set(year, month, day, hour, minute)

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )

        Log.d("EsmHandler", "Scheduled periodic questionnaire for ${title}, remaining days: ${nextRemainingDays}")
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
            .setSmallIcon(R.drawable.ic_launcher)
            .setLargeIcon(
                BitmapFactory.decodeResource(context.resources,
                R.drawable.ic_launcher
            ))
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