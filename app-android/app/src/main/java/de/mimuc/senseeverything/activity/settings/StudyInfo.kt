package de.mimuc.senseeverything.activity.settings

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.BuildConfig
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.MainActivity
import de.mimuc.senseeverything.activity.StudyDebugInfo
import de.mimuc.senseeverything.activity.components.SpacerLine
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.model.EnrolmentResponse
import de.mimuc.senseeverything.api.model.Study
import de.mimuc.senseeverything.api.model.getEnrolmentInfo
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.StudyState
import de.mimuc.senseeverything.data.currentStudyDay
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.logging.WHALELog
import de.mimuc.senseeverything.study.runStudyLifecycleCleanup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class StudyInfo : ComponentActivity() {
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
                                Text(stringResource(R.string.studyinfo_title))
                            }
                        )
                    }
                ) { innerPadding ->
                    StudyInfoView(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@HiltViewModel
class StudyInfoViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase
) : AndroidViewModel(application) {
    private val _enrolmentId = MutableStateFlow("")
    val enrolmentId: StateFlow<String> get() = _enrolmentId

    private val _studyStartedAt = MutableStateFlow(0L)
    val studyStartedAt: StateFlow<Long> get() = _studyStartedAt

    private val _currentDay = MutableStateFlow(0L)
    val currentDay: StateFlow<Long> get() = _currentDay

    private val _study = MutableStateFlow<Study?>(null)
    val study: StateFlow<Study?> get() = _study

    private val _showDataDeletionDialog = MutableStateFlow(false)
    val showDataDeletionDialog: StateFlow<Boolean> get() = _showDataDeletionDialog

    private val _showDataExportDialog = MutableStateFlow(false)
    val showDataExportDialog: StateFlow<Boolean> get() = _showDataExportDialog

    private val _showCancelParticipationDialog = MutableStateFlow(false)
    val showCancelParticipationDialog: StateFlow<Boolean> get() = _showCancelParticipationDialog

    private val _isCancellingParticipation = MutableStateFlow(false)
    val isCancellingParticipation: StateFlow<Boolean> get() = _isCancellingParticipation

    private val _studyState = MutableStateFlow(StudyState.RUNNING)
    val studyState: StateFlow<StudyState> get() = _studyState

    private val _enrolmentInfo = MutableStateFlow<EnrolmentResponse?>(null)
    val enrolmentInfo: StateFlow<EnrolmentResponse?> get() = _enrolmentInfo

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _enrolmentId.value = dataStoreManager.participantIdFlow.first()
                _studyStartedAt.value = dataStoreManager.timestampStudyStartedFlow.first()
                _currentDay.value = dataStoreManager.currentStudyDay()
                _study.value = dataStoreManager.studyFlow.first()
                _studyState.value = dataStoreManager.studyStateFlow.first() ?: StudyState.NOT_ENROLLED

                try {
                    _enrolmentInfo.value = getEnrolmentInfo(ApiClient.getInstance(getApplication()), dataStoreManager.tokenFlow.first())
                } catch (e: Exception) {
                    // ignore, enrolment info is optional
                    WHALELog.w("StudyInfoViewModel", "Failed to fetch enrolment info: ${e.localizedMessage}" )
                }
            }
        }
    }

    fun cancelParticipation(context: Context) {
        _isCancellingParticipation.value = true
        // all with loading spinner
        viewModelScope.launch {
            // 1. refactored endstudy method
            runStudyLifecycleCleanup(context, database)

            // 2. save data still required to datastore
            val enrolmentId = dataStoreManager.participantIdFlow.first()
            val study = dataStoreManager.studyFlow.first()
            dataStoreManager.eraseAllData()
            dataStoreManager.saveParticipantId(enrolmentId)
            dataStoreManager.saveStudyState(StudyState.CANCELLED)
            if (study != null) {
                dataStoreManager.saveStudy(study)
            }

            // 3. pop back to main activity after all operations are complete
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    fun requestCancelParticipation() {
        _showCancelParticipationDialog.value = true
    }

    fun requestDataDeletion(context: Context) {
        _showDataDeletionDialog.value = true
    }

    fun requestDataExport(context: Context) {
        _showDataExportDialog.value = true
    }

    fun sendDataDeletionEmail(context: Context, enrolmentId: String) {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            // Use study's contactEmail if available, fallback to resource string
            val emailAddress = study.value?.contactEmail ?: context.getString(R.string.dataprotection_email)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
            putExtra(Intent.EXTRA_SUBJECT,
                context.getString(R.string.studyinfo_data_deletion_email_subject))
            putExtra(Intent.EXTRA_TEXT,
                context.getString(R.string.studyinfo_data_deletion_email_body, enrolmentId))
        }
        try {
            context.startActivity(Intent.createChooser(emailIntent,
                context.getString(R.string.studyinfo_intent_send_email)))
        } catch (ex: android.content.ActivityNotFoundException) {
        }
        hideDataDeletionDialog()
    }

    fun sendDataExportEmail(context: Context, enrolmentId: String) {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            // Use study's contactEmail if available, fallback to resource string
            val emailAddress = study.value?.contactEmail ?: context.getString(R.string.dataprotection_email)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(emailAddress))
            putExtra(Intent.EXTRA_SUBJECT,
                context.getString(R.string.studyinfo_data_export_email_subject))
            putExtra(Intent.EXTRA_TEXT,
                context.getString(R.string.studyinfo_data_export_email_body, enrolmentId))
        }
        try {
            context.startActivity(Intent.createChooser(emailIntent, context.getString(R.string.studyinfo_intent_send_email)))
        } catch (ex: android.content.ActivityNotFoundException) {
            // no email client installed
        }
        hideDataExportDialog()
    }

    fun hideDataExportDialog() {
        _showDataExportDialog.value = false
    }

    fun hideDataDeletionDialog() {
        _showDataDeletionDialog.value = false
    }

    fun hideCancelParticipationDialog() {
        _showCancelParticipationDialog.value = false
    }
}

@Composable
fun StudyInfoView(
    modifier: Modifier = Modifier,
    viewModel: StudyInfoViewModel = viewModel()
) {
    val enrolmentId by viewModel.enrolmentId.collectAsState()
    val study by viewModel.study.collectAsState()
    val currentDay by viewModel.currentDay.collectAsState()
    val showDataDeletionDialog by viewModel.showDataDeletionDialog.collectAsState()
    val showDataExportDialog by viewModel.showDataExportDialog.collectAsState()
    val showCancelParticipationDialog by viewModel.showCancelParticipationDialog.collectAsState()
    val isCancellingParticipation by viewModel.isCancellingParticipation.collectAsState()
    val studyState by viewModel.studyState.collectAsState()
    val enrolmentInfo by viewModel.enrolmentInfo.collectAsState()
    val context = LocalContext.current

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    // Sync pager state with tab selection
    LaunchedEffect(pagerState.currentPage) {
        selectedTabIndex = pagerState.currentPage
    }

    if (showCancelParticipationDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.studyinfo_cancel_participation_confirm_title),
            text = stringResource(R.string.studyinfo_cancel_participation_confirm_text),
            confirmButtonText = stringResource(R.string.studyinfo_cancel_participation),
            onConfirm = {
                viewModel.hideCancelParticipationDialog()
                viewModel.cancelParticipation(context)
            },
            onDismiss = {
                viewModel.hideCancelParticipationDialog()
            }
        )
    }

    if (showDataDeletionDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.studyinfo_data_deletion_title),
            text = stringResource(R.string.studyinfo_data_deletion_description, enrolmentId) +
                    stringResource(R.string.studyinfo_data_export_proceed_email),
            confirmButtonText = stringResource(R.string.studyinfo_send_email),
            onConfirm = {
                viewModel.sendDataDeletionEmail(context, enrolmentId)
            },
            onDismiss = {
                viewModel.hideDataDeletionDialog()
            }
        )
    }

    if (showDataExportDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.studyinfo_data_export_title),
            text = stringResource(R.string.studyinfo_data_export_description, enrolmentId) +
                    stringResource(R.string.studyinfo_data_export_proceed_email),
            confirmButtonText = stringResource(R.string.studyinfo_send_email),
            onConfirm = {
                viewModel.sendDataExportEmail(context, enrolmentId)
            },
            onDismiss = {
                viewModel.hideDataExportDialog()
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                },
                text = { Text(stringResource(R.string.studyinfo_tab_settings)) }
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(1)
                    }
                },
                text = { Text(stringResource(R.string.studyinfo_tab_faq)) }
            )
            Tab(
                selected = selectedTabIndex == 2,
                onClick = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(2)
                    }
                },
                text = { Text(stringResource(R.string.studyinfo_tab_data_protection)) }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> SettingsContent(
                    enrolmentId = enrolmentId,
                    study = study,
                    currentDay = currentDay,
                    studyState = studyState,
                    enrolmentInfo = enrolmentInfo,
                    isCancellingParticipation = isCancellingParticipation,
                    viewModel = viewModel
                )
                1 -> FAQContent(embeddedInfoUrl = study?.embeddedInfoUrl)
                2 -> DataProtectionContent(dataProtectionNotice = study?.dataProtectionNotice)
            }
        }
    }
}

@Composable
fun SettingsContent(
    enrolmentId: String,
    study: Study?,
    currentDay: Long,
    studyState: StudyState,
    enrolmentInfo: EnrolmentResponse?,
    isCancellingParticipation: Boolean,
    viewModel: StudyInfoViewModel
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(stringResource(R.string.studyinfo_your_id), fontWeight = FontWeight.Bold)
        SelectionContainer {
            Text(enrolmentId)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(stringResource(R.string.studyinfo_remaining_days), fontWeight = FontWeight.Bold)
        if (study == null) {
            when (studyState) {
                StudyState.CANCELLED -> {
                    Text(stringResource(R.string.studyinfo_study_cancelled))
                }
                StudyState.ENDED -> {
                    Text(stringResource(R.string.studyinfo_study_ended))
                }
                else -> {
                    Text(stringResource(R.string.studyinfo_no_information_available))
                }
            }
        } else {
            val remainingDays = study.durationDays - currentDay
            if (remainingDays == 0L) {
                Text(stringResource(R.string.studyinfo_last_day))
            } else {
                Text(stringResource(R.string.n_days, remainingDays))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isCancellingParticipation) {
            // only allow premature cancellation if study is running
            if (studyState == StudyState.RUNNING) {
                Button(
                    onClick = {
                        viewModel.requestCancelParticipation()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.studyinfo_cancel_participation))
                }

                SpacerLine(paddingValues = PaddingValues(vertical = 12.dp), width = 96.dp)
            }

            // only show data deletion after study cancellation or end
            if (studyState == StudyState.CANCELLED || studyState == StudyState.ENDED) {
                // Data deletion and export needs to be available even after study cancellation or end
                Button(
                    onClick = {
                        viewModel.requestDataDeletion(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.studyinfo_request_data_deletion))
                }
            }

            Button(
                onClick = {
                    viewModel.requestDataExport(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.studyinfo_request_data_export))
            }

            if (enrolmentInfo?.debugEnabled == true || BuildConfig.DEBUG) {
                Button(
                    onClick = {
                        // open enrolment settings
                        val intent = Intent(context, StudyDebugInfo::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.studyinfo_open_debug))
                }
            }
        } else {
            CircularProgressIndicator(
                modifier = Modifier
                    .width(64.dp)
                    .height(64.dp),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Text(stringResource(R.string.studyinfo_cancelling_pending))
        }
    }
}

@Composable
fun FAQContent(embeddedInfoUrl: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (embeddedInfoUrl.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.studyinfo_faq_not_available),
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = WebViewClient()
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = false
                            setSupportZoom(false)
                            builtInZoomControls = false
                            displayZoomControls = false
                        }
                    }
                },
                update = { webView ->
                    webView.loadUrl(embeddedInfoUrl)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun DataProtectionContent(dataProtectionNotice: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (dataProtectionNotice.isNullOrBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.studyinfo_data_protection_not_available),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(AnnotatedString.fromHtml(dataProtectionNotice))
            }
        }
    }
}


@Composable
fun ConfirmationDialog(
    title: String,
    text: String,
    confirmButtonText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.abort))
            }
        }
    )
}