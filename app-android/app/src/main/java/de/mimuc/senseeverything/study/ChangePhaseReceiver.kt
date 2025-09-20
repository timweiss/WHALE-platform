package de.mimuc.senseeverything.study

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.api.model.ExperimentalGroupPhase
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.helpers.goAsync
import de.mimuc.senseeverything.service.SEApplicationController
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class ChangePhaseReceiver  : BroadcastReceiver() {
    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context?, intent: Intent?) = goAsync {
        if (context == null) return@goAsync

        val phaseJson = intent?.getStringExtra("phaseJson") ?: return@goAsync
        val phase = Json.decodeFromString<ExperimentalGroupPhase>(phaseJson)

        Log.d("ChangePhaseReceiver", "Changing phase to $phase")

        val application = context.applicationContext as SEApplicationController
        application.esmHandler.scheduleRandomEMANotificationsForPhase(phase, Calendar.getInstance(), context.applicationContext, dataStoreManager)
        application.esmHandler.scheduleFloatingWidgetNotifications(phase, Calendar.getInstance(), context.applicationContext, dataStoreManager, database)
    }
}

fun schedulePhaseChanges(context: Context, studyStartTimestamp: Long, phases: List<ExperimentalGroupPhase>?) {
    if (phases == null) return

    // schedule an alarm for each phase change so that it can be adapted
    for (phase in phases) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ChangePhaseReceiver::class.java)
        intent.putExtra("phaseJson", Json.encodeToString(phase))

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        // if fromDay is 0, schedule phase change 5 minutes after study start, otherwise on start of the day
        val triggerTimestamp = if (phase.fromDay == 0) {
            studyStartTimestamp + TimeUnit.MINUTES.toMillis(5)
        } else {
            timestampToNextFullDay(studyStartTimestamp, phase.fromDay)
        }

        Log.d("ChangePhaseReceiver", "Scheduling phase change to ${phase.name} at $triggerTimestamp")
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTimestamp,
            pendingIntent
        )
    }
}

fun timestampToNextFullDay(studyStartTimestamp: Long, fromDay: Int): Long {
    val calendar = Calendar.getInstance().apply {
        timeInMillis = studyStartTimestamp + TimeUnit.DAYS.toMillis(fromDay.toLong())
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}