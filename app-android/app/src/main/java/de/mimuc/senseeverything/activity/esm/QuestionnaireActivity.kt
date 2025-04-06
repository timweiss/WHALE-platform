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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.api.model.CheckboxGroupElement
import de.mimuc.senseeverything.api.model.CheckboxGroupValue
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.FullQuestionnaire
import de.mimuc.senseeverything.api.model.RadioGroupElement
import de.mimuc.senseeverything.api.model.RadioGroupValue
import de.mimuc.senseeverything.api.model.SliderElement
import de.mimuc.senseeverything.api.model.SliderValue
import de.mimuc.senseeverything.api.model.TextEntryElement
import de.mimuc.senseeverything.api.model.TextEntryValue
import de.mimuc.senseeverything.api.model.TextViewElement
import de.mimuc.senseeverything.api.model.emptyQuestionnaire
import de.mimuc.senseeverything.api.model.emptyValueForElement
import de.mimuc.senseeverything.api.model.makeFullQuestionnaireFromJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
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
class QuestionnaireViewModel  @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase
) : AndroidViewModel(application) {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    private val _questionnaire = MutableStateFlow(emptyQuestionnaire())
    val questionnaire: StateFlow<FullQuestionnaire> get() = _questionnaire

    private val _activeStep = MutableStateFlow(1)
    val activeStep: StateFlow<Int> get() = _activeStep

    private val _elementValues = MutableStateFlow<Map<Int, ElementValue>>(emptyMap())
    val elementValues: StateFlow<Map<Int, ElementValue>> = _elementValues

    private var pendingQuestionnaireId = -1

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
            _elementValues.value = mutableMapOf()

            _questionnaire.value = loaded
            _isLoading.value = false

            for (element in loaded.elements) {
                setElementValue(element.id, emptyValueForElement(element))
            }
        } else {
            val triggerId = intent.getIntExtra("triggerId", -1)
            if (triggerId != -1) {
                viewModelScope.launch {
                    dataStoreManager.questionnairesFlow.collect { questionnaires ->
                        val questionnaire = questionnaires.find { it.triggers.any { it.id == triggerId } }
                        if (questionnaire == null) {
                            _isLoading.value = false
                        } else {
                            _elementValues.value = mutableMapOf()

                            _questionnaire.value = questionnaire
                            _isLoading.value = false

                            for (element in questionnaire.elements) {
                                setElementValue(element.id, emptyValueForElement(element))
                            }
                        }
                    }
                }
            }
        }

        pendingQuestionnaireId = intent.getIntExtra("pendingQuestionnaireId", -1)
    }

    fun setElementValue(elementId: Int, value: ElementValue) {
        _elementValues.value = _elementValues.value.toMutableMap().apply {
            put(elementId, value)
        }
    }

    fun nextStep() {
        _activeStep.value++
    }

    fun previousStep() {
        _activeStep.value--
    }

    fun saveQuestionnaire(context: Context) {
        Log.d("Questionnaire", "Save questionnaire")

        viewModelScope.launch {
            combine(
                dataStoreManager.studyIdFlow,
                dataStoreManager.tokenFlow
            ) { studyId, token ->
                // schedule to upload answers
                Log.d("Questionnaire", "Answers: " + makeAnswerJsonArray())
                enqueueQuestionnaireUploadWorker(context, makeAnswerJsonArray(), questionnaire.value.questionnaire.id, studyId, token)

                // remove pending questionnaire
                withContext(Dispatchers.IO) {
                    if (pendingQuestionnaireId != -1) {
                        database.pendingQuestionnaireDao()?.deleteById(pendingQuestionnaireId)
                    }
                }

                // pop activity
                val activity = (context as? Activity)
                activity?.finish()
            }.collect {}
        }
    }

    private fun canHaveAnswer(elementId: Int): Boolean {
        val element = questionnaire.value.elements.find { it.id == elementId }
        if (element != null) {
            return element.type != "text_view"
        }
        return false
    }

    private fun makeAnswerJsonArray(): String {
        val jsonArray = _elementValues.value.values
            .filter { canHaveAnswer(it.elementId) }
            .map { it.toJson() }
        return jsonArray.toString()
    }
}

@Composable
fun QuestionnaireView(viewModel: QuestionnaireViewModel = androidx.lifecycle.viewmodel.compose.viewModel(), modifier: Modifier = Modifier) {
    val isLoading = viewModel.isLoading.collectAsState()
    val questionnaire = viewModel.questionnaire.collectAsState()

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
                ),
                title = {
                    Text(questionnaire.value.questionnaire.name)
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = modifier
            .padding(innerPadding)
            .padding(16.dp)) {
            if (isLoading.value) {
                Text("Loading...")
            } else if (questionnaire.value.elements.isEmpty()) {
                Text("No questions have been provided. This is likely a mistake of the study creator.")
            } else {
                val maxStep = questionnaire.value.elements.maxOf { it.step }
                val currentStep by viewModel.activeStep.collectAsState()
                val currentElements = remember(currentStep) { questionnaire.value.elements.filter { it.step == currentStep }.sortedBy { it.position } }
                val answerValues = viewModel.elementValues.collectAsState()

                LazyColumn {
                    items(items = currentElements, key = { item -> item.id }) { element ->
                        val elementValue = answerValues.value[element.id]
                        Log.d("Questionnaire", "Element value: $elementValue")
                        when (element.type) {
                            "text_view" -> {
                                TextViewElementComponent(element = element as TextViewElement)
                            }
                            "radio_group" -> {
                                RadioGroupElementComponent(element = element as RadioGroupElement, value = (elementValue as RadioGroupValue).value,
                                    onValueChange = { newValue ->
                                        viewModel.setElementValue(element.id, RadioGroupValue(element.id, element.name, newValue))
                                    }
                                )
                            }
                            "checkbox_group" -> {
                                CheckboxGroupElementComponent(element = element as CheckboxGroupElement, value = (elementValue as CheckboxGroupValue).values,
                                    onValueChange = { newValue ->
                                        viewModel.setElementValue(element.id, CheckboxGroupValue(element.id, element.name, newValue))
                                    })
                            }
                            "slider" -> {
                                SliderElementComponent(element = element as SliderElement, value = (elementValue as SliderValue).value,
                                    onValueChange = { newValue ->
                                        viewModel.setElementValue(element.id, SliderValue(element.id, element.name, newValue))
                                    })
                            }
                            "text_entry" -> {
                                TextEntryElementComponent(element = element as TextEntryElement, value = (elementValue as TextEntryValue).value,
                                    onValueChange = { newValue ->
                                        viewModel.setElementValue(element.id, TextEntryValue(element.id, element.name, newValue))
                                    })
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row {
                    TextButton(onClick = { viewModel.previousStep() }, enabled = currentStep > 1) {
                        Text("Previous")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (currentStep < maxStep) {
                        TextButton(onClick = { viewModel.nextStep() }) {
                            Text("Next")
                        }
                    } else {
                        TextButton(onClick = { viewModel.saveQuestionnaire(context) }) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}