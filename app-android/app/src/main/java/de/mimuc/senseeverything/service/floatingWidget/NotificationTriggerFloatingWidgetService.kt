package de.mimuc.senseeverything.service.floatingWidget

import android.content.Intent
import android.os.IBinder
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
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.QuestionnaireDataRepository
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.NotificationTriggerStatus
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.helpers.QuestionnaireRuleEvaluator
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.esm.FloatingWidgetNotificationScheduler
import de.mimuc.senseeverything.workers.enqueueQuestionnaireUploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

@AndroidEntryPoint
class NotificationTriggerFloatingWidgetService : LifecycleService(), SavedStateRegistryOwner {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private var windowManager: WindowManager? = null
    private var floatingWidgetComposeView: FloatingWidgetComposeView? = null
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var currentQuestionnaire: FullQuestionnaire? = null
    private var currentNotificationTrigger: NotificationTrigger? = null
    private var currentQuestionnaireTrigger: EMAFloatingWidgetNotificationTrigger? = null
    private var pendingQuestionnaire: PendingQuestionnaire? = null
    private var textReplacements = emptyMap<String, String>()

    private val TAG = "NotificationTriggerFloatingWidgetService"

    @Inject
    lateinit var dataStore: DataStoreManager

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var questionnaireData: QuestionnaireDataRepository

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()

        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)

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

                val currentNotificationTrigger = currentNotificationTrigger
                if (currentNotificationTrigger == null) {
                    WHALELog.i(TAG, "No valid trigger found for this unlock, stopping service")
                    stopSelf()
                    return@launch
                }

                try {
                    currentQuestionnaireTrigger = fullQuestionnaireJson.decodeFromString(currentNotificationTrigger.triggerJson) as EMAFloatingWidgetNotificationTrigger?
                } catch (e: Exception) {
                    WHALELog.e(TAG, "Failed to parse questionnaire trigger JSON: ${currentNotificationTrigger.triggerJson}", e)
                }

                // Load questionnaire from DataStoreManager
                val questionnaires = dataStore.questionnairesFlow.first()
                currentQuestionnaire = questionnaires.find { it.questionnaire.id.toLong() == currentNotificationTrigger.questionnaireId }

                if (currentQuestionnaire != null) {
                    // Update trigger status to displayed
                    currentNotificationTrigger.displayedAt = System.currentTimeMillis()
                    currentNotificationTrigger.updatedAt = System.currentTimeMillis()
                    currentNotificationTrigger.status = NotificationTriggerStatus.Displayed
                    withContext(Dispatchers.IO) {
                        database.notificationTriggerDao()?.update(currentNotificationTrigger)

                        val pendingQuestionnaireId = PendingQuestionnaire.createEntry(
                            database,
                            dataStore,
                            currentQuestionnaireTrigger as QuestionnaireTrigger,
                            currentNotificationTrigger.uid
                        )?.uid
                        pendingQuestionnaire =
                            database.pendingQuestionnaireDao().getById(pendingQuestionnaireId!!)

                        textReplacements = questionnaireData.getTextReplacementsForPendingQuestionnaire(
                            pendingQuestionnaireId
                        )
                    }

                    // Render the dynamic questionnaire widget
                    renderDynamicWidget()

                    WHALELog.i(TAG, "Displaying questionnaire: ${currentQuestionnaire!!.questionnaire.name} for trigger ${currentNotificationTrigger.uid}")
                } else {
                    WHALELog.e(TAG, "Failed to load questionnaire for trigger ${currentNotificationTrigger.name}: ${currentNotificationTrigger.questionnaireId}")
                    stopSelf()
                }
            } catch (e: Exception) {
                WHALELog.e(TAG, "Error loading questionnaire", e)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun renderDynamicWidget() {
        if (currentQuestionnaire == null) {
            WHALELog.e(TAG, "Cannot render widget: questionnaire is null")
            return
        }

        try {
            // Create the Compose-based floating widget
            floatingWidgetComposeView = FloatingWidgetComposeView(
                context = this,
                questionnaire = currentQuestionnaire!!,
                notificationTriggerId = currentNotificationTrigger?.uid,
                textReplacements = textReplacements,
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

            WHALELog.i(TAG, "Dynamic floating widget rendered successfully")

        } catch (e: Exception) {
            WHALELog.e(TAG, "Failed to render dynamic widget", e)
            stopSelf()
        }
    }

    private fun handleQuestionnaireComplete(elementValues: Map<Int, ElementValue>) {
        WHALELog.i(TAG, "Questionnaire completed")

        scope.launch {
            try {
                // Update trigger status
                currentNotificationTrigger?.answeredAt = System.currentTimeMillis()
                currentNotificationTrigger?.updatedAt = System.currentTimeMillis()
                currentNotificationTrigger?.status = NotificationTriggerStatus.Answered

                withContext (Dispatchers.IO) {
                    database.notificationTriggerDao()?.update(currentNotificationTrigger!!)
                    WHALELog.i(TAG, "Trigger marked as answered in database")
                }

                val answers = elementValues.filter { it.value.isAnswer }
                scheduleAnswerUpload(answers)
                evaluateRules(answers)
            } catch (e: Exception) {
                WHALELog.e(TAG, "Error handling questionnaire completion", e)
            }

            // Stop the service
            stopSelf()
        }
    }

    private suspend fun scheduleAnswerUpload(answers: Map<Int, ElementValue>) {
        withContext(Dispatchers.IO) {
            val pendingQuestionnaire = pendingQuestionnaire
            if (pendingQuestionnaire == null) {
                WHALELog.e(TAG, "Failed to find pending questionnaire after creation")
                return@withContext
            }

            val currentQuestionnaire = currentQuestionnaire
            if (currentQuestionnaire == null) {
                WHALELog.e(TAG, "Cannot schedule upload: currentQuestionnaire is null")
                return@withContext
            }

            pendingQuestionnaire.markCompleted(database, answers)

            WHALELog.d(TAG, "Pending questionnaire marked as completed with answers: ${pendingQuestionnaire.elementValuesJson}")

            val userToken = dataStore.tokenFlow.first()

            enqueueQuestionnaireUploadWorker(
                this@NotificationTriggerFloatingWidgetService.applicationContext,
                pendingQuestionnaire.elementValuesJson!!,
                currentQuestionnaire.questionnaire.id,
                currentQuestionnaire.questionnaire.studyId,
                userToken,
                pendingQuestionnaire.uid
            )

            WHALELog.i(TAG, "Scheduled questionnaire upload with pending ID: ${pendingQuestionnaire.uid} for trigger ID: ${currentNotificationTrigger?.uid}")
        }
    }

    private fun evaluateRules(answers: Map<Int, ElementValue>) {
        val currentQuestionnaire = currentQuestionnaire
        if (currentQuestionnaire == null) {
            WHALELog.e(TAG, "Cannot evaluate rules: currentQuestionnaire is null")
            return
        }

        if (currentQuestionnaire.questionnaire.rules.isNullOrEmpty()) {
            WHALELog.i(TAG, "No rules to evaluate for this questionnaire")
            return
        }

        val pendingQuestionnaire = pendingQuestionnaire
        if (pendingQuestionnaire == null) {
            WHALELog.e(TAG, "Cannot evaluate rules: pendingQuestionnaire is null")
            return
        }

        val evaluator = QuestionnaireRuleEvaluator(currentQuestionnaire.questionnaire.rules)
        val actions = evaluator.evaluate(answers)

        WHALELog.i(TAG, "Evaluated rules, got actions: $actions")

        QuestionnaireRuleEvaluator.handleActions(this, actions.flatMap { it.value }, pendingQuestionnaire)
    }

    private fun handleQuestionnaireDismiss() {
        WHALELog.i(TAG, "Questionnaire dismissed")
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up the floating widget
        floatingWidgetComposeView?.let { composeView ->
            // Use the getter method to access the attached view
            composeView.getAttachedView()?.let { view ->
                try {
                    windowManager?.removeView(view)
                    WHALELog.i(TAG, "Removed view from window manager")
                } catch (e: Exception) {
                    WHALELog.w(TAG, "Error removing view from window manager", e)
                }
            }
            composeView.dispose()
        }

        job.cancel()

        WHALELog.i(TAG, "Service destroyed")
    }
}
