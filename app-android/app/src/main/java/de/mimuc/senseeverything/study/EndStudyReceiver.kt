package de.mimuc.senseeverything.study

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.StudyState
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.helpers.goAsync
import de.mimuc.senseeverything.workers.enqueueSingleSensorReadingsUploadWorker
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class EndStudyReceiver : BroadcastReceiver() {
    val TAG = "EndStudyReceiver"

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context?, intent: Intent?) = goAsync {
        val applicationContext = context?.applicationContext ?: return@goAsync

        dataStoreManager.saveStudyState(StudyState.ENDED)
        Log.i(TAG, "Marked study as ended")

        runStudyLifecycleCleanup(applicationContext)

        val token = dataStoreManager.tokenFlow.first()
        enqueueSingleSensorReadingsUploadWorker(applicationContext, token, "finalReadingsUpload", false)
        Log.i(TAG, "Scheduled final sensor readings upload")
    }
}

fun scheduleStudyEndAlarm(context: Context, afterDays: Int) {
    val endStudyIntent = Intent(context, EndStudyReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        101,
        endStudyIntent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
    )

    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + TimeUnit.DAYS.toMillis(afterDays.toLong()),
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + TimeUnit.DAYS.toMillis(afterDays.toLong()),
                pendingIntent
            )
        }
    } else {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + TimeUnit.DAYS.toMillis(afterDays.toLong()),
            pendingIntent
        )
    }
}