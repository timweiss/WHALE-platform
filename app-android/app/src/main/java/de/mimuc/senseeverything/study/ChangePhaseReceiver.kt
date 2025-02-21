package de.mimuc.senseeverything.study

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.api.model.ExperimentalGroupPhase
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

@AndroidEntryPoint
class ChangePhaseReceiver  : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val phaseJson = intent?.getStringExtra("phaseJson") ?: return
        val phase = Json.decodeFromString<ExperimentalGroupPhase>(phaseJson)

        // todo: change the display mode of the widget
        // todo: schedule random ESM notifications if phase is active (ESM trigger has phase set)
    }
}

fun schedulePhaseChanges(context: Context, studyStartTimestamp: Long, phases: List<ExperimentalGroupPhase>?) {
    if (phases == null) return

    // schedule an alarm for each phase change so that it can be adapted
    for (phase in phases) {
        // nothing to do for the first day
        if (phase.fromDay == 0) continue

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, ChangePhaseReceiver::class.java)
        intent.putExtra("phaseJson", Json.encodeToString(phase))

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val triggerTimestamp = studyStartTimestamp + TimeUnit.DAYS.toMillis(phase.fromDay.toLong())

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerTimestamp,
            pendingIntent
        )
    }
}