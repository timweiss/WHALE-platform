package de.mimuc.senseeverything.activity

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.api.model.ExperimentalGroupPhase
import de.mimuc.senseeverything.api.model.FullQuestionnaire
import de.mimuc.senseeverything.api.model.InteractionWidgetDisplayStrategy
import de.mimuc.senseeverything.api.model.Questionnaire
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.getCurrentStudyPhase
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.LogData
import de.mimuc.senseeverything.workers.enqueueSingleSensorReadingsUploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class StudyDebugInfo : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppandroidTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                titleContentColor = MaterialTheme.colorScheme.primary,
                            ),
                            title = {
                                Text("Debug Info")
                            }
                        )
                    }
                ) { innerPadding ->
                    StudyDebugInfoView(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@HiltViewModel
class StudyDebugInfoViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase
) : AndroidViewModel(application) {
    private val _currentStudyPhase = MutableStateFlow<ExperimentalGroupPhase?>(ExperimentalGroupPhase(0,0,InteractionWidgetDisplayStrategy.DEFAULT))
    val currentStudyPhase: StateFlow<ExperimentalGroupPhase?> get() = _currentStudyPhase

    private val _unsyncedLogDataCount = MutableStateFlow(0L)
    val unsyncedLogDataCount: StateFlow<Long> get() = _unsyncedLogDataCount

    private val _lastLogDataItem = MutableStateFlow<LogData?>(LogData())
    val lastLogDataItem: StateFlow<LogData?> get() = _lastLogDataItem

    private val _lastLogServiceExitTime = MutableStateFlow(0L)
    val lastLogServiceExitTime: StateFlow<Long> get() = _lastLogServiceExitTime

    private val _enrolmentId = MutableStateFlow("")
    val enrolmentId: StateFlow<String> get() = _enrolmentId

    private val _cachedQuestionnaires = MutableStateFlow<List<FullQuestionnaire>>(emptyList())
    val cachedQuestionnaires: StateFlow<List<FullQuestionnaire>> get() = _cachedQuestionnaires

    private val _studyStartedAt = MutableStateFlow(0L)
    val studyStartedAt: StateFlow<Long> get() = _studyStartedAt

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _currentStudyPhase.value = dataStoreManager.getCurrentStudyPhase()
                _enrolmentId.value = dataStoreManager.participantIdFlow.first()
                _cachedQuestionnaires.value = dataStoreManager.questionnairesFlow.first()
                _studyStartedAt.value = dataStoreManager.timestampStudyStartedFlow.first()
                _unsyncedLogDataCount.value = database.logDataDao().unsyncedCount
                _lastLogDataItem.value = database.logDataDao().lastItem
            }
            // _lastLogServiceExitTime.value = dataStoreManager.getLastLogServiceExitTime()
        }
    }

    fun scheduleImmediateSync() {
        viewModelScope.launch {
            val token = dataStoreManager.tokenFlow.first()
            enqueueSingleSensorReadingsUploadWorker(getApplication(), token, "immediateFromDebugSettings")
        }
    }

    fun openEnrolmentSettings(context: Context) {
        val intent = Intent(context, StudyEnrolment::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}

@Composable
fun StudyDebugInfoView(
    modifier: Modifier = Modifier,
    viewModel: StudyDebugInfoViewModel = viewModel()
) {
    val unsyncedLogDataCount = viewModel.unsyncedLogDataCount.collectAsState()
    val currentStudyPhase = viewModel.currentStudyPhase.collectAsState()
    val enrolmentId = viewModel.enrolmentId.collectAsState()
    val questionnaires = viewModel.cachedQuestionnaires.collectAsState()
    val studyStarted = viewModel.studyStartedAt.collectAsState()
    val lastLogDataItem = viewModel.lastLogDataItem.collectAsState()
    val context = LocalContext.current

    Column(modifier = modifier
        .padding(16.dp)) {
        Text("Your Enrolment: ${enrolmentId.value} started on ${dateFromTimestamp(studyStarted.value)}")
        Text("Cached Questionnaires: ${questionnaires.value.map { it.questionnaire.name + " (" + it.questionnaire.id + ")" }}")
        Text("Items not yet synced: ${unsyncedLogDataCount.value}")
        if (lastLogDataItem.value != null) {
            Text("Last Log Data Item: ${dateFromTimestamp(lastLogDataItem.value!!.timestamp)} ${lastLogDataItem.value?.data}")
        } else {
            Text("No log data found")
        }

        if (currentStudyPhase.value != null) {
            Text("Current Phase: ${currentStudyPhase.value?.fromDay}-${currentStudyPhase.value?.durationDays} ${currentStudyPhase.value?.interactionWidgetStrategy}")
        } else {
            Text("No study phase found")
        }

        Button(
            onClick = {
                viewModel.scheduleImmediateSync()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sync all data now")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.openEnrolmentSettings(context)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enrolment Settings")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview2() {
    AppandroidTheme {
        StudyDebugInfoView()
    }
}