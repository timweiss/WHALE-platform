package de.mimuc.senseeverything.activity.esm

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.AndroidViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.esm.socialnetwork.SocialNetworkEntryElementComponent
import de.mimuc.senseeverything.activity.esm.socialnetwork.SocialNetworkRatingElementComponent
import de.mimuc.senseeverything.api.model.CheckboxGroupValue
import de.mimuc.senseeverything.api.model.CircumplexValue
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.RadioGroupValue
import de.mimuc.senseeverything.api.model.SliderValue
import de.mimuc.senseeverything.api.model.SocialNetworkEntryValue
import de.mimuc.senseeverything.api.model.SocialNetworkRatingValue
import de.mimuc.senseeverything.api.model.TextEntryValue
import de.mimuc.senseeverything.api.model.TimeInputValue
import de.mimuc.senseeverything.api.model.ema.CheckboxGroupElement
import de.mimuc.senseeverything.api.model.ema.CircumplexElement
import de.mimuc.senseeverything.api.model.ema.ExternalQuestionnaireLinkElement
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.ema.LikertScaleLabelElement
import de.mimuc.senseeverything.api.model.ema.QuestionnaireElement
import de.mimuc.senseeverything.api.model.ema.QuestionnaireElementType
import de.mimuc.senseeverything.api.model.ema.RadioGroupElement
import de.mimuc.senseeverything.api.model.ema.SliderElement
import de.mimuc.senseeverything.api.model.ema.SocialNetworkEntryElement
import de.mimuc.senseeverything.api.model.ema.SocialNetworkRatingElement
import de.mimuc.senseeverything.api.model.ema.TextEntryElement
import de.mimuc.senseeverything.api.model.ema.TextViewElement
import de.mimuc.senseeverything.api.model.ema.TimeInputElement
import de.mimuc.senseeverything.api.model.emptyValueForElement
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = QuestionnaireHostViewModel.Factory::class)
class QuestionnaireHostViewModel @AssistedInject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase,
    @Assisted val questionnaire: FullQuestionnaire,
    @Assisted val onSave: (Map<Int, ElementValue>) -> Unit,
    @Assisted val onStepChanged: (Int, Map<Int, ElementValue>) -> Unit,
    @Assisted val initialValues: Map<Int, ElementValue> = emptyMap(),
) : AndroidViewModel(application) {
    private val _activeStep = MutableStateFlow(1)
    val activeStep: StateFlow<Int> get() = _activeStep

    private val _elementValues = MutableStateFlow<Map<Int, ElementValue>>(emptyMap())
    val elementValues: StateFlow<Map<Int, ElementValue>> = _elementValues

    @AssistedFactory
    interface Factory {
        fun create(
            questionnaire: FullQuestionnaire,
            onSave: (Map<Int, ElementValue>) -> Unit,
            onStepChanged: (Int, Map<Int, ElementValue>) -> Unit,
            initialValues: Map<Int, ElementValue> = emptyMap()
        ): QuestionnaireHostViewModel
    }

    init {
        initializeValues()
    }

    fun setElementValue(elementId: Int, value: ElementValue) {
        _elementValues.value = _elementValues.value.toMutableMap().apply {
            put(elementId, value)
        }
    }

    fun stepElementsAnswered(): Boolean {
        val currentStep = _activeStep.value
        return questionnaire.elements.filter { it.step == currentStep }
            .all { element ->
                _elementValues.value[element.id]?.isAnswered() ?: false
            }
    }

    fun nextStep() {
        _activeStep.value++
        onStepChanged(_activeStep.value, _elementValues.value)
    }

    fun previousStep() {
        _activeStep.value--
        onStepChanged(_activeStep.value, _elementValues.value)
    }

    fun save() {
        WHALELog.d(
            "Questionnaire",
            "Saving questionnaire from host with values: ${_elementValues.value}"
        )
        onSave(_elementValues.value)
    }

    private fun initializeValues() {
        _elementValues.value = mutableMapOf()

        WHALELog.i("QuestionnaireHostViewModel", "Initial values set: ${initialValues.size}")

        for (element in questionnaire.elements) {
            val elementValue =
                if (initialValues.contains(element.id)) initialValues.getValue(element.id) else emptyValueForElement(
                    element
                )
            setElementValue(element.id, elementValue)
        }
    }
}

@Composable
fun ElementSeparator(element: QuestionnaireElement, currentElements: List<QuestionnaireElement>) {
    val previousElement = if (element.position > 1) {
        currentElements.getOrNull(element.position - 2)
    } else null
    if (previousElement != null && previousElement.displayGroup != element.displayGroup) {
        Spacer(modifier = Modifier.padding(10.dp))
    }
}

@Composable
fun QuestionnaireHost(
    questionnaire: FullQuestionnaire,
    textReplacements: Map<String, String>,
    onSave: (Map<Int, ElementValue>) -> Unit,
    onStepChanged: (Int, Map<Int, ElementValue>) -> Unit = { _, _ -> },
    initialValues: Map<Int, ElementValue> = emptyMap(),
    embedded: Boolean = false,
    hostKey: String = "default_host"
) {
    val viewModel =
        hiltViewModel<QuestionnaireHostViewModel, QuestionnaireHostViewModel.Factory>(key = hostKey) { factory ->
            factory.create(questionnaire, onSave, onStepChanged, initialValues)
        }

    if (questionnaire.elements.isEmpty()) {
        Text(stringResource(R.string.questionnaire_empty_error))
    } else {
        val maxStep = questionnaire.elements.maxOf { it.step }
        val currentStep by viewModel.activeStep.collectAsState()
        val currentElements = remember(currentStep) {
            questionnaire.elements.filter { it.step == currentStep }
                .sortedBy { it.position }
        }
        val answerValues = viewModel.elementValues.collectAsState()

        if (embedded) {
            // column layout as we cannot nest LazyColumn inside LazyColumn
            Column {
                for (element in currentElements) {
                    ElementSeparator(element, currentElements)

                    val elementValue = answerValues.value[element.id]
                    QuestionnaireElement(element, elementValue, textReplacements, onValueChange = { id, value ->
                        viewModel.setElementValue(id, value)
                    })
                }

                // Navigation after content
                if (maxStep == 1) {
                    TextButton(onClick = { viewModel.save() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.questionnaire_save))
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { viewModel.previousStep() },
                            enabled = currentStep > 1
                        ) {
                            Text(stringResource(R.string.questionnaire_previous))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (currentStep < maxStep) {
                            TextButton(onClick = { viewModel.nextStep() }) {
                                Text(stringResource(R.string.questionnaire_next))
                            }
                        } else {
                            TextButton(onClick = { viewModel.save() }) {
                                Text(stringResource(R.string.questionnaire_save))
                            }
                        }
                    }
                }
            }
        } else {
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            val context = LocalContext.current

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 56.dp)
                ) {
                    items(items = currentElements, key = { item -> item.id }) { element ->
                        ElementSeparator(element, currentElements)

                        val elementValue = answerValues.value[element.id]
                        QuestionnaireElement(element, elementValue, textReplacements, onValueChange = { id, value ->
                            viewModel.setElementValue(id, value)
                        })
                    }
                }

                // Navigation buttons positioned at bottom of screen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    TextButton(
                        onClick = {
                            viewModel.previousStep()
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        enabled = currentStep > 1
                    ) {
                        Text(stringResource(R.string.questionnaire_previous))
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (currentStep < maxStep) {
                        TextButton(onClick = {
                            if (viewModel.stepElementsAnswered()) {
                                viewModel.nextStep()
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            } else {
                                Toast.makeText(context,
                                    context.getString(R.string.questionnaire_answer_all_questions), Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text(stringResource(R.string.questionnaire_next))
                        }
                    } else {
                        TextButton(onClick = { viewModel.save() }) {
                            Text(stringResource(R.string.questionnaire_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionnaireElement(
    element: QuestionnaireElement,
    elementValue: ElementValue?,
    textReplacements: Map<String, String>,
    onValueChange: (Int, ElementValue) -> Unit
) {
    when (element.type) {
        QuestionnaireElementType.TEXT_VIEW -> {
            TextViewElementComponent(element = element as TextViewElement, textReplacements)
        }

        QuestionnaireElementType.RADIO_GROUP -> {
            RadioGroupElementComponent(
                element = element as RadioGroupElement,
                value = (elementValue as RadioGroupValue).value,
                onValueChange = { newValue ->
                    onValueChange(
                        element.id,
                        RadioGroupValue(element.id, element.name, newValue)
                    )
                }
            )
        }

        QuestionnaireElementType.CHECKBOX_GROUP -> {
            CheckboxGroupElementComponent(
                element = element as CheckboxGroupElement,
                value = (elementValue as CheckboxGroupValue).values,
                onValueChange = { newValue ->
                    onValueChange(
                        element.id,
                        CheckboxGroupValue(element.id, element.name, newValue)
                    )
                })
        }

        QuestionnaireElementType.SLIDER -> {
            SliderElementComponent(
                element = element as SliderElement,
                value = (elementValue as SliderValue).value,
                onValueChange = { newValue ->
                    onValueChange(
                        element.id,
                        SliderValue(element.id, element.name, newValue)
                    )
                })
        }

        QuestionnaireElementType.TEXT_ENTRY -> {
            TextEntryElementComponent(
                element = element as TextEntryElement,
                value = (elementValue as TextEntryValue).value,
                onValueChange = { newValue ->
                    onValueChange(
                        element.id,
                        TextEntryValue(element.id, element.name, newValue)
                    )
                })
        }

        QuestionnaireElementType.EXTERNAL_QUESTIONNAIRE_LINK -> {
            ExternalQuestionnaireLinkElementComponent(element = element as ExternalQuestionnaireLinkElement)
        }

        QuestionnaireElementType.SOCIAL_NETWORK_ENTRY -> {
            SocialNetworkEntryElementComponent(
                element = element as SocialNetworkEntryElement,
                value = (elementValue as SocialNetworkEntryValue).values,
                onValueChange = { newValue ->
                    onValueChange(
                        element.id,
                        SocialNetworkEntryValue(element.id, element.name, newValue)
                    )
                })
        }

        QuestionnaireElementType.SOCIAL_NETWORK_RATING -> {
            SocialNetworkRatingElementComponent(
                element = element as SocialNetworkRatingElement,
                value = (elementValue as SocialNetworkRatingValue).values,
                onValueChange = { newValue ->
                    onValueChange(
                        element.id,
                        SocialNetworkRatingValue(element.id, element.name, newValue)
                    )
                }
            )
        }

        QuestionnaireElementType.CIRCUMPLEX -> {
            CircumplexElementComponent(
                element = element as CircumplexElement,
                value = (elementValue as CircumplexValue).value,
                onValueChange = { newValue ->
                    onValueChange(
                        element.id,
                        CircumplexValue(element.id, element.name, newValue)
                    )
                }
            )
        }

        QuestionnaireElementType.LIKERT_SCALE_LABEL -> {
            LikertScaleLabelElementComponent(element = element as LikertScaleLabelElement)
        }

        QuestionnaireElementType.BUTTON_GROUP, QuestionnaireElementType.MALFORMED -> {
            Text("Malformed element type: ${element.type}")
        }

        QuestionnaireElementType.TIME_INPUT -> {
            TimeInputElementComponent(element as TimeInputElement, (elementValue as TimeInputValue).value, onValueChange = { newValue ->
                onValueChange(
                    element.id,
                    TimeInputValue(element.id, element.name, newValue)
                )
            })
        }
    }
}