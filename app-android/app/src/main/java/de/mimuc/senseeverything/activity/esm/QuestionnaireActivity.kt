@file:OptIn(ExperimentalMaterial3Api::class)

package de.mimuc.senseeverything.activity.esm

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.ema.emptyQuestionnaire
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.QuestionnaireDataRepository
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.db.models.PendingQuestionnaireStatus
import de.mimuc.senseeverything.helpers.QuestionnaireRuleEvaluator
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.esm.clearReminderNotification
import de.mimuc.senseeverything.workers.enqueueQuestionnaireUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class QuestionnaireActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppandroidTheme {
                QuestionnaireView()
            }
        }
    }

    companion object {
        const val INTENT_PENDING_QUESTIONNAIRE_ID = "pendingQuestionnaireId"
        const val INTENT_TRIGGER_ID = "triggerId"
        const val INTENT_QUESTIONNAIRE = "questionnaire"
    }
}

@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase,
    private val dataRepository: QuestionnaireDataRepository
) : AndroidViewModel(application) {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    private val _questionnaire = MutableStateFlow(emptyQuestionnaire())
    val questionnaire: StateFlow<FullQuestionnaire> get() = _questionnaire

    private val _elementValues = MutableStateFlow<Map<Int, ElementValue>>(emptyMap())
    val elementValues: StateFlow<Map<Int, ElementValue>> get() = _elementValues

    private val _textReplacements = MutableStateFlow<Map<String, String>>(emptyMap())
    val textReplacements: StateFlow<Map<String, String>> = _textReplacements.asStateFlow()

    private lateinit var pendingQuestionnaireId: UUID

    private val _pendingQuestionnaire = MutableStateFlow<PendingQuestionnaire?>(null)
    val pendingQuestionnaire: StateFlow<PendingQuestionnaire?> = _pendingQuestionnaire.asStateFlow()

    // Flag to track if this instance is completing the questionnaire locally
    // Prevents the Flow observer from auto-closing when this instance completes
    private var isCompletingLocally = false

    init {
        _isLoading.value = true
    }

    fun loadQuestionnaire(context: Context) {
        val activity = (context as? Activity)
        val intent = activity?.intent
        if (intent != null) {
            loadFromIntent(activity, intent)
        }
    }

    private fun loadFromIntent(activity: Activity?, intent: Intent) {
        val action: String? = intent.action
        val data: Uri? = intent.data

        if (action == Intent.ACTION_VIEW) {
            handleDeepLinkFinalization(data, activity)
        } else {
            handleNormalLoad(intent, activity)
        }
    }

    /**
     * Handles normal questionnaire loading (not from deep link).
     */
    private fun handleNormalLoad(intent: Intent, activity: Activity?) {
        // Extract and validate pending questionnaire ID
        val pqId = intent.getStringExtra(QuestionnaireActivity.INTENT_PENDING_QUESTIONNAIRE_ID)
            ?.let { UUID.fromString(it) }

        if (pqId == null) {
            viewModelScope.launch {
                close(activity, CloseReason.PendingQuestionnaireNotFound)
            }
            return
        }

        pendingQuestionnaireId = pqId

        // Determine how to load the questionnaire
        val json = intent.getStringExtra(QuestionnaireActivity.INTENT_QUESTIONNAIRE)
        if (json != null) {
            handleLoadFromQuestionnaire(json, pqId, activity)
        } else {
            val triggerId = intent.getIntExtra(QuestionnaireActivity.INTENT_TRIGGER_ID, -1)
            if (triggerId != -1) {
                handleLoadFromTriggerId(triggerId, pqId, activity)
            } else {
                // No questionnaire or trigger ID, just observe pending questionnaire
                startQuestionnaireObservation(pqId, activity)
            }
        }
    }

    /**
     * Handles loading when full questionnaire JSON is provided in the intent.
     */
    private fun handleLoadFromQuestionnaire(json: String, pqId: UUID, activity: Activity?) {
        val loaded = fullQuestionnaireJson.decodeFromString<FullQuestionnaire>(json)
        _questionnaire.value = loaded
        WHALELog.i("Questionnaire", "Loaded questionnaire from intent: ${loaded.questionnaire.name}")

        startQuestionnaireObservation(pqId, activity)
    }

    /**
     * Handles loading when only trigger ID is provided in the intent.
     * Finds the questionnaire by searching for the trigger ID in available questionnaires.
     */
    private fun handleLoadFromTriggerId(triggerId: Int, pqId: UUID, activity: Activity?) {
        viewModelScope.launch {
            val questionnaires = dataStoreManager.questionnairesFlow.first()
            val questionnaire = questionnaires.find { it.triggers.any { it.id == triggerId } }

            if (questionnaire == null) {
                close(activity, CloseReason.PendingQuestionnaireNotFound)
                return@launch
            }

            _questionnaire.value = questionnaire
            WHALELog.i(
                "Questionnaire",
                "Loaded questionnaire by trigger id: $triggerId, name: ${questionnaire.questionnaire.name}"
            )
        }

        startQuestionnaireObservation(pqId, activity)
    }

    /**
     * Starts observing the pending questionnaire and initializes the UI when loaded.
     */
    private fun startQuestionnaireObservation(pqId: UUID, activity: Activity?) {
        WHALELog.i(
            "Questionnaire",
            "Found pending questionnaire id, will remove if saved: $pqId"
        )

        observePendingQuestionnaire(pqId, activity) { _ ->
            loadFromPendingQuestionnaire()
            _textReplacements.value = dataRepository.getTextReplacementsForPendingQuestionnaire(pqId)
            _isLoading.value = false
        }
    }

    /**
     * Observes the pending questionnaire for status changes and automatically closes
     * this activity if another instance completes it.
     *
     * @param pqId The pending questionnaire UUID to observe
     * @param activity The activity to close if needed
     * @param onFirstLoad Callback invoked on first successful load of the questionnaire
     */
    private fun observePendingQuestionnaire(
        pqId: UUID,
        activity: Activity?,
        onFirstLoad: suspend (PendingQuestionnaire) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            database.pendingQuestionnaireDao()?.getByIdFlow(pqId)
                ?.takeWhile { !isCompletingLocally }
                ?.collect { updatedPendingQuestionnaire ->
                    val previousStatus = _pendingQuestionnaire.value?.status
                    _pendingQuestionnaire.value = updatedPendingQuestionnaire

                    if (updatedPendingQuestionnaire == null) {
                        close(activity, CloseReason.PendingQuestionnaireNotFound)
                        return@collect
                    }

                    // If another instance completed this questionnaire, close this instance
                    if (updatedPendingQuestionnaire.status == PendingQuestionnaireStatus.COMPLETED
                        && previousStatus != PendingQuestionnaireStatus.COMPLETED
                        && !isCompletingLocally) {
                        WHALELog.i("Questionnaire", "Questionnaire was completed by another instance, closing this one")
                        close(activity, CloseReason.CompletedAnotherInstance)
                        return@collect
                    }

                    // First time loading - initialize the questionnaire
                    if (previousStatus == null) {
                        if (updatedPendingQuestionnaire.status == PendingQuestionnaireStatus.COMPLETED) {
                            close(activity, CloseReason.AlreadyCompleted)
                            return@collect
                        }

                        // Call the initialization callback
                        onFirstLoad(updatedPendingQuestionnaire)
                    }
                }
        }
    }

    /**
     * Handles deep link finalization from external questionnaires.
     * Called when the user returns from an external questionnaire via deep link.
     */
    private fun handleDeepLinkFinalization(data: Uri?, activity: Activity?) {
        if (data != null && data.path != null && data.path!!.startsWith("/finalize")) {
            WHALELog.d("Questionnaire", "Called finalization deep link")

            val finalizePendingQuestionnaireId = data.getQueryParameter("id")
            if (finalizePendingQuestionnaireId == null) {
                viewModelScope.launch {
                    close(
                        activity,
                        CloseReason.PendingQuestionnaireNotSet
                    )
                }
                return
            }

            val pqId = UUID.fromString(finalizePendingQuestionnaireId)
            pendingQuestionnaireId = pqId

            // Start observing the pending questionnaire for changes
            observePendingQuestionnaire(pqId, activity) { pq ->
                // Load questionnaire JSON from pending questionnaire (needed for upload)
                _questionnaire.value = fullQuestionnaireJson.decodeFromString<FullQuestionnaire>(pq.questionnaireJson)

                WHALELog.i(
                    "Questionnaire",
                    "Finalizing questionnaire $pendingQuestionnaireId from deep link"
                )

                // Set flag before saving to prevent self-triggered auto-close
                isCompletingLocally = true

                // Save the questionnaire (use safe call to avoid crash if activity is null)
                activity?.let { saveQuestionnaire(it) }
            }
        }
    }

    enum class CloseReason {
        PendingQuestionnaireNotSet,
        PendingQuestionnaireNotFound,
        AlreadyCompleted,
        Completed,
        CompletedAnotherInstance
    }

    private suspend fun close(activity: Activity?, reason: CloseReason) {
        if (activity != null) {
            withContext(Dispatchers.Main) {
                when (reason) {
                    CloseReason.PendingQuestionnaireNotSet -> WHALELog.e("Questionnaire", "Pending questionnaire ID was not set")
                    CloseReason.PendingQuestionnaireNotFound, CloseReason.AlreadyCompleted, CloseReason.CompletedAnotherInstance -> WHALELog.w(
                        "Questionnaire",
                        "Finishing questionnaire activity, reason: $reason, pending questionnaire id: $pendingQuestionnaireId"
                    )
                    CloseReason.Completed -> WHALELog.i(
                        "Questionnaire",
                        "Completed questionnaire, pending questionnaire id: $pendingQuestionnaireId"
                    )
                }

                activity.finishAndRemoveTask()

                if (reason != CloseReason.CompletedAnotherInstance) {
                    val message = when (reason) {
                        CloseReason.PendingQuestionnaireNotFound, CloseReason.PendingQuestionnaireNotSet -> activity.getString(R.string.questionnaire_not_found_toast)
                        CloseReason.AlreadyCompleted -> activity.getString(R.string.questionnaire_already_completed_toast)
                        CloseReason.Completed -> activity.getString(R.string.questionnaire_thank_you_toast)
                        else -> ""
                    }

                    Toast.makeText(
                        activity,
                        message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun saveFromHost(values: Map<Int, ElementValue>, context: Context) {
        WHALELog.i(
            "QuestionnaireActivity",
            "Saving questionnaire received from host, values: $values"
        )

        _elementValues.value = values
        saveQuestionnaire(context)
    }

    fun stepChanged(page: Int, values: Map<Int, ElementValue>, context: Context) {
        WHALELog.i("QuestionnaireViewModel", "Step changed to page $page")

        viewModelScope.launch(Dispatchers.IO) {
            val pq = _pendingQuestionnaire.value
            if (pq != null) {
                pq.update(database, answerValues(values), page)
            }
        }
    }

    fun saveQuestionnaire(context: Context) {
        WHALELog.i("Questionnaire", "Saving questionnaire")

        viewModelScope.launch {
            val studyId = dataStoreManager.studyIdFlow.first()
            val token = dataStoreManager.tokenFlow.first()

            val pendingQuestionnaire = _pendingQuestionnaire.value
            if (pendingQuestionnaire == null) {
                WHALELog.e("Questionnaire", "No pending questionnaire to save to")
                return@launch
            }

            // Set flag before marking completed to prevent self-triggered auto-close
            isCompletingLocally = true

            withContext(Dispatchers.IO) {
                pendingQuestionnaire.markCompleted(database, answerValues(elementValues.value))
                clearReminderNotification(context, database, pendingQuestionnaireId)
            }

            // schedule to upload answers
            WHALELog.d("Questionnaire", "Answers: " + makeAnswerJsonArray())
            enqueueQuestionnaireUploadWorker(
                context,
                makeAnswerJsonArray(),
                questionnaire.value.questionnaire.id,
                studyId,
                token,
                pendingQuestionnaireId
            )

            WHALELog.i("Questionnaire", "Scheduled questionnaire upload worker")

            val rules = questionnaire.value.questionnaire.rules
            if (rules != null) {
                val actions = QuestionnaireRuleEvaluator(rules).evaluate(elementValues.value)
                WHALELog.i("Questionnaire", "Evaluated rules, got actions: $actions")
                QuestionnaireRuleEvaluator.handleActions(context, actions.flatMap { it.value }, pendingQuestionnaire)
            }

            close(context as? Activity, CloseReason.Completed)
        }
    }

    private fun makeAnswerJsonArray(): String {
        return ElementValue.valueMapToJson(answerValues(_elementValues.value)).toString()
    }

    private fun answerValues(values: Map<Int, ElementValue>): Map<Int, ElementValue> {
        return values.filter { it -> it.value.isAnswer }
    }

    private fun loadFromPendingQuestionnaire() {
        val pq = _pendingQuestionnaire.value
        if (pq != null) {
            _elementValues.value = ElementValue.valueMapFromJson(JSONArray(pq.elementValuesJson ?: "[]"))
            WHALELog.i("Questionnaire", "Loaded pending questionnaire values: ${_elementValues.value}")
        } else {
            WHALELog.w("Questionnaire", "No pending questionnaire to load from")
        }
    }
}

@Composable
fun QuestionnaireView(
    viewModel: QuestionnaireViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    modifier: Modifier = Modifier
) {
    val isLoading = viewModel.isLoading.collectAsState()
    val questionnaire = viewModel.questionnaire.collectAsState()
    val initialValues = viewModel.elementValues.collectAsState()
    val replacements = viewModel.textReplacements.collectAsState()
    val pendingQuestionnaire = viewModel.pendingQuestionnaire.collectAsState()

    val context = LocalContext.current

    LaunchedEffect(key1 = true) {
        viewModel.loadQuestionnaire(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ), title = {
                    Text(questionnaire.value.questionnaire.name)
                })
        }) { innerPadding ->
        Column(
            modifier = modifier
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (isLoading.value) {
                Text("Loading...")
            } else {
                QuestionnaireHost(
                    questionnaire = questionnaire.value,
                    textReplacements = replacements.value,
                    onSave = { items ->
                        viewModel.saveFromHost(items, context)
                    },
                    onStepChanged = { page, values ->
                        viewModel.stepChanged(page, values, context)
                    },
                    initialValues = initialValues.value,
                    pendingQuestionnaire = pendingQuestionnaire.value
                )
            }
        }
    }
}
