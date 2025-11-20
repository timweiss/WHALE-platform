package de.mimuc.senseeverything.activity.esm

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.model.ema.CheckboxGroupElement
import de.mimuc.senseeverything.api.model.ema.ExternalQuestionnaireLinkElement
import de.mimuc.senseeverything.api.model.ema.GroupAlignment
import de.mimuc.senseeverything.api.model.ema.LikertScaleLabelElement
import de.mimuc.senseeverything.api.model.ema.QuantityEntryElement
import de.mimuc.senseeverything.api.model.ema.RadioGroupElement
import de.mimuc.senseeverything.api.model.ema.SliderElement
import de.mimuc.senseeverything.api.model.ema.TextEntryElement
import de.mimuc.senseeverything.api.model.ema.TextViewElement
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.fetchExternalQuestionnaireParams
import de.mimuc.senseeverything.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

fun renderTextWithReplacements(text: String, textReplacements: Map<String, String>): String {
    if (textReplacements.isEmpty()) return text

    var tempText = text
    for ((key, value) in textReplacements) {
        tempText = tempText.replace("{{${key}}}", value)
    }
    return tempText
}

@Composable
fun TextViewElementComponent(element: TextViewElement, textReplacements: Map<String, String>) {
    val displayText = renderTextWithReplacements(element.configuration.text, textReplacements)

    Text(AnnotatedString.fromHtml(displayText))
}

@Composable
fun RadioGroupElementComponent(
    element: RadioGroupElement,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    val selected = remember { mutableStateOf(-1) }

    fun selectElement(index: Int) {
        selected.value = index
        onValueChange(index)
    }

    if (element.configuration.alignment == GroupAlignment.Horizontal) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            element.configuration.options.forEachIndexed { index, option ->
                val rowModifier = if (option.isEmpty()) {
                    Modifier
                        .padding(bottom = 10.dp)
                } else {
                    Modifier
                        .padding(6.dp)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        rowModifier
                            .selectable(
                                selected = (index == value),
                                onClick = { selectElement(index) },
                                role = Role.RadioButton
                            )
                            .padding(6.dp)
                ) {
                    RadioButton(
                        selected = value == index,
                        onClick = null
                    )
                    if (!option.isEmpty()) {
                        Text(option, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            element.configuration.options.forEachIndexed { index, option ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                        .selectable(
                            selected = (index == value),
                            onClick = { selectElement(index) },
                            role = Role.RadioButton
                        )
                        .fillMaxWidth()
                        .padding(6.dp)
                ) {
                    RadioButton(
                        selected = value == index,
                        onClick = null
                    )
                    Text(option, textAlign = TextAlign.Start)
                }
            }
        }
    }
}

@Composable
fun CheckboxGroupElementComponent(
    element: CheckboxGroupElement,
    value: List<String>,
    onValueChange: (List<String>) -> Unit
) {
    fun selectElement(option: String) {
        if (value.contains(option)) {
            val newValue = value.filter { o -> option.compareTo(o) != 0 }
            onValueChange(newValue)
        } else {
            val newValue = value.toMutableStateList().apply { add(option) }
            onValueChange(newValue)
        }
    }

    if (element.configuration.alignment == GroupAlignment.Horizontal) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            element.configuration.options.forEach { option ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        selectElement(option)
                    }) {
                    Checkbox(checked = value.contains(option), onCheckedChange = {
                        selectElement(option)
                    })
                    Text(option)
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            element.configuration.options.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectElement(option)
                        }) {
                    Checkbox(checked = value.contains(option), onCheckedChange = {
                        selectElement(option)
                    })
                    Text(option)
                }
            }
        }
    }
}

@Composable
fun SliderElementComponent(element: SliderElement, value: Double, onValueChange: (Double) -> Unit) {
    val stepSize = (element.configuration.max - element.configuration.min / element.configuration.stepSize).toInt()

    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toDouble()) },
        valueRange = element.configuration.min.toFloat()..element.configuration.max.toFloat(),
        steps = stepSize
    )
}

@Composable
fun TextEntryElementComponent(
    element: TextEntryElement,
    value: String,
    onValueChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    TextField(
        value = value,
        onValueChange = { onValueChange(it) },
        label = { Text(element.configuration.hint) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions.Default.copy(
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
            }
        )
    )
}

@Composable
fun QuantityEntryElementComponent(
    element: QuantityEntryElement,
    value: String,
    onValueChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = value,
                onValueChange = { onValueChange(it) },
                label = { Text(element.configuration.hint) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions.Default.copy(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    }
                )
            )

            if (element.configuration.unit.isNotEmpty()) {
                Text(
                    text = element.configuration.unit,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }

    if (value.isNotEmpty()) {
        val intValue = value.toIntOrNull()
        if (intValue != null) {
            if (intValue < element.configuration.rangeMin || intValue > element.configuration.rangeMax) {
                Text(
                    text = stringResource(
                        R.string.questionnaire_element_quantity_valid_range,
                        element.configuration.rangeMin,
                        element.configuration.rangeMax
                    ),
                    color = androidx.compose.ui.graphics.Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@HiltViewModel
class ExternalQuestionnaireLinkElementComponentViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase
) : AndroidViewModel(application) {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    fun openQuestionnaire(context: Context, element: ExternalQuestionnaireLinkElement, pendingQuestionnaireId: String?) {
        viewModelScope.launch {
            _isLoading.value = true

            val apiClient = ApiClient.getInstance(context)

            val urlParamsMap = element.configuration.urlParams.associate { it.key to it.value }
            val urlParams = withContext(Dispatchers.IO) {
                fetchExternalQuestionnaireParams(
                    urlParamsMap,
                    dataStoreManager,
                    database,
                    apiClient,
                    pendingQuestionnaireId
                )
            }
            if (!element.configuration.externalUrl.startsWith("https://")) return@launch

            val url =
                element.configuration.externalUrl + "?" + urlParams.entries.joinToString("&") { "${it.key}=${it.value}" }

            val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            _isLoading.value = false

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                val toast = Toast.makeText(
                    context,
                    "Cannot open questionnaire. Please install a web browser.",
                    Toast.LENGTH_LONG
                )
                toast.show()
            }
        }
    }
}

@Composable
fun ExternalQuestionnaireLinkElementComponent(
    viewModel: ExternalQuestionnaireLinkElementComponentViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    element: ExternalQuestionnaireLinkElement
) {
    val context = LocalContext.current
    val pendingQuestionnaire = LocalPendingQuestionnaire.current
    Button(onClick = {
        viewModel.openQuestionnaire(context, element, pendingQuestionnaire?.uid?.toString())
    }) {
        Text(
            element.configuration.actionText,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LikertScaleLabelElementComponent(
    element: LikertScaleLabelElement
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // First label - left aligned
        Text(
            text = element.configuration.options.first(),
            textAlign = TextAlign.Start,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Last label - right aligned
        Text(
            text = element.configuration.options.last(),
            textAlign = TextAlign.End,
            fontSize = 12.sp,
            lineHeight = 12.sp,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

