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
import androidx.work.WorkInfo
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.components.DataSyncProgressBanner
import de.mimuc.senseeverything.activity.components.DataSyncWarningBanner
import de.mimuc.senseeverything.activity.components.QuestionnaireInbox
import de.mimuc.senseeverything.activity.components.SpacerLine
import de.mimuc.senseeverything.db.models.QuestionnaireInboxItem

@Composable
fun StudyEndedScreen(
    uploadWorkInfo: WorkInfo?,
    unsyncedCount: Long,
    questionnaireInboxItems: List<QuestionnaireInboxItem>,
    onOpenQuestionnaire: (QuestionnaireInboxItem) -> Unit,
    onOpenSettings: () -> Unit,
    onEnqueueUpload: () -> Unit
) {
    Text(stringResource(R.string.main_study_ended_last_questionnaire_hint))
    Spacer(modifier = Modifier.height(8.dp))

    val workState = uploadWorkInfo?.state
    val isUploadActive = workState == WorkInfo.State.ENQUEUED || workState == WorkInfo.State.RUNNING

    if (uploadWorkInfo != null && (isUploadActive || workState == WorkInfo.State.SUCCEEDED || workState == WorkInfo.State.FAILED)) {
        DataSyncProgressBanner(
            workInfo = uploadWorkInfo,
            onRetry = onEnqueueUpload
        )
    } else if (unsyncedCount > 0) {
        DataSyncWarningBanner(onSyncItems = onEnqueueUpload)
    }

    if (questionnaireInboxItems.isNotEmpty()) {
        QuestionnaireInbox(
            questionnaireInboxItems,
            openQuestionnaire = onOpenQuestionnaire
        )
    }

    SpacerLine(paddingValues = PaddingValues(vertical = 12.dp), width = 96.dp)
    FilledTonalButton(
        onClick = onOpenSettings,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.study_settings))
    }
}
