package de.mimuc.senseeverything.activity

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.api.model.ExperimentalGroupPhase
import de.mimuc.senseeverything.api.model.InteractionWidgetDisplayStrategy
import de.mimuc.senseeverything.api.model.ema.EMAFloatingWidgetNotificationTrigger
import de.mimuc.senseeverything.api.model.ema.EMAFloatingWidgetTriggerConfiguration
import de.mimuc.senseeverything.api.model.ema.EventQuestionnaireTrigger
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.contentextraction.ContentExtractionRunReading
import de.mimuc.senseeverything.contentextraction.DownloadFailures
import de.mimuc.senseeverything.contentextraction.FLAG_EXTRACTION_HANDLED
import de.mimuc.senseeverything.contentextraction.ModelStore
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.StudyState
import de.mimuc.senseeverything.data.getCurrentStudyPhase
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.LogData
import de.mimuc.senseeverything.db.models.NotificationTrigger
import de.mimuc.senseeverything.db.models.NotificationTriggerModality
import de.mimuc.senseeverything.db.models.NotificationTriggerPriority
import de.mimuc.senseeverything.db.models.NotificationTriggerSource
import de.mimuc.senseeverything.db.models.NotificationTriggerStatus
import de.mimuc.senseeverything.db.models.ScheduledAlarm
import de.mimuc.senseeverything.helpers.DatabaseExporter
import de.mimuc.senseeverything.helpers.ExportStatus
import de.mimuc.senseeverything.helpers.getCurrentTimeBucket
import de.mimuc.senseeverything.sensor.implementation.ConversationSensor
import de.mimuc.senseeverything.sensor.implementation.UITreeSensor
import de.mimuc.senseeverything.service.accessibility.ScreenshotPeriodEndReceiver
import de.mimuc.senseeverything.service.accessibility.ScreenshotRatingContext
import de.mimuc.senseeverything.service.accessibility.ScreenshotStore
import de.mimuc.senseeverything.service.esm.EsmHandler
import de.mimuc.senseeverything.study.setStudyEndAlarm
import de.mimuc.senseeverything.workers.ContentExtractionWorker
import de.mimuc.senseeverything.workers.ModelDownloadWorker
import de.mimuc.senseeverything.workers.UploadWorkTag
import de.mimuc.senseeverything.workers.enqueueManualContentExtraction
import de.mimuc.senseeverything.workers.enqueueModelDownload
import de.mimuc.senseeverything.workers.enqueueSinglePendingQuestionnaireUploadWorker
import de.mimuc.senseeverything.workers.enqueueSingleSensorReadingsUploadWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

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
    private val database: AppDatabase,
    private val databaseExporter: DatabaseExporter
) : AndroidViewModel(application) {
    private val _currentStudyPhase = MutableStateFlow<ExperimentalGroupPhase?>(ExperimentalGroupPhase(0, "", 0,0,InteractionWidgetDisplayStrategy.DEFAULT, null))
    val currentStudyPhase: StateFlow<ExperimentalGroupPhase?> get() = _currentStudyPhase

    private val _unsyncedLogDataCount = MutableStateFlow(0L)
    val unsyncedLogDataCount: StateFlow<Long> get() = _unsyncedLogDataCount

    private val _lastLogDataItem = MutableStateFlow<LogData?>(null)
    val lastLogDataItem: StateFlow<LogData?> get() = _lastLogDataItem

    private val _lastLogServiceExitTime = MutableStateFlow(0L)
    val lastLogServiceExitTime: StateFlow<Long> get() = _lastLogServiceExitTime

    private val _enrolmentId = MutableStateFlow("")
    val enrolmentId: StateFlow<String> get() = _enrolmentId

    private val _cachedQuestionnaires = MutableStateFlow<List<FullQuestionnaire>>(emptyList())
    val cachedQuestionnaires: StateFlow<List<FullQuestionnaire>> get() = _cachedQuestionnaires

    private val _studyStartedAt = MutableStateFlow(0L)
    val studyStartedAt: StateFlow<Long> get() = _studyStartedAt

    private val _studyEnded = MutableStateFlow(false)
    val studyEnded: StateFlow<Boolean> get() = _studyEnded

    private val _sensorRunning = MutableStateFlow(false)
    val sensorRunning: StateFlow<Boolean> get() = _sensorRunning

    private val _screenshots = MutableStateFlow<List<ScreenshotPreview>>(emptyList())
    val screenshots: StateFlow<List<ScreenshotPreview>> get() = _screenshots

    val exportStatus: StateFlow<ExportStatus> = databaseExporter.exportStatus

    private val _extraction = MutableStateFlow<ExtractionDebugState?>(null)
    /** null when the study has no content extraction */
    val extraction: StateFlow<ExtractionDebugState?> get() = _extraction

    val modelDownloadWork: StateFlow<WorkInfo?> = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.UNIQUE_NAME)
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val manualExtractionWork: StateFlow<WorkInfo?> = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWorkFlow(ContentExtractionWorker.UNIQUE_MANUAL)
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var conversationSensor: ConversationSensor? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _currentStudyPhase.value = dataStoreManager.getCurrentStudyPhase()
                _enrolmentId.value = dataStoreManager.participantIdFlow.first()
                _cachedQuestionnaires.value = dataStoreManager.questionnairesFlow.first()
                _studyStartedAt.value = dataStoreManager.timestampStudyStartedFlow.first()
                _studyEnded.value = dataStoreManager.studyStateFlow.first() == StudyState.ENDED
                _unsyncedLogDataCount.value = database.logDataDao().unsyncedCount
                _lastLogDataItem.value = database.logDataDao().lastItem
                _screenshots.value = loadScreenshots()
                _extraction.value = loadExtractionState()
            }
            // _lastLogServiceExitTime.value = dataStoreManager.getLastLogServiceExitTime()
        }
    }

    /** Decodes every sampled screenshot at a quarter of its size, enough for a thumbnail. */
    private fun loadScreenshots(): List<ScreenshotPreview> {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        return ScreenshotStore.readAll(ScreenshotStore.root(application)).map { stored ->
            ScreenshotPreview(
                periodEnd = stored.periodEnd,
                packageName = stored.packageName,
                caption = "#${stored.slot} ${dateFromTimestamp(stored.sample.timestamp)}",
                // null if the image was replaced since listing
                image = BitmapFactory.decodeFile(stored.image.path, options)?.asImageBitmap()
            )
        }
    }

    /**
     * Emits the event the [ScreenshotPeriodEndReceiver] emits at the period end, with the same context.
     * Valid from now, as the period may still be running or have ended longer ago than the validity.
     * Doesn't mark the period as prompted, so its regular rating is still requested.
     */
    fun requestScreenshotRating(periodEnd: Long) {
        viewModelScope.launch {
            val eventName = ScreenshotPeriodEndReceiver.EVENT
            // handleEvent silently ignores events without a trigger
            val hasTrigger = dataStoreManager.questionnairesFlow.first()
                .flatMap { it.triggers }
                .filterIsInstance<EventQuestionnaireTrigger>()
                .any { it.configuration.eventName == eventName }
            if (!hasTrigger) {
                Toast.makeText(application, "No event trigger for $eventName", Toast.LENGTH_SHORT).show()
                return@launch
            }

            EsmHandler.handleEvent(
                eventName, null, null, application, dataStoreManager, database,
                contextJson = ScreenshotRatingContext(periodEnd).toJson()
            )
            Toast.makeText(application, "Emitted $eventName", Toast.LENGTH_SHORT).show()
        }
    }

    /** Files and database only; the worker states come from WorkManager. Blocking. */
    private suspend fun loadExtractionState(): ExtractionDebugState? {
        val model = dataStoreManager.studyFlow.first()?.contentExtraction?.model ?: return null
        val root = ModelStore.root(getApplication())
        val failures = DownloadFailures(root).read()
        return ExtractionDebugState(
            modelSizeBytes = model.sizeBytes,
            modelReady = ModelStore.isReady(root, model),
            partialBytes = ModelStore.partFile(root, model.sha256).length(),
            failedDownloads = failures.count,
            lastDownloadFailure = failures.lastReason,
            waitingRows = database.logDataDao().countWithoutFlag(UITreeSensor.NAME, FLAG_EXTRACTION_HANDLED),
            lastRun = runCatching {
                Json.decodeFromString<ContentExtractionRunReading>(ModelStore.lastRunFile(root).readText())
            }.getOrNull()
        )
    }

    fun refreshExtraction() {
        viewModelScope.launch(Dispatchers.IO) { _extraction.value = loadExtractionState() }
    }

    /** Clears the failure count (the extraction gives up after 3) and (re)starts the download, on Wi-Fi. */
    fun downloadModelNow() {
        viewModelScope.launch(Dispatchers.IO) {
            val model = dataStoreManager.studyFlow.first()?.contentExtraction?.model ?: return@launch
            DownloadFailures(ModelStore.root(getApplication())).clear()
            enqueueModelDownload(getApplication(), dataStoreManager.tokenFlow.first(), model, ExistingWorkPolicy.REPLACE)
            _extraction.value = loadExtractionState()
        }
    }

    /** A run now, as a foreground service, without the night window and constraints. */
    fun runExtractionNow() {
        enqueueManualContentExtraction(getApplication())
        Toast.makeText(getApplication(), "Extraction run enqueued", Toast.LENGTH_SHORT).show()
    }

    fun scheduleImmediateSync() {
        viewModelScope.launch {
            val token = dataStoreManager.tokenFlow.first()
            enqueueSingleSensorReadingsUploadWorker(getApplication(), token, UploadWorkTag.IMMEDIATE_FROM_DEBUG, true)
        }
    }

    fun schedulePendingQuestionnaireSync() {
        viewModelScope.launch {
            val token = dataStoreManager.tokenFlow.first()
            val studyId = dataStoreManager.studyIdFlow.first()
            enqueueSinglePendingQuestionnaireUploadWorker(getApplication(), studyId, token, "immediateFromDebugSettings")
        }
    }

    fun openEnrolmentSettings(context: Context) {
        val intent = Intent(context, StudyEnrolment::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun startConversationRecording(context: Context) {
        conversationSensor = ConversationSensor(context, database)
        conversationSensor!!.start(context)
        _sensorRunning.value = true
    }

    fun stopConversationRecording(context: Context) {
        conversationSensor?.stop()
        _sensorRunning.value = false
    }

    fun exportDatabase(context: Context) {
        viewModelScope.launch {
            databaseExporter.exportDatabase(context) { zipFile ->
                // Clean up after a delay to allow sharing to complete
                viewModelScope.launch {
                    kotlinx.coroutines.delay(1000 * 60 * 2) // 2 minutes
                    zipFile.delete()
                }
            }
        }
    }

    fun pushNotificationTrigger() {
        val trigger = NotificationTrigger(
            uid = UUID.randomUUID(),
            modality = NotificationTriggerModality.EventContingent,
            name = "Debug Trigger",
            questionnaireId = 8L,
            addedAt = System.currentTimeMillis(),
            validFrom = System.currentTimeMillis(),
            priority = NotificationTriggerPriority.Default,
            source = NotificationTriggerSource.Scheduled,
            status = NotificationTriggerStatus.Planned,
            triggerJson = fullQuestionnaireJson.encodeToString(EMAFloatingWidgetNotificationTrigger(
                1,
                8,
                0,
                true,
                EMAFloatingWidgetTriggerConfiguration(
                    "Debug Trigger",
                    "Debug",
                    emptyList(),
                    0,
                    0,
                    0,
                    NotificationTriggerModality.EventContingent,
                    NotificationTriggerPriority.Default,
                    NotificationTriggerSource.Scheduled,
                    "Debug",
                    -1)
            )),
            timeBucket = getCurrentTimeBucket(),
            updatedAt = System.currentTimeMillis()
        )

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                database.notificationTriggerDao().insert(trigger)
            }
        }
    }

    fun scheduleStudyEnd() {
        viewModelScope.launch {
            val timestamp = System.currentTimeMillis() + 30.seconds.inWholeMilliseconds
            val scheduledAlarm = ScheduledAlarm.getOrCreateScheduledAlarm(
                database,
                "EndStudyReceiver",
                "EndDebug",
                timestamp
            )
            val context = this@StudyDebugInfoViewModel.application
            setStudyEndAlarm(context, scheduledAlarm)

            Toast.makeText(
                context,
                "Scheduled study end alarm on $timestamp",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}

class ScreenshotPreview(
    val periodEnd: Long,
    val packageName: String,
    val caption: String,
    val image: ImageBitmap?
)

class ExtractionDebugState(
    val modelSizeBytes: Long,
    val modelReady: Boolean,
    /** bytes of an unfinished download */
    val partialBytes: Long,
    val failedDownloads: Int,
    val lastDownloadFailure: String?,
    /** UI tree rows the extraction hasn't handled yet */
    val waitingRows: Int,
    val lastRun: ContentExtractionRunReading?
)

@Composable
private fun ContentExtractionDebugSection(
    state: ExtractionDebugState,
    downloadWork: WorkInfo?,
    manualRunWork: WorkInfo?,
    onDownloadNow: () -> Unit,
    onRunNow: () -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    Text("Content extraction")
    Text(
        if (state.modelReady) "Model: ready (${size(state.modelSizeBytes)})"
        else "Model: missing, ${size(state.partialBytes)} of ${size(state.modelSizeBytes)} downloaded"
    )
    downloadWork?.let { work ->
        val downloaded = work.progress.getLong(ModelDownloadWorker.PROGRESS_DOWNLOADED_BYTES, -1)
        val progress = if (downloaded >= 0) ", ${downloaded * 100 / state.modelSizeBytes} %" else ""
        val error = work.outputData.getString(ModelDownloadWorker.OUTPUT_ERROR)?.let { ", error: $it" } ?: ""
        Text("Download: ${work.state}$progress, attempt ${work.runAttemptCount + 1}$error")
    }
    if (state.failedDownloads > 0) {
        Text("Failed downloads: ${state.failedDownloads} of ${DownloadFailures.MAX} (last: ${state.lastDownloadFailure})")
    }
    Text("UI tree rows waiting: ${state.waitingRows}")
    manualRunWork?.let { Text("Manual run: ${it.state}") }
    state.lastRun?.let { run ->
        val seconds = (run.endedAt - run.startedAt) / 1000
        val battery = "${run.batteryStart.levelPercent} → ${run.batteryEnd.levelPercent} %" +
                if (run.batteryStart.charging == false && run.batteryEnd.charging == false) " on battery" else ""
        Text(
            "Last run (${run.trigger}, ${dateFromTimestamp(run.startedAt)}): ${run.outcome} after $seconds s, " +
                    "backend ${run.backend ?: "-"}, engine start ${run.engineInitMs ?: "-"} ms. " +
                    "${run.rowsHandled} rows, ${run.snapshotsExtracted} snapshots, ${run.calls} calls " +
                    "(${run.callErrors} errors). CPU ${run.cpuMs / 1000} s, peak memory " +
                    "${run.peakRssKb?.let { it / 1024 } ?: "-"} MB, battery $battery." +
                    run.engineErrors.joinToString("") { "\nError: $it" }
        )
    }
    Button(onClick = onDownloadNow, modifier = Modifier.fillMaxWidth()) {
        Text("Download model now")
    }
    Button(onClick = onRunNow, modifier = Modifier.fillMaxWidth()) {
        Text("Run extraction now")
    }
    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
        Text("Refresh extraction status")
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
    val studyEnded = viewModel.studyEnded.collectAsState()
    val sensorRunning = viewModel.sensorRunning.collectAsState()
    val exportStatus = viewModel.exportStatus.collectAsState()
    val screenshots = viewModel.screenshots.collectAsState()
    val extraction = viewModel.extraction.collectAsState()
    val modelDownloadWork = viewModel.modelDownloadWork.collectAsState()
    val manualExtractionWork = viewModel.manualExtractionWork.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Your Enrolment: ${enrolmentId.value} started on ${dateFromTimestamp(studyStarted.value)} ended: ${studyEnded.value}")
        Text("Cached Questionnaires: ${questionnaires.value.map { it.questionnaire.name + " (" + it.questionnaire.id + ")" }}")
        Text("Items not yet synced: ${unsyncedLogDataCount.value}")
        if (lastLogDataItem.value != null) {
            Text("Last Log Data Item: ${dateFromTimestamp(lastLogDataItem.value!!.timestamp)} ${lastLogDataItem.value?.data}")
        } else {
            Text("No log data found")
        }

        if (currentStudyPhase.value != null) {
            Text("Current Phase: ${currentStudyPhase.value?.fromDay}-${currentStudyPhase.value?.durationDays} ${currentStudyPhase.value?.name}")
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

        Button(
            onClick = {
                viewModel.schedulePendingQuestionnaireSync()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sync questionnaires now")
        }

        Button(
            onClick = {
                viewModel.exportDatabase(context)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = exportStatus.value !is ExportStatus.Exporting
        ) {
            when (exportStatus.value) {
                is ExportStatus.Exporting -> Text("Exporting...")
                is ExportStatus.Success -> Text("Export Database (Success!)")
                is ExportStatus.Error -> Text("Export Database (Error)")
                else -> Text("Export Database")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.pushNotificationTrigger()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add notification trigger")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.scheduleStudyEnd()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Schedule Study End")
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!sensorRunning.value) {
            Button(
                onClick = {
                    viewModel.startConversationRecording(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Conversation Sensor")
            }
        } else {
            Text("Conversation Sensor is running, needs to be manually stopped")
            Button(
                onClick = {
                    viewModel.stopConversationRecording(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Stop Conversation Sensor")
            }
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

        extraction.value?.let { state ->
            Spacer(modifier = Modifier.height(16.dp))
            ContentExtractionDebugSection(
                state = state,
                downloadWork = modelDownloadWork.value,
                manualRunWork = manualExtractionWork.value,
                onDownloadNow = viewModel::downloadModelNow,
                onRunNow = viewModel::runExtractionNow,
                onRefresh = viewModel::refreshExtraction
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Screenshot samples: ${screenshots.value.size}")
        screenshots.value.groupBy { it.periodEnd }.forEach { (periodEnd, periodPreviews) ->
            Text("Period until ${dateFromTimestamp(periodEnd)}", modifier = Modifier.padding(top = 16.dp))
            Button(
                onClick = {
                    viewModel.requestScreenshotRating(periodEnd)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rate this period")
            }
            periodPreviews.groupBy { it.packageName }.forEach { (packageName, previews) ->
                Text(packageName, modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    previews.forEach { preview ->
                        Column {
                            if (preview.image != null) {
                                Image(
                                    bitmap = preview.image,
                                    contentDescription = preview.caption,
                                    modifier = Modifier.height(240.dp)
                                )
                            } else {
                                Text("(replaced)")
                            }
                            Text(preview.caption, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
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