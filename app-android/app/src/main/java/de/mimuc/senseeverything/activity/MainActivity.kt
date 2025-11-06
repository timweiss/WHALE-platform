package de.mimuc.senseeverything.activity

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.esm.QuestionnaireActivity
import de.mimuc.senseeverything.activity.onboarding.Onboarding
import de.mimuc.senseeverything.activity.onboarding.OnboardingStep
import de.mimuc.senseeverything.activity.onboarding.startedButIncomplete
import de.mimuc.senseeverything.activity.settings.StudyInfo
import de.mimuc.senseeverything.activity.studyLifecycleScreens.ActiveStudyScreen
import de.mimuc.senseeverything.activity.studyLifecycleScreens.LoadingScreen
import de.mimuc.senseeverything.activity.studyLifecycleScreens.NotEnrolledScreen
import de.mimuc.senseeverything.activity.studyLifecycleScreens.StudyCancelledScreen
import de.mimuc.senseeverything.activity.studyLifecycleScreens.StudyEndedScreen
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.loadStudy
import de.mimuc.senseeverything.api.model.Study
import de.mimuc.senseeverything.api.model.ema.QuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.StudyState
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.db.models.QuestionnaireInboxItem
import de.mimuc.senseeverything.db.models.toInboxItem
import de.mimuc.senseeverything.db.models.validDistance
import de.mimuc.senseeverything.helpers.LogServiceHelper
import de.mimuc.senseeverything.helpers.isServiceRunning
import de.mimuc.senseeverything.permissions.PermissionManager
import de.mimuc.senseeverything.service.LogService
import de.mimuc.senseeverything.workers.StaleUnsyncedSensorReadingsCheckWorker
import de.mimuc.senseeverything.workers.UploadWorkTag
import de.mimuc.senseeverything.workers.enqueueSingleSensorReadingsUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppandroidTheme {
                StudyHome()
            }
        }
    }
}

@HiltViewModel
class StudyHomeViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase
) : AndroidViewModel(application) {
    private val _isEnrolled = MutableStateFlow(false)
    val isEnrolled: StateFlow<Boolean> get() = _isEnrolled

    private val _isStudyRunning = MutableStateFlow(false)
    val isStudyRunning: StateFlow<Boolean> get() = _isStudyRunning

    private val _isStudyPaused = MutableStateFlow(false)
    val isStudyPaused: StateFlow<Boolean> get() = _isStudyPaused

    private val _currentDay = MutableStateFlow(0)
    val currentDay: StateFlow<Int> get() = _currentDay

    private val _study = MutableStateFlow(Study.empty)
    val study: StateFlow<Study> get() = _study

    val onboardingStepFlow = dataStoreManager.onboardingStepFlow.stateIn(viewModelScope, SharingStarted.Lazily, OnboardingStep.WELCOME)
    val studyStateFlow = dataStoreManager.studyStateFlow.stateIn(viewModelScope, SharingStarted.Lazily, StudyState.LOADING)

    val pendingQuestionnairesFlow: StateFlow<List<PendingQuestionnaire>> = database.pendingQuestionnaireDao().getAllNotExpiredFlow(
        System.currentTimeMillis()).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _hasPermissionIssues = MutableStateFlow(false)
    val hasPermissionIssues: StateFlow<Boolean> get() = _hasPermissionIssues

    private val _unsyncedCountBeforeStudyEnd = MutableStateFlow<Long>(0)
    val unsyncedCountBeforeStudyEnd: StateFlow<Long> get() = _unsyncedCountBeforeStudyEnd

    val uploadWorkInfo: StateFlow<WorkInfo?> = WorkManager.getInstance(application)
        .getWorkInfosByTagFlow(UploadWorkTag.FINAL_UPLOAD_MANUAL.tag)
        .map { workInfos -> workInfos.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val duringStudyUploadWorkInfo: StateFlow<WorkInfo?> = WorkManager.getInstance(application)
        .getWorkInfosByTagFlow(UploadWorkTag.STALE_UPLOAD_MANUAL.tag)
        .map { workInfos -> workInfos.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val staleUnsyncedItems: StateFlow<Long> = database.logDataDao().getUnsyncedCountBeforeFlow(
        System.currentTimeMillis() - StaleUnsyncedSensorReadingsCheckWorker.STALE_DURATION).stateIn(viewModelScope, SharingStarted.Lazily, 0)

    init {
        load()
    }

    fun load() {
        checkEnrolment()
        checkIfStudyIsRunning()
        getStudyDetails()
        checkPermissions()
        checkUnsyncedBeforeEnd()
    }

    private fun checkPermissions() {
        viewModelScope.launch {
            val permissions = PermissionManager.checkAll(getApplication())
            _hasPermissionIssues.value = permissions.values.any { !it }
        }
    }

    private fun checkUnsyncedBeforeEnd() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val studyEndTimestamp = dataStoreManager.timestampStudyEndFlow.first()
                _unsyncedCountBeforeStudyEnd.value = database.logDataDao().getUnsyncedCountBefore(studyEndTimestamp)
            }
        }
    }

    fun openPermissionFix(context: Context) {
        val intent = Intent(context, RecoverPermissionsActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun startOnboarding() {
        val intent = Intent(getApplication(), Onboarding::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        getApplication<Application>().startActivity(intent)
    }

    fun resumeStudy(context: Context) {
        LogServiceHelper.startLogService(context.applicationContext)
        pollLogServiceStatus(false)
    }

    fun pauseStudy(context: Context) {
        LogServiceHelper.stopLogService(context.applicationContext)
        pollLogServiceStatus(true)
    }

    private fun pollLogServiceStatus(serviceStopped: Boolean) {
        viewModelScope.launch {
            // Poll service state with timeout (max 5 seconds)
            var attempts = 0
            val maxAttempts = 20
            while (attempts < maxAttempts) {
                delay(250)
                val serviceRunning = isServiceRunning(LogService::class.java)

                if (serviceRunning xor serviceStopped) {
                    break
                }

                attempts++
            }
            checkIfStudyIsRunning()
        }
    }

    fun openSettings(context: Context) {
        val intent = Intent(getApplication(), StudyInfo::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun openPendingQuestionnaire(context: Context, pending: QuestionnaireInboxItem) {
        val trigger = fullQuestionnaireJson.decodeFromString<QuestionnaireTrigger>(pending.pendingQuestionnaire.triggerJson)

        val intent = Intent(context, QuestionnaireActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(QuestionnaireActivity.INTENT_TRIGGER_ID, trigger.id)
            putExtra(QuestionnaireActivity.INTENT_PENDING_QUESTIONNAIRE_ID, pending.pendingQuestionnaire.uid.toString())
        }

        context.startActivity(intent)
    }

    private fun checkEnrolment() {
        viewModelScope.launch {
            val token = dataStoreManager.tokenFlow.first()
            _isEnrolled.value = token.isNotEmpty()
        }
    }

    private fun checkIfStudyIsRunning() {
        viewModelScope.launch {
            val isRunning = isServiceRunning(LogService::class.java)
            _isStudyRunning.value = isRunning

            dataStoreManager.studyPausedFlow.collect { paused ->
                _isStudyPaused.value = paused
            }
        }
    }

    private fun getStudyDetails() {
        viewModelScope.launch {
            setCurrentStudyDay()
            var study = dataStoreManager.studyFlow.first()
            if (study != null) {
                _study.value = study
            } else {
                // try fetching from backend
                val studyId = dataStoreManager.studyIdFlow.first()
                val api = ApiClient.getInstance(getApplication())
                study = loadStudy(api, studyId)
                if (study != null) {
                    _study.value = study
                    // persist study
                    dataStoreManager.saveStudy(study)
                }
            }
        }
    }

    private suspend fun setCurrentStudyDay() {
        val unixStarted = dataStoreManager.timestampStudyStartedFlow.first()
        val date = Instant.ofEpochMilli(unixStarted).atZone(ZoneId.systemDefault()).toLocalDate()
        val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
        val days = today.toEpochDay() - date.toEpochDay() + 1
        _currentDay.value = days.toInt()
    }

    fun enqueueUploadJob(workTag: UploadWorkTag = UploadWorkTag.FINAL_UPLOAD_MANUAL) {
        viewModelScope.launch {
            val token = dataStoreManager.tokenFlow.first()
            enqueueSingleSensorReadingsUploadWorker(
                this@StudyHomeViewModel.application,
                token,
                workTag,
                true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyHome(viewModel: StudyHomeViewModel = viewModel()) {
    val isEnrolled = viewModel.isEnrolled.collectAsState()
    val isStudyRunning = viewModel.isStudyRunning.collectAsState()
    val isStudyPaused = viewModel.isStudyPaused.collectAsState()
    val currentDay = viewModel.currentDay.collectAsState()
    val study = viewModel.study.collectAsState()
    val onboardingStep = viewModel.onboardingStepFlow.collectAsState()
    val pendingQuestionnaires = viewModel.pendingQuestionnairesFlow.collectAsState()
    val studyState by viewModel.studyStateFlow.collectAsState()
    val hasPermissionIssues = viewModel.hasPermissionIssues.collectAsState()
    val unsyncedBeforeEnd = viewModel.unsyncedCountBeforeStudyEnd.collectAsState()
    val uploadWorkInfo = viewModel.uploadWorkInfo.collectAsState()
    val duringStudyUploadWorkInfo = viewModel.duringStudyUploadWorkInfo.collectAsState()
    val staleUnsyncedItems = viewModel.staleUnsyncedItems.collectAsState()
    val context = LocalContext.current

    var visible by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
            title = { Text("WHALE") }
        )
    }, modifier = Modifier.fillMaxSize()) { innerPadding ->
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically {
                with(density) { -60.dp.roundToPx() }
            } + fadeIn(initialAlpha = 0.2f),
            exit = slideOutVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    stringResource(R.string.welcome_to_whale),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp
                    )
                    Image(
                        painter = painterResource(id = R.drawable.whale),
                        contentDescription = "",
                        Modifier.width(96.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Show appropriate screen based on study state
                when (studyState) {
                    StudyState.RUNNING, StudyState.NOT_ENROLLED -> {
                        if (isEnrolled.value && !onboardingStep.value.startedButIncomplete()) {
                            // Filter questionnaires for active study
                            val activeStudyInboxItems = pendingQuestionnaires.value
                                .filter { it.validDistance > kotlin.time.Duration.ZERO }
                                .map { it.toInboxItem() }

                            ActiveStudyScreen(
                                currentDay = currentDay.value,
                                study = study.value,
                                isStudyRunning = isStudyRunning.value,
                                isStudyPaused = isStudyPaused.value,
                                hasPermissionIssues = hasPermissionIssues.value,
                                duringStudyUploadWorkInfo = duringStudyUploadWorkInfo,
                                staleUnsyncedItems = staleUnsyncedItems,
                                questionnaireInboxItems = activeStudyInboxItems,
                                onResumeStudy = { viewModel.resumeStudy(context) },
                                onPauseStudy = { viewModel.pauseStudy(context) },
                                onFixPermissions = { viewModel.openPermissionFix(context) },
                                onOpenQuestionnaire = { viewModel.openPendingQuestionnaire(context, it) },
                                onOpenSettings = { viewModel.openSettings(context) },
                                onEnqueueStaleUpload = { viewModel.enqueueUploadJob(UploadWorkTag.STALE_UPLOAD_MANUAL) }
                            )
                        } else {
                            NotEnrolledScreen(
                                resumingOnboarding = onboardingStep.value.startedButIncomplete(),
                                onStartOnboarding = { viewModel.startOnboarding() }
                            )
                        }
                    }
                    StudyState.CANCELLED -> {
                        StudyCancelledScreen(
                            onOpenSettings = { viewModel.openSettings(context) }
                        )
                    }
                    StudyState.ENDED -> {
                        val endedStudyInboxItems = pendingQuestionnaires.value.map { it.toInboxItem() }

                        StudyEndedScreen(
                            uploadWorkInfo = uploadWorkInfo.value,
                            unsyncedCount = unsyncedBeforeEnd.value,
                            questionnaireInboxItems = endedStudyInboxItems,
                            onOpenQuestionnaire = { viewModel.openPendingQuestionnaire(context, it) },
                            onOpenSettings = { viewModel.openSettings(context) },
                            onEnqueueUpload = { viewModel.enqueueUploadJob() }
                        )
                    }
                    StudyState.LOADING -> {
                        LoadingScreen()
                    }
                }
            }
        }

        LaunchedEffect(lifecycleState) {
            when (lifecycleState) {
                androidx.lifecycle.Lifecycle.State.RESUMED -> {
                    viewModel.load()
                    visible = true // required for animation
                }
                else -> {}
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StudyHomePreview() {
    AppandroidTheme {
        StudyHome()
    }
}