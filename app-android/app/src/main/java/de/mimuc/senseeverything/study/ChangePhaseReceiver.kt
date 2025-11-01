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
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

enum class PhaseSchedulingStatus {
    SCHEDULED,
    SKIPPED,
    CHANGED
}

@Serializable
data class PhaseScheduleInfo(
    val phaseName: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    var status: PhaseSchedulingStatus
)

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

        val phaseSchedule = dataStoreManager.phaseSchedulesFlow.first()
        if (phaseSchedule != null) {
            phaseSchedule.find { it.phaseName == phase.name }?.let {
                it.status =
                    PhaseSchedulingStatus.CHANGED
            }
            dataStoreManager.savePhaseSchedules(phaseSchedule)
        }
    }
}

suspend fun reschedulePhaseChanges(context: Context, database: AppDatabase, dataStoreManager: DataStoreManager): List<PhaseScheduleInfo> {
    val studyStartTimestamp = dataStoreManager.timestampStudyStartedFlow.first()
    val phases = dataStoreManager.studyPhasesFlow.first()

    return if (phases != null) {
        schedulePhaseChanges(context, studyStartTimestamp, phases, database)
    } else {
        WHALELog.w("ChangePhaseReceiver", "Cannot reschedule phase changes, missing phases")
        emptyList()
    }
}

suspend fun schedulePhaseChanges(context: Context, studyStartTimestamp: Long, phases: List<ExperimentalGroupPhase>?, database: AppDatabase): List<PhaseScheduleInfo> {
    if (phases == null) return emptyList()

    val phaseSchedules = mutableListOf<PhaseScheduleInfo>()

    // schedule an alarm for each phase change so that it can be adapted
    for ((index, phase) in phases.withIndex()) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ChangePhaseReceiver::class.java)
        intent.putExtra("phaseJson", Json.encodeToString(phase))

        // if fromDay is 0, schedule phase change 2 minutes after study start, otherwise on start of the day
        val triggerTimestamp = if (phase.fromDay == 0) {
            studyStartTimestamp + TimeUnit.MINUTES.toMillis(2)
        } else {
            timestampToNextFullDay(studyStartTimestamp, phase.fromDay)
        }

        // Calculate end timestamp: next phase start or this phase start + duration
        val endTimestamp = if (index < phases.size - 1) {
            val nextPhase = phases[index + 1]
            if (nextPhase.fromDay == 0) {
                studyStartTimestamp + TimeUnit.MINUTES.toMillis(2)
            } else {
                timestampToNextFullDay(studyStartTimestamp, nextPhase.fromDay)
            }
        } else {
            triggerTimestamp + TimeUnit.DAYS.toMillis(phase.durationDays.toLong())
        }

        val status = if (triggerTimestamp < System.currentTimeMillis()) {
            WHALELog.i("ChangePhaseReceiver", "Not scheduling phase change to ${phase.name} at $triggerTimestamp, timestamp is in the past")
            PhaseSchedulingStatus.SKIPPED
        } else {
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
            PhaseSchedulingStatus.SCHEDULED
        }

        phaseSchedules.add(
            PhaseScheduleInfo(
                phaseName = phase.name,
                startTimestamp = triggerTimestamp,
                endTimestamp = endTimestamp,
                status = status
            )
        )
    }

    logPhaseSchedule(phaseSchedules)
    return phaseSchedules
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

fun logPhaseSchedule(phaseSchedules: List<PhaseScheduleInfo>) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val sb = StringBuilder()

    sb.appendLine("=== Phase Schedule ===")
    sb.appendLine("Total phases: ${phaseSchedules.size}")

    phaseSchedules.forEachIndexed { index, phase ->
        val startDate = dateFormat.format(Date(phase.startTimestamp))
        val endDate = dateFormat.format(Date(phase.endTimestamp))
        val durationDays = TimeUnit.MILLISECONDS.toDays(phase.endTimestamp - phase.startTimestamp)

        sb.appendLine("Phase ${index + 1}: ${phase.phaseName}")
        sb.appendLine("  Start: $startDate (${phase.startTimestamp})")
        sb.appendLine("  End:   $endDate (${phase.endTimestamp})")
        sb.appendLine("  Duration: $durationDays days")
        sb.appendLine("  Status: ${phase.status}")
    }

    if (phaseSchedules.isNotEmpty()) {
        val lastPhase = phaseSchedules.last()
        val studyEndDate = dateFormat.format(Date(lastPhase.endTimestamp))
        sb.appendLine("Study End: $studyEndDate (${lastPhase.endTimestamp})")
    }

    sb.append("=====================")

    WHALELog.i("ChangePhaseReceiver", sb.toString())
}