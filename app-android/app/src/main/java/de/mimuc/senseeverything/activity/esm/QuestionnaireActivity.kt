@file:OptIn(ExperimentalMaterial3Api::class)

package de.mimuc.senseeverything.activity.esm

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import de.mimuc.senseeverything.api.model.FullQuestionnaire
import de.mimuc.senseeverything.api.model.emptyQuestionnaire
import de.mimuc.senseeverything.api.model.makeFullQuestionnaireFromJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.workers.enqueueQuestionnaireUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
}

@HiltViewModel
class QuestionnaireViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase
) : AndroidViewModel(application) {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    private val _questionnaire = MutableStateFlow(emptyQuestionnaire())
    val questionnaire: StateFlow<FullQuestionnaire> get() = _questionnaire

    private val _elementValues = MutableStateFlow<Map<Int, ElementValue>>(emptyMap())
    val elementValues: StateFlow<Map<Int, ElementValue>> get() = _elementValues

    private var pendingQuestionnaireId: Long = -1

    private var pendingQuestionnaire: PendingQuestionnaire? = null

    init {
        _isLoading.value = true
    }

    fun loadQuestionnaire(context: Context) {
        val activity = (context as? Activity)
        val intent = activity?.intent
        if (intent != null) {
            loadFromIntent(intent)
        }
    }

    private fun loadFromIntent(intent: Intent) {
        val json = intent.getStringExtra("questionnaire")
        if (json != null) {
            val loaded = makeFullQuestionnaireFromJson(JSONObject(json))
            _questionnaire.value = loaded
            _isLoading.value = false
            Log.i("Questionnaire", "Loaded questionnaire from intent: ${loaded.questionnaire.name}")
        } else {
            val triggerId = intent.getIntExtra("triggerId", -1)
            if (triggerId != -1) {
                viewModelScope.launch {
                    dataStoreManager.questionnairesFlow.collect { questionnaires ->
                        val questionnaire =
                            questionnaires.find { it.triggers.any { it.id == triggerId } }
                        if (questionnaire == null) {
                            _isLoading.value = false
                        } else {
                            _questionnaire.value = questionnaire
                            _isLoading.value = false
                        }
                    }
                }
                Log.i(
                    "Questionnaire",
                    "Loaded questionnaire by trigger id: $triggerId, name: ${_questionnaire.value.questionnaire.name}"
                )
            }
        }

        pendingQuestionnaireId = intent.getLongExtra("pendingQuestionnaireId", -1)
        if (pendingQuestionnaireId != -1L) {
            Log.i(
                "Questionnaire",
                "Found pending questionnaire id, will remove if saved: $pendingQuestionnaireId"
            )

            viewModelScope.launch(Dispatchers.IO) {
                pendingQuestionnaire = database.pendingQuestionnaireDao()?.getById(pendingQuestionnaireId)
                loadFromPendingQuestionnaire()
            }
        }
    }

    fun saveFromHost(values: Map<Int, ElementValue>, context: Context) {
        Log.i(
            "QuestionnaireActivity",
            "Saving questionnaire received from host, values: $values"
        )

        _elementValues.value = values
        saveQuestionnaire(context)
    }

    fun stepChanged(page: Int, values: Map<Int, ElementValue>, context: Context) {
        Log.d("QuestionnaireViewModel", "Step changed to page $page with values: $values")

        viewModelScope.launch(Dispatchers.IO) {
            if (pendingQuestionnaire != null) {
                pendingQuestionnaire?.update(database, answerValues(values), page)
            }
        }
    }

    fun saveQuestionnaire(context: Context) {
        Log.i("Questionnaire", "Saving questionnaire")

        viewModelScope.launch {
            combine(
                dataStoreManager.studyIdFlow, dataStoreManager.tokenFlow
            ) { studyId, token ->
                // schedule to upload answers
                Log.d("Questionnaire", "Answers: " + makeAnswerJsonArray())
                enqueueQuestionnaireUploadWorker(
                    context,
                    makeAnswerJsonArray(),
                    questionnaire.value.questionnaire.id,
                    studyId,
                    token
                )
                Log.i("Questionnaire", "Scheduled questionnaire upload worker")

                // remove pending questionnaire
                withContext(Dispatchers.IO) {
                    if (pendingQuestionnaireId != -1L) {
                        database.pendingQuestionnaireDao()?.deleteById(pendingQuestionnaireId)
                        Log.i(
                            "Questionnaire",
                            "Removed pending questionnaire with id: $pendingQuestionnaireId"
                        )
                    }
                }

                // pop activity
                val activity = (context as? Activity)
                activity?.finish()

                // show thank you toast
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.questionnaire_thank_you_toast),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }.collect {}
        }
    }

    private fun makeAnswerJsonArray(): String {
        return ElementValue.valueMapToJson(answerValues(_elementValues.value)).toString()
    }

    private fun answerValues(values: Map<Int, ElementValue>): Map<Int, ElementValue> {
        return values.filter { it -> it.value.isAnswer }
    }

    private fun loadFromPendingQuestionnaire() {
        if (pendingQuestionnaire != null) {
            _elementValues.value = ElementValue.valueMapFromJson(JSONObject(pendingQuestionnaire!!.elementValuesJson ?: "{}"))
            Log.i("Questionnaire", "Loaded pending questionnaire values: ${_elementValues.value}")
        } else {
            Log.w("Questionnaire", "No pending questionnaire to load from")
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
                QuestionnaireHost(questionnaire.value, onSave = { items ->
                    viewModel.saveFromHost(items, context)
                }, onStepChanged = { page, values ->
                    viewModel.stepChanged(page, values, context)
                }, initialValues = initialValues.value)
            }
        }
    }
}
