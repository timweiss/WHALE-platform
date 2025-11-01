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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.esm.QuestionnaireActivity
import de.mimuc.senseeverything.activity.onboarding.Onboarding
import de.mimuc.senseeverything.activity.onboarding.OnboardingStep
import de.mimuc.senseeverything.activity.onboarding.startedButIncomplete
import de.mimuc.senseeverything.activity.settings.StudyInfo
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
import de.mimuc.senseeverything.workers.enqueueSingleSensorReadingsUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
        System.currentTimeMillis()).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _hasPermissionIssues = MutableStateFlow(false)
    val hasPermissionIssues: StateFlow<Boolean> get() = _hasPermissionIssues

    private val _unsyncedCountBeforeStudyEnd = MutableStateFlow<Long>(0)
    val unsyncedCountBeforeStudyEnd: StateFlow<Long> get() = _unsyncedCountBeforeStudyEnd

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

        viewModelScope.launch {
            delay(1000)
            checkIfStudyIsRunning()
        }
    }

    fun pauseStudy(context: Context) {
        LogServiceHelper.stopLogService(context.applicationContext)
        viewModelScope.launch {
            delay(1000)
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

    fun enqueueUploadJob() {
        viewModelScope.launch {
            val token = dataStoreManager.tokenFlow.first()
            enqueueSingleSensorReadingsUploadWorker(this@StudyHomeViewModel.application, token, "finalReadingsUploadUserInitiated", true)
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
                // Slide in from 40 dp from the top.
                with(density) { -60.dp.roundToPx() }
            } + fadeIn(
                // Fade in with the initial alpha of 0.3f.
                initialAlpha = 0.2f
            ),
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

                when (studyState) {
                    StudyState.RUNNING, StudyState.NOT_ENROLLED -> {
                        if (isEnrolled.value && !onboardingStep.value.startedButIncomplete()) {
                            Text(
                                stringResource(
                                    R.string.day_of,
                                    currentDay.value,
                                    study.value.durationDays,
                                    study.value.name
                                )
                            )

                            StudyActivity(
                                isRunning = isStudyRunning.value,
                                isPaused = isStudyPaused.value,
                                ended = false,
                                canResume = !hasPermissionIssues.value,
                                resumeStudy = { viewModel.resumeStudy(context) },
                                pauseStudy = { viewModel.pauseStudy(context) })

                            Spacer(modifier = Modifier.height(8.dp))

                            if (hasPermissionIssues.value) {
                                PermissionWarningBanner(
                                    onFixPermissions = { viewModel.openPermissionFix(context) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Text(
                                AnnotatedString.fromHtml(study.value.description),
                                style = MaterialTheme.typography.bodyLarge
                            )

                            if (pendingQuestionnaires.value.isNotEmpty()) {
                                val inboxItems = pendingQuestionnaires.value.filter { it.validDistance > kotlin.time.Duration.ZERO } .map { it.toInboxItem() }
                                QuestionnaireInbox(inboxItems, viewModel)
                            }

                            SpacerLine(paddingValues = PaddingValues(vertical = 12.dp), width = 96.dp)
                            FilledTonalButton(
                                onClick = { viewModel.openSettings(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.study_settings))
                            }
                        } else {
                            Button(
                                onClick = { viewModel.startOnboarding() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (onboardingStep.value.startedButIncomplete()) {
                                    Text(stringResource(R.string.continue_registration))
                                } else {
                                    Text(stringResource(R.string.enroll_in_study))
                                }
                            }
                        }
                    }
                    StudyState.CANCELLED -> {
                        Text(stringResource(R.string.main_study_cancelled_uninstall_hint))
                        Spacer(modifier = Modifier.height(8.dp))
                        SpacerLine(paddingValues = PaddingValues(vertical = 12.dp), width = 96.dp)
                        FilledTonalButton(
                            onClick = { viewModel.openSettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.study_settings))
                        }
                    }
                    StudyState.ENDED -> {
                        Text(stringResource(R.string.main_study_ended_last_questionnaire_hint))
                        Spacer(modifier = Modifier.height(8.dp))

                        if (unsyncedBeforeEnd.value > 0) {
                            DataSyncWarningBanner(onSyncItems = { viewModel.enqueueUploadJob() })
                        }

                        if (pendingQuestionnaires.value.isNotEmpty()) {
                            val inboxItems = pendingQuestionnaires.value.map { it.toInboxItem() }
                            QuestionnaireInbox(inboxItems, viewModel)
                        }

                        SpacerLine(paddingValues = PaddingValues(vertical = 12.dp), width = 96.dp)
                        FilledTonalButton(
                            onClick = { viewModel.openSettings(context) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.study_settings))
                        }
                    }
                    StudyState.LOADING -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                            Text(stringResource(R.string.main_loading))
                        }
                    }
                }
            }
        }

        LaunchedEffect(lifecycleState) {
            when (lifecycleState) {
                androidx.lifecycle.Lifecycle.State.RESUMED -> {
                    viewModel.load()
                    visible = true
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun QuestionnaireInbox(
    pendingQuestionnaires: List<QuestionnaireInboxItem>,
    viewModel: StudyHomeViewModel
) {
    val context = LocalContext.current

    if (pendingQuestionnaires.isEmpty()) {
        return
    }

    SpacerLine(paddingValues = PaddingValues(vertical = 12.dp), width = 96.dp)

    Column {
        Row(horizontalArrangement = Arrangement.Center) {
            Image(
                painter = painterResource(id = R.drawable.baseline_inbox_24),
                contentDescription = "",
                modifier = Modifier
                    .size(28.dp)
                    .padding(end = 6.dp)
            )
            Text(
                stringResource(R.string.main_questionnaire_inbox), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }

        Text(stringResource(R.string.main_questionnaire_inbox_completion_hint))
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        pendingQuestionnaires.forEach { pq ->
            Card(
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth(), onClick = {
                        viewModel.openPendingQuestionnaire(context, pq)
                    }) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Text(pq.title, fontWeight = FontWeight.SemiBold)
                    if (pq.validUntil != -1L) {
                        Text(
                            stringResource(
                                R.string.main_questionnaire_inbox_element_duration_validitiy,
                                pq.validDistance.inWholeMinutes
                            ))
                    } else {
                        Text(stringResource(R.string.main_questionnaire_inbox_element_duration_indefinite))
                    }
                }
            }
        }
    }
}

val green = Color.hsl(80f, 1f, 0.33f, 1f)
val orange = Color.hsl(37f, 1f, 0.50f, 1f)
val red = Color.hsl(0f, 1f, 0.41f, 1f)
val grey = Color.LightGray

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
            StatusIndicator(color = grey)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.study_has_ended))
        }
    } else if (isRunning) {
        Column {
            if (!isPaused) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIndicator(color = Color.hsl(80f, 1f, 0.33f, 1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.study_is_running))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIndicator(color = Color.hsl(37f, 1f, 0.50f, 1f))
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
                StatusIndicator(color = Color.hsl(0f, 1f, 0.41f, 1f))
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

@Composable
fun DataSyncWarningBanner(onSyncItems: () -> Unit) {
    StudyWarningBanner(
        imagePainter = painterResource(id = R.drawable.rounded_key_vertical_24),
        title = stringResource(R.string.main_lastsync_title),
        subtitle = stringResource(R.string.main_lastsync_subtitle),
        actionText = stringResource(R.string.main_lastsync_button),
        action = onSyncItems
    )
}

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

@Composable
fun StudyWarningBanner(imagePainter: Painter, title: String, subtitle: String, actionText: String, action: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = orange.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = imagePainter,
                contentDescription = title,
                modifier = Modifier
                    .size(28.dp)
                    .padding(end = 6.dp)
            )
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
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(onClick = action) {
                Text(actionText)
            }
        }
    }
}

@Composable
fun StatusIndicator(color: Color) {
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun SpacerLine(paddingValues: PaddingValues, width: Dp) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(1.dp)
                .background(Color.LightGray)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StudyHomePreview() {
    AppandroidTheme {
        StudyHome()
    }
}