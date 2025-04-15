package de.mimuc.senseeverything.activity.esm

import android.app.Application
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.api.model.CheckboxGroupElement
import de.mimuc.senseeverything.api.model.ExternalQuestionnaireLinkElement
import de.mimuc.senseeverything.api.model.GroupAlignment
import de.mimuc.senseeverything.api.model.RadioGroupElement
import de.mimuc.senseeverything.api.model.SliderElement
import de.mimuc.senseeverything.api.model.TextEntryElement
import de.mimuc.senseeverything.api.model.TextViewElement
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.fetchExternalQuestionnaireParams
import de.mimuc.senseeverything.db.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.core.net.toUri

@Composable
fun TextViewElementComponent(element: TextViewElement) {
    Text(element.textContent)
}

@Composable
fun RadioGroupElementComponent(
    element: RadioGroupElement,
    value: String,
    onValueChange: (String) -> Unit
) {
    val selected = remember { mutableStateOf("") }

    fun selectElement(option: String) {
        selected.value = option
        onValueChange(option)
    }

    if (element.alignment == GroupAlignment.Horizontal) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            element.options.forEach { option ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RadioButton(
                        selected = value.compareTo(option) == 0,
                        onClick = { selectElement(option) })
                    Text(option, textAlign = TextAlign.Center)
                }
            }
        }
    } else {
        Column {
            element.options.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = value.compareTo(option) == 0,
                        onClick = { selectElement(option) })
                    Text(option)
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

    if (element.alignment == GroupAlignment.Horizontal) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            element.options.forEach { option ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Checkbox(checked = value.contains(option), onCheckedChange = {
                        selectElement(option)
                    })
                    Text(option)
                }
            }
        }
    } else {
        Column {
            element.options.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
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
    val stepSize = (element.max - element.min / element.stepSize).toInt()

    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.toDouble()) },
        valueRange = element.min.toFloat()..element.max.toFloat(),
        steps = stepSize
    )
}

@Composable
fun TextEntryElementComponent(
    element: TextEntryElement,
    value: String,
    onValueChange: (String) -> Unit
) {
    TextField(
        value = value,
        onValueChange = { onValueChange(it) },
        label = { Text(element.hint) },
        modifier = Modifier.fillMaxWidth()
    )
}

@HiltViewModel
class ExternalQuestionnaireLinkElementComponentViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase
) : AndroidViewModel(application) {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    fun openQuestionnaire(context: Context, element: ExternalQuestionnaireLinkElement) {
        viewModelScope.launch {
            _isLoading.value = true

            val urlParams = fetchExternalQuestionnaireParams(element.urlParams, dataStoreManager, database)
            if (!element.externalUrl.startsWith("https://")) return@launch

            val url = element.externalUrl + "?" + urlParams.entries.joinToString("&") { "${it.key}=${it.value}" }

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

    Text("Please fill out the following questionnaire", modifier = Modifier.fillMaxWidth())
    Button(onClick = { viewModel.openQuestionnaire(context, element) }) {
        Text(
            element.actionText,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}