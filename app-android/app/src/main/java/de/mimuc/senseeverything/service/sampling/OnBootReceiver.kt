package de.mimuc.senseeverything.service.sampling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.StudyState
import de.mimuc.senseeverything.helpers.goAsync
import de.mimuc.senseeverything.helpers.scheduleResumeSamplingAlarm
import de.mimuc.senseeverything.service.SEApplicationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OnBootReceiver: BroadcastReceiver() {
    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onReceive(context: Context?, intent: Intent?) = goAsync {
        if (context == null) {
            Log.e("BootUpReceiver", "Context is null")
            return@goAsync
        }

        if (intent!!.action == "android.intent.action.BOOT_COMPLETED") {
            Log.d("BootUpReceiver", "Received boot completed intent")

            CoroutineScope(Dispatchers.Main).launch {
                combine(
                    dataStoreManager.studyPausedFlow,
                    dataStoreManager.studyPausedUntilFlow,
                    dataStoreManager.studyStateFlow
                ) { studyPaused, studyPausedUntil, studyState ->
                    Triple(studyPaused, studyPausedUntil, studyState)
                }.collect { (studyPaused, studyPausedUntil, studyState) ->
                    val currentContext = context.applicationContext
                    Log.d("BootUpReceiver", "Study paused: $studyPaused, paused until: $studyPausedUntil")

                    if (studyState == StudyState.RUNNING && (!studyPaused || studyPausedUntil < System.currentTimeMillis() || studyPausedUntil == -1L)) {
                        // study can start right away
                        Log.d("BootUpReceiver", "Study is not paused, starting sampling")
                        startSampling(currentContext)
                    } else if (studyState == StudyState.RUNNING) {
                        // study is paused, so we don't start sampling
                        Log.d("BootUpReceiver", "Study is paused until $studyPausedUntil, starting alarm manager to resume study")
                        scheduleResumeSamplingAlarm(currentContext, studyPausedUntil)
                    } else {
                        Log.d("BootUpReceiver", "Study has ended, not starting sampling")
                        // todo: disable BootUpReceiver
                    }
                }
            }
        } else {
            Log.e("BootUpReceiver", "invalid intent action: " + intent.action)
        }
    }

    private fun startSampling(context: Context) {
        val samplingManager = SEApplicationController.getInstance().samplingManager
        if (!samplingManager.isRunning(context)) {
            samplingManager.startSampling(context)
        }
    }
}