package de.mimuc.senseeverything.service.floatingWidget

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.lifecycle.LifecycleService
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import dagger.hilt.android.AndroidEntryPoint
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.ema.EMAFloatingWidgetNotificationTrigger
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.ema.QuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.makeTriggerFromJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.NotificationTriggerStatus
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.sensor.implementation.InteractionLogSensor
import de.mimuc.senseeverything.service.LogService
import de.mimuc.senseeverything.service.esm.FloatingWidgetNotificationScheduler
import de.mimuc.senseeverything.workers.enqueueQuestionnaireUploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class NotificationTriggerFloatingWidgetService : LifecycleService(), SavedStateRegistryOwner {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    internal enum class InteractionLogType {
        Asked,
        Start,
        End,
        Confirm,
        ConfirmAnotherInteraction,
        ConfirmSameInteraction,
        NoInteraction,
        NotAskedAlreadyDisplayedInBucket,
        NotAskedNotDisplayedInBucket
    }

    private var windowManager: WindowManager? = null
    private var floatingWidgetComposeView: FloatingWidgetComposeView? = null
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var currentQuestionnaire: FullQuestionnaire? = null
    private var currentNotificationTrigger: NotificationTrigger? = null
    private var currentQuestionnaireTrigger: EMAFloatingWidgetNotificationTrigger? = null

    private val TAG = "NotificationTriggerFloatingWidgetService"

    private var logServiceMessenger: Messenger? = null

    @Inject
    lateinit var dataStore: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()

        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)

        // bind to LogService
        val intent = Intent(this, LogService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        scope.launch {
            try {
                // Load the trigger from database
                currentNotificationTrigger = FloatingWidgetNotificationScheduler.getLatestValidTriggerForTime(
                    Calendar.getInstance(), database
                )

                if (currentNotificationTrigger == null) {
                    Log.i(TAG, "No valid trigger found")
                    stopSelf()
                    return@launch
                }

                try {
                    currentQuestionnaireTrigger = makeTriggerFromJson(JSONObject(currentNotificationTrigger!!.triggerJson)) as EMAFloatingWidgetNotificationTrigger?
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse questionnaire trigger JSON: ${currentNotificationTrigger!!.triggerJson}", e)
                }

                // Load questionnaire from DataStoreManager
                val questionnaires = dataStore.questionnairesFlow.first()
                currentQuestionnaire = questionnaires.find { it.questionnaire.id.toLong() == currentNotificationTrigger!!.questionnaireId }

                if (currentQuestionnaire != null && currentNotificationTrigger != null) {
                    // Update trigger status to displayed
                    currentNotificationTrigger?.displayedAt = System.currentTimeMillis()
                    currentNotificationTrigger?.status = NotificationTriggerStatus.Displayed
                    withContext(Dispatchers.IO) {
                        database.notificationTriggerDao()?.update(currentNotificationTrigger!!)
                    }

                    // Render the dynamic questionnaire widget
                    renderDynamicWidget()

                    Log.i(TAG, "Displaying questionnaire: ${currentQuestionnaire!!.questionnaire.name}")
                } else {
                    Log.e(TAG, "Failed to load questionnaire for trigger ${currentNotificationTrigger?.name}: ${currentNotificationTrigger?.questionnaireId}")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading questionnaire", e)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun renderDynamicWidget() {
        if (currentQuestionnaire == null) {
            Log.e(TAG, "Cannot render widget: questionnaire is null")
            return
        }

        try {
            // Create the Compose-based floating widget
            floatingWidgetComposeView = FloatingWidgetComposeView(
                context = this,
                questionnaire = currentQuestionnaire!!,
                triggerUid = currentNotificationTrigger?.uid,
                onComplete = { handleQuestionnaireComplete(it) },
                onDismiss = { handleQuestionnaireDismiss() }
            )

            // Create and add the view to window manager
            val view = floatingWidgetComposeView!!.createView(this)
            val params = FloatingWidgetComposeView.createLayoutParams()

            // Position in top-right corner like the original
            params.gravity = Gravity.TOP or Gravity.END
            params.verticalMargin = 0.075f

            windowManager?.addView(view, params)

            Log.d(TAG, "Dynamic floating widget rendered successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to render dynamic widget", e)
            stopSelf()
        }
    }

    private fun handleQuestionnaireComplete(elementValues: Map<Int, ElementValue>) {
        Log.i(TAG, "Questionnaire completed")

        scope.launch {
            try {
                // Update trigger status
                currentNotificationTrigger?.answeredAt = System.currentTimeMillis()
                currentNotificationTrigger?.status = NotificationTriggerStatus.Answered

                withContext (Dispatchers.IO) {
                    database.notificationTriggerDao()?.update(currentNotificationTrigger!!)
                    Log.i(TAG, "Trigger marked as answered in database")
                }

                scheduleAnswerUpload(elementValues)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling questionnaire completion", e)
            }

            // Stop the service
            stopSelf()
        }
    }

    private suspend fun scheduleAnswerUpload(elementValues: Map<Int, ElementValue>) {
        withContext(Dispatchers.IO) {
            val pendingQuestionnaireId = PendingQuestionnaire.createEntry(
                database,
                dataStore,
                currentQuestionnaireTrigger as QuestionnaireTrigger
            )
            val pendingQuestionnaire =
                database.pendingQuestionnaireDao().getById(pendingQuestionnaireId!!)

            if (pendingQuestionnaire == null) {
                Log.e(TAG, "Failed to find pending questionnaire after creation")
                return@withContext
            }

            if (currentQuestionnaire == null) {
                Log.e(TAG, "Cannot schedule upload: currentQuestionnaire is null")
                return@withContext
            }

            val answers = elementValues.filter { it.value.isAnswer }
            pendingQuestionnaire.markCompleted(database, answers)

            Log.d(TAG, "Pending questionnaire marked as completed with answers: ${pendingQuestionnaire.elementValuesJson}")

            val userToken = dataStore.tokenFlow.first()

            enqueueQuestionnaireUploadWorker(
                this@NotificationTriggerFloatingWidgetService.applicationContext,
                pendingQuestionnaire.elementValuesJson!!,
                currentQuestionnaire!!.questionnaire.id,
                currentQuestionnaire!!.questionnaire.studyId,
                userToken,
                pendingQuestionnaireId
            )

            Log.i(TAG, "Scheduled questionnaire upload with pending ID: $pendingQuestionnaireId")
        }
    }

    private fun handleQuestionnaireDismiss() {
        Log.i(TAG, "Questionnaire dismissed")
        logInteractionMessage(InteractionLogType.NoInteraction)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up the floating widget
        floatingWidgetComposeView?.let { composeView ->
            try {
                val view = composeView.createView(this) // Get the view to remove it
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing view from window manager", e)
            }
            composeView.dispose()
        }

        // Clean up service connections
        if (logServiceMessenger != null) {
            unbindService(connection)
        }

        job.cancel()

        Log.d(TAG, "Service destroyed")
    }

    private fun logInteractionMessage(type: InteractionLogType) {
        when (type) {
            InteractionLogType.Asked -> sendDataToLogService("asked")
            InteractionLogType.Start -> sendDataToLogService("start")
            InteractionLogType.End -> sendDataToLogService("end")
            InteractionLogType.Confirm -> sendDataToLogService("confirm")
            InteractionLogType.ConfirmAnotherInteraction -> sendDataToLogService("confirmAnotherInteraction")
            InteractionLogType.ConfirmSameInteraction -> sendDataToLogService("confirmSameInteraction")
            InteractionLogType.NoInteraction -> sendDataToLogService("noInteraction")
            InteractionLogType.NotAskedAlreadyDisplayedInBucket -> sendDataToLogService("notAskedAlreadyDisplayedInBucket")
            InteractionLogType.NotAskedNotDisplayedInBucket -> sendDataToLogService("notAskedNotDisplayedInBucket")
        }
    }

    private val connection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            logServiceMessenger = Messenger(service)
            logInteractionMessage(InteractionLogType.Asked) // needs to be logged here because otherwise we have a race condition
        }

        override fun onServiceDisconnected(name: ComponentName) {
            logServiceMessenger = null
        }
    }

    private fun sendDataToLogService(message: String) {
        if (logServiceMessenger != null) {
            val msg = Message.obtain(null, LogService.SEND_SENSOR_LOG_DATA)
            val bundle = Bundle()
            bundle.putString("sensorData", message)
            bundle.putString("sensorName", InteractionLogSensor::class.java.name)
            msg.data = bundle

            try {
                logServiceMessenger!!.send(msg)
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to send message to LogService", e)
            }
        }
    }
}
