package de.mimuc.senseeverything.activity.onboarding

import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.OPSTR_GET_USAGE_STATS
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.provider.Settings.SettingNotFoundException
import android.text.TextUtils.SimpleStringSplitter
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.EnrolmentScreen
import de.mimuc.senseeverything.activity.getActivity
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.fetchAndPersistQuestionnaires
import de.mimuc.senseeverything.api.loadStudy
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.StudyState
import de.mimuc.senseeverything.data.persistQuestionnaireElementContent
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.helpers.generateSensitiveDataSalt
import de.mimuc.senseeverything.helpers.isServiceRunning
import de.mimuc.senseeverything.service.AccessibilityLogService
import de.mimuc.senseeverything.service.LogService
import de.mimuc.senseeverything.service.SEApplicationController
import de.mimuc.senseeverything.study.schedulePhaseChanges
import de.mimuc.senseeverything.study.scheduleStudyEndAlarm
import de.mimuc.senseeverything.workers.enqueueClearInteractionWidgetTimeBucketsWorker
import de.mimuc.senseeverything.workers.enqueueSensorReadingsUploadWorker
import de.mimuc.senseeverything.workers.enqueueUpdateQuestionnaireWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class Onboarding : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppandroidTheme {
                OnboardingView()
            }
        }
    }
}

enum class OnboardingStep {
    WELCOME,
    ENTER_STUDY_ID,
    ACCEPT_PERMISSIONS,
    START_STUDY,
    COMPLETED
}

fun OnboardingStep.startedButIncomplete(): Boolean {
    return when (this) {
        OnboardingStep.WELCOME -> false
        OnboardingStep.ENTER_STUDY_ID -> true
        OnboardingStep.ACCEPT_PERMISSIONS -> true
        OnboardingStep.START_STUDY -> true
        OnboardingStep.COMPLETED -> false
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager
) : AndroidViewModel(application) {
    private val _step = MutableStateFlow(OnboardingStep.WELCOME)
    val step: StateFlow<OnboardingStep> get() = _step

    init {
        loadStep()
    }

    private fun loadStep() {
        viewModelScope.launch {
            _step.value = dataStoreManager.onboardingStepFlow.first()
        }
    }

    fun nextStep() {
        _step.value = when (_step.value) {
            OnboardingStep.WELCOME -> OnboardingStep.ENTER_STUDY_ID
            OnboardingStep.ENTER_STUDY_ID -> OnboardingStep.ACCEPT_PERMISSIONS
            OnboardingStep.ACCEPT_PERMISSIONS -> OnboardingStep.START_STUDY
            OnboardingStep.START_STUDY -> OnboardingStep.COMPLETED
            OnboardingStep.COMPLETED -> OnboardingStep.COMPLETED
        }

        viewModelScope.launch {
            dataStoreManager.saveOnboardingStep(_step.value)
        }
    }

    fun finishOnboarding(context: Context) {
        // save that we've finished onboarding
        viewModelScope.launch {
            dataStoreManager.saveOnboardingStep(OnboardingStep.COMPLETED)
        }
        // pop and open the mainactivity again
        val activity = context.getActivity()
        if (activity != null) {
            activity.finish()
        } else {
            Log.e("OnboardingViewModel", "Could not get activity")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingView(viewModel: OnboardingViewModel = viewModel()) {
    val step = viewModel.step.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    when (step.value) {
                        OnboardingStep.WELCOME -> Text(stringResource(R.string.onboarding_welcome))
                        OnboardingStep.ENTER_STUDY_ID -> Text(stringResource(R.string.onboarding_join_study))
                        OnboardingStep.ACCEPT_PERMISSIONS -> Text(stringResource(R.string.onboarding_permissions))
                        OnboardingStep.START_STUDY -> Text(stringResource(R.string.onboarding_start_study))
                        OnboardingStep.COMPLETED -> Text(stringResource(R.string.onboarding_completed))
                    }
                }
            )
        }
    ) { innerPadding ->
        when (step.value) {
            OnboardingStep.WELCOME -> WelcomeScreen(viewModel::nextStep, innerPadding)
            OnboardingStep.ENTER_STUDY_ID -> EnterStudyIdScreen(viewModel::nextStep, innerPadding)
            OnboardingStep.ACCEPT_PERMISSIONS -> AcceptPermissionsScreen(
                viewModel::nextStep,
                innerPadding
            )

            OnboardingStep.START_STUDY -> StartStudyScreen(
                { viewModel.finishOnboarding(context) },
                innerPadding
            )

            OnboardingStep.COMPLETED -> {}
        }
    }
}

@Composable
fun Heading(@DrawableRes id: Int, description: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id),
            contentDescription = description,
            modifier = Modifier.size(46.dp)
        )
        Spacer(modifier = Modifier.padding(16.dp))
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun WelcomeScreen(nextStep: () -> Unit, innerPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)
    ) {
        Heading(R.drawable.rounded_waving_hand_24, "Home symbol", "Welcome")
        Spacer(modifier = Modifier.padding(12.dp))

        Text(stringResource(R.string.onboarding_app_explanation))
        Spacer(modifier = Modifier.padding(16.dp))

        Row {
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = nextStep) {
                Text(stringResource(R.string.onboarding_continue))
            }
        }
    }
}

@Composable
fun EnterStudyIdScreen(nextStep: () -> Unit, innerPadding: PaddingValues) {
    EnrolmentScreen(innerPadding = innerPadding, finishedEnrolment = nextStep)
}

@HiltViewModel
class AcceptPermissionsViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager
) : AndroidViewModel(application) {
    private val _permissions = MutableStateFlow(emptyMap<String, Boolean>())
    val permissions: StateFlow<Map<String, Boolean>> get() = _permissions

    init {
        checkPermissions()
    }

    fun checkPermissions() {
        _permissions.value = mutableMapOf()

        checkAndSetPermission(Manifest.permission.WAKE_LOCK)
        checkAndSetPermission(Manifest.permission.RECORD_AUDIO)
        checkAndSetPermission(Manifest.permission.ACCESS_WIFI_STATE)
        checkAndSetPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED)
        checkAndSetPermission(Manifest.permission.READ_PHONE_STATE)
        checkAndSetPermission(Manifest.permission.ACCESS_NETWORK_STATE)
        checkAndSetPermission(Manifest.permission.FOREGROUND_SERVICE)
        checkAndSetPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        checkAndSetPermission(Manifest.permission.SYSTEM_ALERT_WINDOW)
        checkAndSetPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        checkAndSetPermission(Manifest.permission.BLUETOOTH_SCAN)
        checkAndSetPermission(Manifest.permission.POST_NOTIFICATIONS)
        checkAndSetPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
        checkAndSetPermission(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
        checkAndSetPermission(Manifest.permission.ACTIVITY_RECOGNITION)

        setPermission(Manifest.permission.PACKAGE_USAGE_STATS, hasAccessToUsageStats())

        if (Settings.canDrawOverlays(getApplication())) {
            setPermission(Manifest.permission.SYSTEM_ALERT_WINDOW, true)
        }

        if (NotificationManagerCompat.getEnabledListenerPackages(getApplication())
                .contains(getApplication<SEApplicationController>().packageName)
        ) {
            setPermission(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE, true)
        }

        setPermission(
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
            isAccessibilityServiceEnabled(getApplication())
        )


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager =
                getApplication<SEApplicationController>().getSystemService(Context.ALARM_SERVICE) as AlarmManager?
            if (alarmManager != null) {
                setPermission(
                    Manifest.permission.SCHEDULE_EXACT_ALARM,
                    alarmManager.canScheduleExactAlarms()
                )
            }
        }

        val powerManager =
            getApplication<SEApplicationController>().getSystemService(Context.POWER_SERVICE) as PowerManager?
        val packageName = getApplication<SEApplicationController>().packageName
        if (powerManager != null && powerManager.isIgnoringBatteryOptimizations(packageName)) {
            setPermission(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, true)
        } else {
            setPermission(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, false)
        }
    }

    private fun checkAndSetPermission(permission: String) {
        _permissions.value = _permissions.value.toMutableMap().apply {
            val checked = checkSelfPermission(getApplication(), permission)
            put(permission, checked == PackageManager.PERMISSION_GRANTED)
        }
    }

    private fun setPermission(permission: String, granted: Boolean) {
        _permissions.value = _permissions.value.toMutableMap().apply {
            put(permission, granted)
        }
    }

    fun requestPermission(permission: String, context: Context) {
        val activity = context.getActivity()
        if (activity != null) {
            ActivityCompat.requestPermissions(activity, arrayOf(permission), 1)
            checkAndSetPermission(permission)
        } else {
            Log.e("AcceptPermissionsViewModel", "Could not get activity")
        }
    }

    fun requestSystemWindowPermission(context: Context) {
        val activity = context.getActivity()
        if (activity != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(activity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,

                        Uri.parse("package:" + context.packageName)
                    )
                    startActivityForResult(activity, intent, 1001, null)
                } else {
                    _permissions.value = _permissions.value.toMutableMap().apply {
                        put(Manifest.permission.SYSTEM_ALERT_WINDOW, true)
                    }
                    Log.d(
                        "AcceptPermissionsViewModel",
                        "SYSTEM_ALERT_WINDOW permission already granted"
                    )
                }
            }
        } else {
            Log.e("AcceptPermissionsViewModel", "Could not get activity")
        }
    }

    fun requestNotificationListenerPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun requestAccessibilityServicePermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun requestUsageStatsPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun requestExactAlarmPermission(context: Context) {
        // permission is only not granted above API 31
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager?
            if (alarmManager != null) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
    }

    fun requestPowerExemptionPermission(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager?
        val packageName = context.packageName
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(Uri.parse("package:$packageName"))
        )
    }

    private fun isAccessibilityServiceEnabled(context: Context): Boolean {
        var accessibilityEnabled = 0

        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: SettingNotFoundException) {
            Log.d("Onboarding", e.toString())
        }

        val mStringColonSplitter = SimpleStringSplitter(':')

        if (accessibilityEnabled == 1) {
            val settingValue =
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            if (settingValue != null) {
                mStringColonSplitter.setString(settingValue)
                while (mStringColonSplitter.hasNext()) {
                    val accessibilityService = mStringColonSplitter.next()
                    if (accessibilityService.equals(
                            AccessibilityLogService.SERVICE,
                            ignoreCase = true
                        )
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun hasAccessToUsageStats(): Boolean {
        val application = getApplication<SEApplicationController>()
        val appOps = application.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(OPSTR_GET_USAGE_STATS, Process.myUid(), application.packageName);
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return (application.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED);
        } else {
            return (mode == MODE_ALLOWED);
        }
    }
}

@Composable
fun AcceptPermissionsScreen(
    nextStep: () -> Unit,
    innerPadding: PaddingValues,
    viewModel: AcceptPermissionsViewModel = viewModel()
) {
    val permissions = viewModel.permissions.collectAsState()
    val context = LocalContext.current
    val allAccepted = permissions.value.values.all { it }

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)
    ) {
        Heading(
            id = R.drawable.rounded_key_vertical_24,
            description = "Lock symbol",
            text = stringResource(R.string.onboarding_permissions_necessary)
        )
        Spacer(modifier = Modifier.padding(12.dp))
        Text(stringResource(R.string.onboarding_permissions_hint))

        Spacer(modifier = Modifier.padding(12.dp))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            // Standard permissions
            StandardPermissionItem(
                label = "Wake Lock",
                permission = Manifest.permission.WAKE_LOCK,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Record Audio",
                permission = Manifest.permission.RECORD_AUDIO,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Access Wifi",
                permission = Manifest.permission.ACCESS_WIFI_STATE,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Bluetooth Scan",
                permission = Manifest.permission.BLUETOOTH_SCAN,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Access Network State",
                permission = Manifest.permission.ACCESS_NETWORK_STATE,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Receive Boot Completed",
                permission = Manifest.permission.RECEIVE_BOOT_COMPLETED,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Read Phone State",
                permission = Manifest.permission.READ_PHONE_STATE,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Foreground Service",
                permission = Manifest.permission.FOREGROUND_SERVICE,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Notifications",
                permission = Manifest.permission.POST_NOTIFICATIONS,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Access Fine Location",
                permission = Manifest.permission.ACCESS_FINE_LOCATION,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Background Location",
                permission = Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            StandardPermissionItem(
                label = "Activity Recognition",
                permission = Manifest.permission.ACTIVITY_RECOGNITION,
                permissions = permissions.value,
                viewModel = viewModel,
                context = context
            )

            // Special permissions with custom request methods
            SpecialPermissionItem(
                label = "Schedule Exact Alarm",
                permission = Manifest.permission.SCHEDULE_EXACT_ALARM,
                permissions = permissions.value,
                onRequestPermission = { viewModel.requestExactAlarmPermission(context) }
            )

            SpecialPermissionItem(
                label = "System Alert Window",
                permission = Manifest.permission.SYSTEM_ALERT_WINDOW,
                permissions = permissions.value,
                onRequestPermission = { viewModel.requestSystemWindowPermission(context) }
            )

            SpecialPermissionItem(
                label = "Notification Access",
                permission = Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE,
                permissions = permissions.value,
                onRequestPermission = { viewModel.requestNotificationListenerPermission(context) }
            )

            SpecialPermissionItem(
                label = "Accessibility Events",
                permission = Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
                permissions = permissions.value,
                onRequestPermission = { viewModel.requestAccessibilityServicePermission(context) }
            )

            SpecialPermissionItem(
                label = "Usage Statistics",
                permission = Manifest.permission.PACKAGE_USAGE_STATS,
                permissions = permissions.value,
                onRequestPermission = { viewModel.requestUsageStatsPermission(context) }
            )

            SpecialPermissionItem(
                label = "Power Management",
                permission = Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                permissions = permissions.value,
                onRequestPermission = { viewModel.requestPowerExemptionPermission(context) }
            )

            Spacer(modifier = Modifier.padding(16.dp))
            Text(stringResource(R.string.onboarding_permissions_accept_all_hint))

            Spacer(modifier = Modifier.padding(12.dp))
            Button(onClick = nextStep, enabled = allAccepted) {
                Text(stringResource(R.string.onboarding_continue))
            }
        }

        LaunchedEffect(lifecycleState) {
            when (lifecycleState) {
                Lifecycle.State.RESUMED -> viewModel.checkPermissions()
                else -> {}
            }
        }
    }
}

@HiltViewModel
class StartStudyViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase,
) : AndroidViewModel(application) {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> get() = _pending

    fun prepareStudy(context: Context, finish: () -> Unit) {
        viewModelScope.launch {
            _pending.value = true

            val studyId = dataStoreManager.studyIdFlow.first()
            val api = ApiClient.getInstance(getApplication())
            val study = loadStudy(api, studyId)
            if (study != null) {
                Log.d("StartStudyViewModel", "Loaded study: $study")
                dataStoreManager.saveStudy(study)
                dataStoreManager.saveStudyDays(study.durationDays)
                dataStoreManager.saveRemainingStudyDays(study.durationDays)
                dataStoreManager.saveSensitiveDataSalt(generateSensitiveDataSalt())
                dataStoreManager.saveStudyState(StudyState.RUNNING)

                try {
                    val questionnaires = fetchAndPersistQuestionnaires(studyId, dataStoreManager, api)
                    persistQuestionnaireElementContent(context, questionnaires)
                } catch (exception: Exception) {
                    Log.e("StartStudyViewModel", "Could not load questionnaires", exception)
                }

                getApplication<SEApplicationController>().esmHandler.schedulePeriodicQuestionnaires(
                    context,
                    dataStoreManager,
                    database
                )

                getApplication<SEApplicationController>().esmHandler.scheduleOneTimeQuestionnaires(
                    context,
                    dataStoreManager,
                    database
                )

                val token = dataStoreManager.tokenFlow.first()
                enqueueSensorReadingsUploadWorker(context, token)
                enqueueUpdateQuestionnaireWorker(context)
                enqueueClearInteractionWidgetTimeBucketsWorker(context)
                scheduleStudyEndAlarm(context, study.durationDays)

                val startedTimestamp = System.currentTimeMillis()
                dataStoreManager.saveTimestampStudyStarted(startedTimestamp)
                schedulePhaseChanges(context, startedTimestamp, dataStoreManager.studyPhasesFlow.first())

                // automatically start data collection
                if (!isServiceRunning(LogService::class.java)) {
                    SEApplicationController.getInstance().samplingManager.startSampling(context.applicationContext)
                }
            } else {
                Log.e("StartStudyViewModel", "Could not load study")
            }

            _pending.value = false
            finish()
        }
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

// Step 1: Welcome
// Step 2: Enter Study ID
// Step 3: Accept all permissions
// Step 4: Start the study

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AppandroidTheme {
        OnboardingView()
    }
}

