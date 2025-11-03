package de.mimuc.senseeverything.activity.studyLifecycleScreens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.components.SpacerLine

@Composable
fun StudyCancelledScreen(
    onOpenSettings: () -> Unit
) {
    Text(stringResource(R.string.main_study_cancelled_uninstall_hint))
    Spacer(modifier = Modifier.height(8.dp))
    SpacerLine(paddingValues = PaddingValues(vertical = 12.dp), width = 96.dp)
    FilledTonalButton(
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.study_settings))
    }
}
