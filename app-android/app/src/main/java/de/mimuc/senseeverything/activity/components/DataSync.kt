package de.mimuc.senseeverything.activity.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.ui.theme.StudyStatusColors

@Composable
fun StaleDataSyncWarningBanner(onSyncItems: () -> Unit) {
    StudyWarningBanner(
        imagePainter = painterResource(id = R.drawable.outline_arrow_warm_up_24),
        title = stringResource(R.string.main_stale_data_title),
        subtitle = stringResource(R.string.main_stale_data_subtitle),
        actionText = stringResource(R.string.main_lastsync_button),
        action = onSyncItems
    )
}

@Composable
fun DataSyncWarningBanner(onSyncItems: () -> Unit) {
    StudyWarningBanner(
        imagePainter = painterResource(id = R.drawable.outline_arrow_warm_up_24),
        title = stringResource(R.string.main_lastsync_title),
        subtitle = stringResource(R.string.main_lastsync_subtitle),
        actionText = stringResource(R.string.main_lastsync_button),
        action = onSyncItems
    )
}

@Composable
fun DataSyncProgressBanner(workInfo: WorkInfo?, onRetry: () -> Unit) {
    val state = workInfo?.state

    val (backgroundColor, title, subtitle, showProgress, showRetry) = when (state) {
        WorkInfo.State.ENQUEUED -> DataSyncProgress(
            StudyStatusColors.Warning.copy(alpha = 0.15f),
            stringResource(R.string.main_upload_enqueued_title),
            stringResource(R.string.main_upload_enqueued_subtitle),
            false,
            false
        )
        WorkInfo.State.RUNNING -> DataSyncProgress(
            Color(0xFF2196F3).copy(alpha = 0.15f),
            stringResource(R.string.main_upload_running_title),
            stringResource(R.string.main_upload_running_subtitle),
            true,
            false
        )
        WorkInfo.State.SUCCEEDED -> DataSyncProgress(
            Color(0xFF4CAF50).copy(alpha = 0.15f),
            stringResource(R.string.main_upload_success_title),
            stringResource(R.string.main_upload_success_subtitle),
            false,
            false
        )
        WorkInfo.State.FAILED -> DataSyncProgress(
            Color(0xFFF44336).copy(alpha = 0.15f),
            stringResource(R.string.main_upload_failed_title),
            stringResource(R.string.main_upload_failed_subtitle),
            false,
            true
        )
        else -> return
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 6.dp),
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.outline_arrow_warm_up_24),
                    contentDescription = title,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 6.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (showRetry) {
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalButton(onClick = onRetry) {
                    Text(stringResource(R.string.main_upload_retry_button))
                }
            }
        }
    }
}

private data class DataSyncProgress(
    val color: Color,
    val title: String,
    val subtitle: String,
    val showProgress: Boolean,
    val showRetry: Boolean
)

@Composable
fun StaleDataUploadInfo(
    duringStudyUploadWorkInfo: State<WorkInfo?>,
    staleUnsyncedItems: State<Long>,
    enqueueUpload: () -> Unit
) {
    val duringStudyWorkState = duringStudyUploadWorkInfo.value?.state
    val isDuringStudyUploadActive =
        duringStudyWorkState == WorkInfo.State.ENQUEUED || duringStudyWorkState == WorkInfo.State.RUNNING

    if (duringStudyUploadWorkInfo.value != null && (isDuringStudyUploadActive || duringStudyWorkState == WorkInfo.State.FAILED)) {
        DataSyncProgressBanner(
            workInfo = duringStudyUploadWorkInfo.value,
            onRetry = enqueueUpload
        )
        Spacer(modifier = Modifier.height(8.dp))
    } else if (staleUnsyncedItems.value > 0) {
        StaleDataSyncWarningBanner(onSyncItems = enqueueUpload)
        Spacer(modifier = Modifier.height(8.dp))
    }
}