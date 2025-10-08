package de.mimuc.senseeverything.study

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.api.model.ExperimentalGroupPhase
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.ScheduledAlarm
import de.mimuc.senseeverything.helpers.goAsync
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.esm.EsmHandler
import kotlinx.coroutines.flow.first
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

        WHALELog.i("ChangePhaseReceiver", "Changing phase to ${phase.name} (from ${phase.fromDay} for ${phase.durationDays} days)")

        EsmHandler.scheduleRandomEMANotificationsForPhase(phase, Calendar.getInstance(), context.applicationContext, dataStoreManager)
        EsmHandler.scheduleFloatingWidgetNotifications(phase, Calendar.getInstance(), context.applicationContext, dataStoreManager, database)
    }
}

suspend fun reschedulePhaseChanges(context: Context, database: AppDatabase, dataStoreManager: DataStoreManager) {
    val studyStartTimestamp = dataStoreManager.timestampStudyStartedFlow.first()
    val phases = dataStoreManager.studyPhasesFlow.first()

    if (phases != null) {
        schedulePhaseChanges(context, studyStartTimestamp, phases, database)
    } else {
        WHALELog.w("ChangePhaseReceiver", "Cannot reschedule phase changes, missing phases")
    }
}

suspend fun schedulePhaseChanges(context: Context, studyStartTimestamp: Long, phases: List<ExperimentalGroupPhase>?, database: AppDatabase) {
    if (phases == null) return

    // schedule an alarm for each phase change so that it can be adapted
    for (phase in phases) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ChangePhaseReceiver::class.java)
        intent.putExtra("phaseJson", Json.encodeToString(phase))

        // if fromDay is 0, schedule phase change 2 minutes after study start, otherwise on start of the day
        val triggerTimestamp = if (phase.fromDay == 0) {
            studyStartTimestamp + TimeUnit.MINUTES.toMillis(2)
        } else {
            timestampToNextFullDay(studyStartTimestamp, phase.fromDay)
        }

        if (triggerTimestamp < System.currentTimeMillis()) {
            WHALELog.i("ChangePhaseReceiver", "Not scheduling phase change to ${phase.name} at $triggerTimestamp, timestamp is in the past")
            continue
        }

        val scheduledAlarm = ScheduledAlarm.getOrCreateScheduledAlarm(
            database,
            ChangePhaseReceiver::class.java.name,
            "phase_${phase.fromDay}",
            triggerTimestamp
        )

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduledAlarm.requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        WHALELog.i("ChangePhaseReceiver", "Scheduling phase change to ${phase.name} at $triggerTimestamp")
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            scheduledAlarm.timestamp,
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