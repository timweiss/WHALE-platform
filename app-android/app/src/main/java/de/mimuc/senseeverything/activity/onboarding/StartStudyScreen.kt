package de.mimuc.senseeverything.activity.onboarding

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.fetchAndPersistQuestionnaires
import de.mimuc.senseeverything.api.loadStudy
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.StudyState
import de.mimuc.senseeverything.data.persistQuestionnaireElementContent
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.helpers.LogServiceHelper
import de.mimuc.senseeverything.helpers.generateSensitiveDataSalt
import de.mimuc.senseeverything.helpers.isServiceRunning
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.service.LogService
import de.mimuc.senseeverything.service.esm.EsmHandler
import de.mimuc.senseeverything.service.esm.SamplingEventReceiver
import de.mimuc.senseeverything.service.healthcheck.PeriodicServiceHealthcheckReceiver
import de.mimuc.senseeverything.study.reschedulePhaseChanges
import de.mimuc.senseeverything.study.scheduleStudyEndAlarm
import de.mimuc.senseeverything.workers.enqueueOldDataCheckWorker
import de.mimuc.senseeverything.workers.enqueuePendingQuestionnaireUploadWorker
import de.mimuc.senseeverything.workers.enqueueSensorReadingsUploadWorker
import de.mimuc.senseeverything.workers.enqueueUpdateQuestionnaireWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

@HiltViewModel
class StartStudyViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase,
) : AndroidViewModel(application) {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> get() = _pending

    private val _showErrorDialog = MutableStateFlow(false)
    val showErrorDialog: StateFlow<Boolean> get() = _showErrorDialog

    private val _errorCode = MutableStateFlow("")
    val errorCode: StateFlow<String> get() = _errorCode

    fun prepareStudy(context: Context, finish: () -> Unit) {
        viewModelScope.launch {
            _pending.value = true

            val studyId = dataStoreManager.studyIdFlow.first()
            val api = ApiClient.Companion.getInstance(getApplication())
            val study = loadStudy(api, studyId)
            if (study != null) {
                WHALELog.d("StartStudyViewModel", "Loaded study: $study")
                dataStoreManager.saveStudy(study)
                dataStoreManager.saveStudyDays(study.durationDays)
                dataStoreManager.saveRemainingStudyDays(study.durationDays)
                dataStoreManager.saveSensitiveDataSalt(generateSensitiveDataSalt())
                dataStoreManager.saveStudyState(StudyState.RUNNING)

                // the timestamp needs to be available for the EMA scheduling
                val startedTimestamp = System.currentTimeMillis()
                dataStoreManager.saveTimestampStudyStarted(startedTimestamp)

                try {
                    val questionnaires =
                        fetchAndPersistQuestionnaires(studyId, dataStoreManager, api)
                    persistQuestionnaireElementContent(context, questionnaires)
                } catch (exception: Exception) {
                    WHALELog.e("StartStudyViewModel", "Could not load questionnaires", exception)
                }

                EsmHandler.schedulePeriodicQuestionnaires(
                    context,
                    dataStoreManager,
                    database
                )

                EsmHandler.scheduleOneTimeQuestionnaires(
                    context,
                    dataStoreManager,
                    database
                )

                val token = dataStoreManager.tokenFlow.first()
                enqueueSensorReadingsUploadWorker(context, token)
                enqueueUpdateQuestionnaireWorker(context)
                enqueuePendingQuestionnaireUploadWorker(context, studyId, token)
                enqueueOldDataCheckWorker(context, LocalTime.of(14, 5))

                val phaseSchedules = reschedulePhaseChanges(context, database, dataStoreManager)
                dataStoreManager.savePhaseSchedules(phaseSchedules)

                val studyEndTimestamp = phaseSchedules.lastOrNull()?.endTimestamp
                    ?: (startedTimestamp + study.durationDays.days.inWholeMilliseconds)
                scheduleStudyEndAlarm(context, studyEndTimestamp, database)
                dataStoreManager.saveTimestampStudyEnd(studyEndTimestamp)
                PeriodicServiceHealthcheckReceiver.schedule(context)

                // automatically start data collection
                if (!isServiceRunning(LogService::class.java)) {
                    LogServiceHelper.startLogService(context.applicationContext)
                }

                SamplingEventReceiver.sendBroadcast(context, "setupComplete")
            } else {
                _showErrorDialog.value = true
                _errorCode.value = "timeout"
                WHALELog.e("StartStudyViewModel", "Could not load study")
            }

            _pending.value = false
            finish()
        }
    }

    fun closeError() {
        _showErrorDialog.value = false
        _errorCode.value = ""
    }
}

@Composable
fun StartStudyScreen(
    finish: () -> Unit,
    innerPadding: PaddingValues,
    viewModel: StartStudyViewModel = viewModel()
) {
    val context = LocalContext.current
    val pending = viewModel.pending.collectAsState()
    val showErrorDialog = viewModel.showErrorDialog.collectAsState()
    val errorCode = viewModel.errorCode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)
    ) {
        Heading(
            id = R.drawable.rounded_sentiment_very_satisfied_24,
            description = "Happy face",
            text = stringResource(R.string.onboarding_start_study_heading)
        )
        Spacer(modifier = Modifier.padding(12.dp))
        Text(stringResource(R.string.onboarding_start_study_everything_setup))

        EnrolmentErrorDialog(showErrorDialog.value, errorCode.value) { viewModel.closeError() }

        if (pending.value) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(64.dp)
                        .height(64.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(stringResource(R.string.onboarding_start_study_finalizing))
            }
        }

        Spacer(modifier = Modifier.padding(16.dp))
        Button(onClick = {
            viewModel.prepareStudy(context, finish)
        }, modifier = Modifier.fillMaxWidth(), enabled = !pending.value) {
            Text(stringResource(R.string.onboarding_start_study_start))
        }
    }
}