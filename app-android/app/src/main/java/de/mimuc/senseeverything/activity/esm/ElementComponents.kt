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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.api.ApiClient
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Composable
fun TextViewElementComponent(element: TextViewElement) {
    Text(AnnotatedString.fromHtml(element.textContent))
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

    if (element.alignment == GroupAlignment.Horizontal) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            element.options.forEachIndexed { index, option ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
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
                    Text(option, textAlign = TextAlign.Center)
                }
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            element.options.forEachIndexed { index, option ->
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

    if (element.alignment == GroupAlignment.Horizontal) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            element.options.forEach { option ->
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
            element.options.forEach { option ->
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

            val apiClient = ApiClient.getInstance(context)

            val urlParams = withContext(Dispatchers.IO) {
                fetchExternalQuestionnaireParams(
                    element.urlParams,
                    dataStoreManager,
                    database,
                    apiClient
                )
            }
            if (!element.externalUrl.startsWith("https://")) return@launch

            val url =
                element.externalUrl + "?" + urlParams.entries.joinToString("&") { "${it.key}=${it.value}" }

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
    Button(onClick = { viewModel.openQuestionnaire(context, element) }) {
        Text(
            element.actionText,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}
