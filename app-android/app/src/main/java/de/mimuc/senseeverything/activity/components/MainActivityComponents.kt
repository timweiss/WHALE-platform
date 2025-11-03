package de.mimuc.senseeverything.activity.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.ui.theme.StudyStatusColors

/**
 * Displays study activity status and controls for pausing/resuming
 */
@Composable
fun StudyActivity(
    isRunning: Boolean,
    isPaused: Boolean,
    ended: Boolean,
    canResume: Boolean,
    resumeStudy: () -> Unit,
    pauseStudy: () -> Unit
) {
    if (ended) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIndicator(color = StudyStatusColors.Ended)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.study_has_ended))
        }
    } else if (isRunning) {
        Column {
            if (!isPaused) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIndicator(color = StudyStatusColors.Running)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.study_is_running))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIndicator(color = StudyStatusColors.Warning)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.study_is_paused), fontStyle = FontStyle.Italic)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { resumeStudy() }) {
                    Text(
                        stringResource(R.string.resume_study),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIndicator(color = StudyStatusColors.Stopped)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.study_not_running))
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (canResume) {
                Button(onClick = { resumeStudy() }) {
                    Text(
                        stringResource(R.string.service_not_running_resume_study),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Warning banner for permission issues
 */
@Composable
fun PermissionWarningBanner(onFixPermissions: () -> Unit) {
    StudyWarningBanner(
        imagePainter = painterResource(id = R.drawable.rounded_key_vertical_24),
        title = stringResource(R.string.main_permission_warning_title),
        subtitle = stringResource(R.string.main_permission_warning_text),
        actionText = stringResource(R.string.main_permission_warning_button),
        action = onFixPermissions
    )
}

/**
 * Colored circle indicator for study status
 */
@Composable
fun StatusIndicator(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * Horizontal separator line
 */
@Composable
fun SpacerLine(paddingValues: PaddingValues, width: Dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(1.dp)
                .background(Color.LightGray)
        )
    }
}
