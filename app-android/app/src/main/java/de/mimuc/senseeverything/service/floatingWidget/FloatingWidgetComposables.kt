package de.mimuc.senseeverything.service.floatingWidget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.mimuc.senseeverything.activity.esm.CircumplexElementComponent
import de.mimuc.senseeverything.api.model.CircumplexValue
import de.mimuc.senseeverything.api.model.ElementValue
import de.mimuc.senseeverything.api.model.RadioGroupValue
import de.mimuc.senseeverything.api.model.SliderValue
import de.mimuc.senseeverything.api.model.TextEntryValue
import de.mimuc.senseeverything.api.model.ema.ButtonGroupElement
import de.mimuc.senseeverything.api.model.ema.CircumplexElement
import de.mimuc.senseeverything.api.model.ema.GroupAlignment
import de.mimuc.senseeverything.api.model.ema.QuestionnaireElement
import de.mimuc.senseeverything.api.model.ema.QuestionnaireElementType
import de.mimuc.senseeverything.api.model.ema.RadioGroupElement
import de.mimuc.senseeverything.api.model.ema.SliderElement
import de.mimuc.senseeverything.api.model.ema.TextEntryElement
import de.mimuc.senseeverything.api.model.ema.TextViewElement

@Composable
fun FloatingQuestionStep(
    elements: List<QuestionnaireElement>,
    elementValues: Map<Int, ElementValue>,
    onElementValueChange: (Int, ElementValue) -> Unit,
    onButtonSelection: (ButtonGroupElement, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .widthIn(min = 280.dp, max = 400.dp)
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            elements.sortedBy { it.position }.forEach { element ->
                FloatingElementRenderer(
                    element = element,
                    value = elementValues[element.id],
                    onValueChange = { newValue ->
                        onElementValueChange(element.id, newValue)
                    },
                    onButtonSelection = { buttonText ->
                        if (element is ButtonGroupElement) {
                            onButtonSelection(element, buttonText)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun FloatingElementRenderer(
    element: QuestionnaireElement,
    value: ElementValue?,
    onValueChange: (ElementValue) -> Unit,
    onButtonSelection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (element.type) {
        QuestionnaireElementType.TEXT_VIEW -> {
            val textElement = element as TextViewElement
            FloatingTextView(textElement, modifier)
        }

        QuestionnaireElementType.BUTTON_GROUP -> {
            val buttonElement = element as ButtonGroupElement
            FloatingButtonGroup(
                element = buttonElement,
                onButtonClick = onButtonSelection,
                modifier = modifier
            )
        }

        QuestionnaireElementType.RADIO_GROUP -> {
            val radioElement = element as RadioGroupElement
            val radioValue = value as? RadioGroupValue
            FloatingRadioGroup(
                element = radioElement,
                selectedIndex = radioValue?.value ?: -1,
                onSelectionChange = { index ->
                    onValueChange(RadioGroupValue(element.id, element.name, index))
                },
                modifier = modifier
            )
        }

        QuestionnaireElementType.TEXT_ENTRY -> {
            val textEntryElement = element as TextEntryElement
            val textValue = value as? TextEntryValue
            FloatingTextEntry(
                element = textEntryElement,
                value = textValue?.value ?: "",
                onValueChange = { newText ->
                    onValueChange(TextEntryValue(element.id, element.name, newText))
                },
                modifier = modifier
            )
        }

        QuestionnaireElementType.SLIDER -> {
            val sliderElement = element as SliderElement
            val sliderValue = value as? SliderValue
            FloatingSlider(
                element = sliderElement,
                value = sliderValue?.value ?: sliderElement.min.toDouble(),
                onValueChange = { newValue ->
                    onValueChange(SliderValue(element.id, element.name, newValue))
                },
                modifier = modifier
            )
        }

        QuestionnaireElementType.CIRCUMPLEX -> {
            val circumplexElement = element as CircumplexElement
            val circumplexValue = value as CircumplexValue

            CircumplexElementComponent(circumplexElement, value = circumplexValue.value, onValueChange = {
                onValueChange(CircumplexValue(element.id, element.name, it))
            })
        }

        else -> {
            // Placeholder for unsupported element types
            Text(
                text = "Unsupported element type: ${element.type.apiName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun FloatingTextView(
    element: TextViewElement,
    modifier: Modifier = Modifier
) {
    Text(
        text = element.textContent,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier
    )
}

@Composable
fun FloatingButtonGroup(
    element: ButtonGroupElement,
    onButtonClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (element.alignment) {
        GroupAlignment.Horizontal -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                element.buttons.keys.forEach { buttonText ->
                    Button(
                        onClick = { onButtonClick(buttonText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(buttonText)
                    }
                }
            }
        }

        GroupAlignment.Vertical -> {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                element.buttons.keys.forEach { buttonText ->
                    Button(
                        onClick = { onButtonClick(buttonText) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(buttonText)
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingRadioGroup(
    element: RadioGroupElement,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        element.options.forEachIndexed { index, option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (selectedIndex == index),
                        onClick = { onSelectionChange(index) },
                        role = Role.RadioButton
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (selectedIndex == index),
                    onClick = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun FloatingTextEntry(
    element: TextEntryElement,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(element.hint) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
fun FloatingSlider(
    element: SliderElement,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Value: ${value.toInt()}",
            style = MaterialTheme.typography.bodySmall
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toDouble()) },
            valueRange = element.min.toFloat()..element.max.toFloat(),
            steps = if (element.stepSize > 0) {
                ((element.max - element.min) / element.stepSize).toInt() - 1
            } else 0
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = element.min.toString(),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = element.max.toString(),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
