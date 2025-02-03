package de.mimuc.senseeverything.activity

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.fetchAndPersistQuestionnaires
import de.mimuc.senseeverything.api.loadStudy
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.helpers.generateSensitiveDataSalt
import de.mimuc.senseeverything.service.AccessibilityLogService
import de.mimuc.senseeverything.service.SEApplicationController
import de.mimuc.senseeverything.workers.enqueueClearInteractionWidgetTimeBucketsWorker
import de.mimuc.senseeverything.workers.enqueueEndStudyWorker
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
                        OnboardingStep.WELCOME -> Text("Welcome")
                        OnboardingStep.ENTER_STUDY_ID -> Text("Join Study")
                        OnboardingStep.ACCEPT_PERMISSIONS -> Text("Permissions")
                        OnboardingStep.START_STUDY -> Text("Start Study")
                        OnboardingStep.COMPLETED -> Text("Completed")
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

        Text("We can enter some text here to explain the app and so on and so on")
        Spacer(modifier = Modifier.padding(16.dp))

        Row {
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = nextStep) {
                Text("Continue")
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
            text = "Necessary Permissions"
        )
        Spacer(modifier = Modifier.padding(12.dp))
        Text("To participate in the study, we need access to some of your phone's sensors. All data will be pseudonymized and stored securely.")

        Spacer(modifier = Modifier.padding(12.dp))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            // Wake Lock
            Text("Wake Lock: ${permissions.value[Manifest.permission.WAKE_LOCK] ?: false}")
            if (permissions.value[Manifest.permission.WAKE_LOCK] == false) {
                Button(onClick = {
                    viewModel.requestPermission(
                        Manifest.permission.WAKE_LOCK,
                        context
                    )
                }) {
                    Text("Request Permission")
                }
            }
            // Record Audio
            Text("Record Audio: ${permissions.value[Manifest.permission.RECORD_AUDIO] ?: false}")
            if (permissions.value[Manifest.permission.RECORD_AUDIO] == false) {
                Button(onClick = {
                    viewModel.requestPermission(
                        Manifest.permission.RECORD_AUDIO,
                        context
                    )
                }) {
                    Text("Request Permission")
                }
            }
            // Access Wifi
            Text("Access Wifi: ${permissions.value[Manifest.permission.ACCESS_WIFI_STATE] ?: false}")
            if (permissions.value[Manifest.permission.ACCESS_WIFI_STATE] == false) {
                Button(onClick = {
                    viewModel.requestPermission(
                        Manifest.permission.ACCESS_WIFI_STATE,
                        context
                    )
                }) {
                    Text("Request Permission")
                }
            }
            // Bluetooth Scan
            Text("Bluetooth Scan: ${permissions.value[Manifest.permission.BLUETOOTH_SCAN] ?: false}")
            if (permissions.value[Manifest.permission.BLUETOOTH_SCAN] == false) {
                Button(onClick = {
                    viewModel.requestPermission(
                        Manifest.permission.BLUETOOTH_SCAN,
                        context
                    )
                }) {
                    Text("Request Permission")
                }
            }
            // Access Network State
            Text("Access Network State: ${permissions.value[Manifest.permission.ACCESS_NETWORK_STATE] ?: false}")
            if (permissions.value[Manifest.permission.ACCESS_NETWORK_STATE] == false) {
                Button(onClick = {
                    viewModel.requestPermission(
                        Manifest.permission.ACCESS_NETWORK_STATE,
                        context
                    )
                }) {
                    Text("Request Permission")
                }
            }
            // Receive Boot Completed
            Text("Receive Boot Completed: ${permissions.value[Manifest.permission.RECEIVE_BOOT_COMPLETED] ?: false}")
            if (permissions.value[Manifest.permission.RECEIVE_BOOT_COMPLETED] == false) {
                Button(onClick = {
                    viewModel.requestPermission(
                        Manifest.permission.RECEIVE_BOOT_COMPLETED,
                        context
                    )
                }) {
                    Text("Request Permission")
                }
            }
            // Read Phone State
            Text("Read Phone State: ${permissions.value[Manifest.permission.READ_PHONE_STATE] ?: false}")
            if (permissions.value[Manifest.permission.READ_PHONE_STATE] == false) {
                Button(onClick = {
                    viewModel.requestPermission(
                        Manifest.permission.READ_PHONE_STATE,
                        context
                    )
                }) {
                    Text("Request Permission")
                }
            }
            // Foreground Service
            Text("Foreground Service: ${permissions.value[Manifest.permission.FOREGROUND_SERVICE] ?: false}")
            if (permissions.value[Manifest.permission.FOREGROUND_SERVICE] == false) {
                Button(onClick = {
                    viewModel.requestPermission(
                        Manifest.permission.FOREGROUND_SERVICE,
                        context
                    )
                }) {
                    Text("Request Permission")
                }
            }
            // Post Notifications
            Text("Notifications: ${permissions.value[Manifest.permission.POST_NOTIFICATIONS] ?: false}")
            if (permissions.value[Manifest.permission.FOREGROUND_SERVICE] == false) {
                Button(onClick = {
                    viewModel.requestPermission(
                        Manifest.permission.POST_NOTIFICATIONS,
                        context
                    )
                }) {
                    Text("Request Permission")
                }
            }
            // Schedule Exact Alarm
            Text("Schedule Exact Alarm: ${permissions.value[Manifest.permission.SCHEDULE_EXACT_ALARM] ?: false}")
            if (permissions.value[Manifest.permission.SCHEDULE_EXACT_ALARM] == false) {
                Button(onClick = {
                    viewModel.requestExactAlarmPermission(
                        context
                    )
                }) {
                    Text("Request Permission")
                }
            }
            // Access Fine Location
            Text("Access Fine Location: ${permissions.value[Manifest.permission.ACCESS_FINE_LOCATION] ?: false}")
            if (permissions.value[Manifest.permission.ACCESS_FINE_LOCATION] == false) {
                Button(onClick = {
                    viewModel.requestPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        context
                    )
                }) {
                    Text("Request Permission")
                }
            }
            // Background Location
            Text("Background Location: ${permissions.value[Manifest.permission.ACCESS_BACKGROUND_LOCATION] ?: false}")
            if (permissions.value[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == false) {
                Button(onClick = {
                    viewModel.requestPermission(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                        context
                    )
                }) {
                    Text("Request Permission")
                }
            }
            // System Alert Window
            Text("System Alert Window: ${permissions.value[Manifest.permission.SYSTEM_ALERT_WINDOW] ?: false}")
            if (permissions.value[Manifest.permission.SYSTEM_ALERT_WINDOW] == false) {
                Button(onClick = { viewModel.requestSystemWindowPermission(context) }) {
                    Text("Request Permission")
                }
            }
            // Notification Listener Service
            Text("Notification Access: ${permissions.value[Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE] ?: false}")
            if (permissions.value[Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE] == false) {
                Button(onClick = { viewModel.requestNotificationListenerPermission(context) }) {
                    Text("Request Permission")
                }
            }
            // Accessibility Events
            Text("Accessibility Events: ${permissions.value[Manifest.permission.BIND_ACCESSIBILITY_SERVICE] ?: false}")
            if (permissions.value[Manifest.permission.BIND_ACCESSIBILITY_SERVICE] == false) {
                Button(onClick = { viewModel.requestAccessibilityServicePermission(context) }) {
                    Text("Request Permission")
                }
            }
            // Power Exemptions
            Text("Power Management: ${permissions.value[Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] ?: false}")
            if (permissions.value[Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] == false) {
                Button(onClick = { viewModel.requestPowerExemptionPermission(context) }) {
                    Text("Request Permission")
                }
            }

            Spacer(modifier = Modifier.padding(16.dp))
            Text("Please accept all permissions to continue. You can pause or stop the study at any time, and have the ability to delete all data collected.")

            Spacer(modifier = Modifier.padding(12.dp))
            Button(onClick = nextStep, enabled = allAccepted) {
                Text("Continue")
            }
        }

        LaunchedEffect(lifecycleState) {
            when (lifecycleState) {
                androidx.lifecycle.Lifecycle.State.RESUMED -> viewModel.checkPermissions()
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

                fetchAndPersistQuestionnaires(studyId, dataStoreManager, api)

                getApplication<SEApplicationController>().esmHandler.schedulePeriodicQuestionnaires(
                    context,
                    dataStoreManager,
                    database
                )

                val token = dataStoreManager.tokenFlow.first()
                enqueueSensorReadingsUploadWorker(context, token)
                enqueueUpdateQuestionnaireWorker(context)
                enqueueClearInteractionWidgetTimeBucketsWorker(context)
                enqueueEndStudyWorker(context, study.durationDays)

                dataStoreManager.saveTimestampStudyStarted(System.currentTimeMillis())
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
            text = "All set!"
        )
        Spacer(modifier = Modifier.padding(12.dp))
        Text(stringResource(R.string.everything_set_up))

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
                Text("Finalizing setup, this may take a while...")
            }
        }

        Spacer(modifier = Modifier.padding(16.dp))
        Button(onClick = {
            viewModel.prepareStudy(context, finish)
        }, modifier = Modifier.fillMaxWidth(), enabled = !pending.value) {
            Text(stringResource(R.string.start_study))
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