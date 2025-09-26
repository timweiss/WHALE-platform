package de.mimuc.senseeverything.activity.esm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.mimuc.senseeverything.api.model.ema.TimeInputElement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeInputElementComponent(
    element: TimeInputElement,
    value: Pair<Int, Int>,
    onValueChange: (Pair<Int, Int>) -> Unit
) {
    fun hasValue(): Boolean {
        return value.first != -1 && value.second != -1
    }

    @Composable
    fun buttonColor(): ButtonColors {
        return if (hasValue()) ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) else ButtonDefaults.buttonColors()
    }

    val showTimePicker = remember { mutableStateOf(false) }

    val displayTime = if (hasValue()) {
        String.format("%02d:%02d", value.first, value.second)
    } else {
        element.configuration.label
    }

    Column(modifier = Modifier.Companion.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Companion.CenterVertically) {
            if (hasValue()) {
                Text(
                    displayTime,
                    modifier = Modifier.Companion.padding(end = 16.dp),
                    fontWeight = FontWeight.Companion.Bold
                )
            }

            Button(
                onClick = { showTimePicker.value = true },
                modifier = Modifier.Companion.fillMaxWidth(),
                colors = buttonColor()
            ) {
                Text(element.configuration.label)
            }
        }
    }

    if (showTimePicker.value) {
        TimeInputDialog(
            currentHour = if (hasValue()) value.first else 12,
            currentMinute = if (hasValue()) value.second else 0,
            onTimeSelected = { selectedHour, selectedMinute ->
                onValueChange(Pair(selectedHour, selectedMinute))
                showTimePicker.value = false
            },
            onDismiss = { showTimePicker.value = false }
        )
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeInputDialog(
    currentHour: Int,
    currentMinute: Int,
    onTimeSelected: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val timePickerState = remember {
        TimePickerState(
            initialHour = currentHour,
            initialMinute = currentMinute,
            is24Hour = true
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Time") },
        text = {
            TimeInput(
                state = timePickerState
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(timePickerState.hour, timePickerState.minute)
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}