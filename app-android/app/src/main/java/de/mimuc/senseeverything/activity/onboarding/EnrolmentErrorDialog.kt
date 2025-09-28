package de.mimuc.senseeverything.activity.onboarding

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun EnrolmentErrorDialog(showErrorDialog: Boolean, errorCode: String, onDismiss: () -> Unit) {
    if (showErrorDialog && errorCode.isNotEmpty()) {
        AlertDialog(
            title = {
                if (errorCode == "full" || errorCode == "closed") {
                    Text("Study closed")
                } else {
                    Text("Incorrect enrolment key")
                }
            },
            text = {
                if (errorCode == "full" || errorCode == "closed") {
                    Text("The study no longer accepts new participants.")
                } else {
                    Text("The study could not be joined. Please check if the enrolment key is correct.")
                }
            },
            onDismissRequest = {
                onDismiss()
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                    }
                ) {
                    Text("Confirm")
                }
            }
        )
    }
}