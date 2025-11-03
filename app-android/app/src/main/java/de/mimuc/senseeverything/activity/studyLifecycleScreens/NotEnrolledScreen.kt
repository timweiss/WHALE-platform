package de.mimuc.senseeverything.activity.studyLifecycleScreens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.mimuc.senseeverything.R

@Composable
fun NotEnrolledScreen(
    resumingOnboarding: Boolean,
    onStartOnboarding: () -> Unit
) {
    Button(
        onClick = onStartOnboarding,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (resumingOnboarding) {
            Text(stringResource(R.string.continue_registration))
        } else {
            Text(stringResource(R.string.enroll_in_study))
        }
    }
}
