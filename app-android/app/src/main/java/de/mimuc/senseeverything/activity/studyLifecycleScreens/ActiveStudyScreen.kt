package de.mimuc.senseeverything.activity.studyLifecycleScreens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.components.PermissionWarningBanner
import de.mimuc.senseeverything.activity.components.QuestionnaireInbox
import de.mimuc.senseeverything.activity.components.SpacerLine
import de.mimuc.senseeverything.activity.components.StaleDataUploadInfo
import de.mimuc.senseeverything.activity.components.StudyActivity
import de.mimuc.senseeverything.api.model.Study
import de.mimuc.senseeverything.db.models.QuestionnaireInboxItem

@Composable
fun ActiveStudyScreen(
    currentDay: Int,
    study: Study,
    isStudyRunning: Boolean,
    isStudyPaused: Boolean,
    hasPermissionIssues: Boolean,
    duringStudyUploadWorkInfo: State<WorkInfo?>,
    staleUnsyncedItems: State<Long>,
    questionnaireInboxItems: List<QuestionnaireInboxItem>,
    onResumeStudy: () -> Unit,
    onPauseStudy: () -> Unit,
    onFixPermissions: () -> Unit,
    onOpenQuestionnaire: (QuestionnaireInboxItem) -> Unit,
    onOpenSettings: () -> Unit,
    onEnqueueStaleUpload: () -> Unit
) {
    Text(
        stringResource(
            R.string.day_of,
            currentDay,
            study.durationDays,
            study.name
        )
    )

    StudyActivity(
        isRunning = isStudyRunning,
        isPaused = isStudyPaused,
        ended = false,
        canResume = !hasPermissionIssues,
        resumeStudy = onResumeStudy,
        pauseStudy = onPauseStudy
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (hasPermissionIssues) {
        PermissionWarningBanner(
            onFixPermissions = onFixPermissions
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Shows banner in case there is stale data
    StaleDataUploadInfo(
        duringStudyUploadWorkInfo,
        staleUnsyncedItems,
        enqueueUpload = onEnqueueStaleUpload
    )

    Text(
        AnnotatedString.fromHtml(study.description),
        style = MaterialTheme.typography.bodyLarge
    )

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
