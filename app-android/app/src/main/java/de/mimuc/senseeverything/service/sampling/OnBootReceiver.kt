package de.mimuc.senseeverything.service.sampling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.StudyState
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.helpers.LogServiceHelper
import de.mimuc.senseeverything.helpers.goAsync
import de.mimuc.senseeverything.helpers.isServiceRunning
import de.mimuc.senseeverything.helpers.scheduleResumeSamplingAlarm
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.LogService
import de.mimuc.senseeverything.service.esm.EsmHandler
import de.mimuc.senseeverything.service.healthcheck.PeriodicServiceHealthcheckReceiver
import de.mimuc.senseeverything.service.healthcheck.ServiceHealthcheck
import de.mimuc.senseeverything.study.reschedulePhaseChanges
import de.mimuc.senseeverything.study.rescheduleStudyEndAlarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OnBootReceiver: BroadcastReceiver() {
    @Inject
    lateinit var dataStoreManager: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    override fun onReceive(context: Context?, intent: Intent?) = goAsync {
        if (context == null) {
            WHALELog.e("BootUpReceiver", "Context is null")
            return@goAsync
        }

        if (intent!!.action == "android.intent.action.BOOT_COMPLETED") {
            WHALELog.i("BootUpReceiver", "Received boot completed intent")

            CoroutineScope(Dispatchers.Main).launch {
                val (studyPaused, studyPausedUntil, studyState) = combine(
                    dataStoreManager.studyPausedFlow,
                    dataStoreManager.studyPausedUntilFlow,
                    dataStoreManager.studyStateFlow
                ) { studyPaused, studyPausedUntil, studyState ->
                    Triple(studyPaused, studyPausedUntil, studyState)
                }.first()

                val currentContext = context.applicationContext
                WHALELog.i("BootUpReceiver", "Study paused: $studyPaused, paused until: $studyPausedUntil")

                if (studyState == StudyState.RUNNING && (!studyPaused || studyPausedUntil < System.currentTimeMillis() || studyPausedUntil == -1L)) {
                    // study can start right away
                    WHALELog.i("BootUpReceiver", "Study is not paused, starting sampling")
                    startSampling(currentContext)

                    // all alarms need to be rescheduled after reboot
                    rescheduleAlarms(currentContext, database, dataStoreManager)
                } else if (studyState == StudyState.RUNNING) {
                    // study is paused, so we don't start sampling
                    WHALELog.i("BootUpReceiver", "Study is paused until $studyPausedUntil, starting alarm manager to resume study")
                    scheduleResumeSamplingAlarm(currentContext, studyPausedUntil)
                } else {
                    WHALELog.i("BootUpReceiver", "Study has ended, not starting sampling")
                    // todo: disable BootUpReceiver
                }
            }
        } else {
            WHALELog.e("BootUpReceiver", "invalid intent action: " + intent.action)
        }
    }

    private fun startSampling(context: Context) {
        if (!isServiceRunning(context, LogService::class.java)) {
            LogServiceHelper.startLogService(context)
        }
    }
}

private suspend fun rescheduleAlarms(context: Context, database: AppDatabase, dataStoreManager: DataStoreManager) {
    rescheduleStudyEndAlarm(context, database)
    reschedulePhaseChanges(context, database, dataStoreManager)
    EsmHandler.rescheduleQuestionnaires(context, dataStoreManager, database)

    // Schedule periodic healthcheck
    PeriodicServiceHealthcheckReceiver.schedule(context)

    // Run immediate healthcheck after boot
    ServiceHealthcheck.checkServices(context)
}