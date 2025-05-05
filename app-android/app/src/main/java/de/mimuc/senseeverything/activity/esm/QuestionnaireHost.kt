package de.mimuc.senseeverything.activity.esm

import android.app.Application
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.AndroidViewModel
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.api.model.CheckboxGroupElement
import de.mimuc.senseeverything.api.model.CheckboxGroupValue
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.ExternalQuestionnaireLinkElement
import de.mimuc.senseeverything.api.model.FullQuestionnaire
import de.mimuc.senseeverything.api.model.QuestionnaireElement
import de.mimuc.senseeverything.api.model.RadioGroupElement
import de.mimuc.senseeverything.api.model.RadioGroupValue
import de.mimuc.senseeverything.api.model.SliderElement
import de.mimuc.senseeverything.api.model.SliderValue
import de.mimuc.senseeverything.api.model.SocialNetworkEntryElement
import de.mimuc.senseeverything.api.model.SocialNetworkValue
import de.mimuc.senseeverything.api.model.TextEntryElement
import de.mimuc.senseeverything.api.model.TextEntryValue
import de.mimuc.senseeverything.api.model.TextViewElement
import de.mimuc.senseeverything.api.model.emptyValueForElement
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel(assistedFactory = QuestionnaireHostViewModel.QuestionnaireHostViewModelFactory::class)
class QuestionnaireHostViewModel @AssistedInject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase,
    @Assisted val questionnaire: FullQuestionnaire,
    @Assisted val onSave: (Map<Int, ElementValue>) -> Unit
) : AndroidViewModel(application) {
    private val _activeStep = MutableStateFlow(1)
    val activeStep: StateFlow<Int> get() = _activeStep

    private val _elementValues = MutableStateFlow<Map<Int, ElementValue>>(emptyMap())
    val elementValues: StateFlow<Map<Int, ElementValue>> = _elementValues

    @AssistedFactory
    interface QuestionnaireHostViewModelFactory {
        fun create(
            questionnaire: FullQuestionnaire,
            onSave: (Map<Int, ElementValue>) -> Unit
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

    fun nextStep() {
        _activeStep.value++
    }

    fun previousStep() {
        _activeStep.value--
    }

    fun save() {
        Log.d("Questionnaire", "Saving questionnaire from host with values: ${_elementValues.value}")
        onSave(_elementValues.value)
    }

    private fun initializeValues() {
        _elementValues.value = mutableMapOf()

        for (element in questionnaire.elements) {
            setElementValue(element.id, emptyValueForElement(element))
        }
    }
}

@Composable
fun QuestionnaireHost(
    questionnaire: FullQuestionnaire,
    onSave: (Map<Int, ElementValue>) -> Unit
) {
    val viewModel =
        hiltViewModel<QuestionnaireHostViewModel, QuestionnaireHostViewModel.QuestionnaireHostViewModelFactory> { factory ->
            factory.create(questionnaire, onSave)
        }

    Column {
        if (questionnaire.elements.isEmpty()) {
            Text("No questions have been provided. This is likely a mistake of the study creator.")
        } else {
            val maxStep = questionnaire.elements.maxOf { it.step }
            val currentStep by viewModel.activeStep.collectAsState()
            val currentElements = remember(currentStep) {
                questionnaire.elements.filter { it.step == currentStep }
                    .sortedBy { it.position }
            }
            val answerValues = viewModel.elementValues.collectAsState()

            LazyColumn {
                items(items = currentElements, key = { item -> item.id }) { element ->
                    val elementValue = answerValues.value[element.id]
                    Log.d("Questionnaire", "Element value: $elementValue")
                    QuestionnaireElement(element, elementValue, onValueChange = { id, value ->
                        viewModel.setElementValue(id, value)
                    })
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
                    TextButton(onClick = { viewModel.save() }) {
                        Text("Save")
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
    onValueChange: (Int, ElementValue) -> Unit
) {
    when (element.type) {
        "text_view" -> {
            TextViewElementComponent(element = element as TextViewElement)
        }

        "radio_group" -> {
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

        "checkbox_group" -> {
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

        "slider" -> {
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

        "text_entry" -> {
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

        "external_questionnaire_link" -> {
            ExternalQuestionnaireLinkElementComponent(element = element as ExternalQuestionnaireLinkElement)
        }

        "social_network_entry" -> {
            SocialNetworkEntryElementComponent(
                element = element as SocialNetworkEntryElement,
                value = (elementValue as SocialNetworkValue).values,
                onValueChange = { newValue ->
                    onValueChange(
                        element.id,
                        SocialNetworkValue(element.id, element.name, newValue)
                    )
                })
        }

        else -> {
            Text("Unknown element: ${element.type}")
        }
    }
}