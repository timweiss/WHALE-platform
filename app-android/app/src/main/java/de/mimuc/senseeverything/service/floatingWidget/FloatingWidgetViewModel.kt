package de.mimuc.senseeverything.service.floatingWidget

import androidx.lifecycle.ViewModel
import de.mimuc.senseeverything.api.model.ButtonGroupValue
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.ema.ButtonGroupElement
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.emptyValueForElement
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class FloatingWidgetViewModel() : ViewModel() {

    private val _currentStep = MutableStateFlow(1)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _elementValues = MutableStateFlow<Map<Int, ElementValue>>(emptyMap())
    val elementValues: StateFlow<Map<Int, ElementValue>> = _elementValues.asStateFlow()

    private val _textReplacements = MutableStateFlow<Map<String, String>>(emptyMap())
    val textReplacements: StateFlow<Map<String, String>> = _textReplacements.asStateFlow()

    private val _isCompleted = MutableStateFlow(false)
    val isCompleted: StateFlow<Boolean> = _isCompleted.asStateFlow()

    private var questionnaire: FullQuestionnaire? = null
    private var pendingQuestionnaireId: UUID? = null

    fun initialize(questionnaire: FullQuestionnaire, triggerUid: UUID?, replacements: Map<String, String>) {
        this.questionnaire = questionnaire
        this.pendingQuestionnaireId = triggerUid // Use triggerUid as the pending questionnaire ID for simplicity

        // Initialize empty values for all elements
        val initialValues = mutableMapOf<Int, ElementValue>()
        questionnaire.elements.forEach { element ->
            initialValues[element.id] = emptyValueForElement(element)
        }
        _elementValues.value = initialValues

        _textReplacements.value = replacements

        WHALELog.i("FloatingWidgetViewModel", "Initialized with questionnaire: ${questionnaire.questionnaire.name}")
    }

    fun handleButtonSelection(buttonElement: ButtonGroupElement, selectedButton: String) {
        // Update the element value
        val newValues = _elementValues.value.toMutableMap()
        newValues[buttonElement.id] = ButtonGroupValue(
            buttonElement.id,
            buttonElement.name,
            selectedButton
        )
        _elementValues.value = newValues

        // Navigate to next step or complete
        val nextStep = buttonElement.configuration.options.firstOrNull { it.label == selectedButton }?.nextStep
        if (nextStep != null) {
            navigateToStep(nextStep)
        } else {
            completeQuestionnaire()
        }
    }

    fun updateElementValue(elementId: Int, value: ElementValue) {
        val newValues = _elementValues.value.toMutableMap()
        newValues[elementId] = value
        _elementValues.value = newValues
    }

    private fun navigateToStep(step: Int) {
        _currentStep.value = step
        WHALELog.i("FloatingWidgetViewModel", "Navigated to step: $step")
    }

    private fun completeQuestionnaire() {
        _isCompleted.value = true
        WHALELog.i("FloatingWidgetViewModel", "Questionnaire completed")
    }

    override fun onCleared() {
        super.onCleared()
        WHALELog.i("FloatingWidgetViewModel", "ViewModel cleared")
    }
}
