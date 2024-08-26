package de.mimuc.senseeverything.activity

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.network.enqueueSensorReadingsUploadWorker
import de.mimuc.senseeverything.network.enqueueUpdateQuestionnaireWorker
import de.mimuc.senseeverything.service.SEApplicationController
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

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager
) : AndroidViewModel(application) {
    enum class Step {
        WELCOME,
        ENTER_STUDY_ID,
        ACCEPT_PERMISSIONS,
        START_STUDY
    }

    private val _step = MutableStateFlow(Step.WELCOME)
    val step: StateFlow<Step> get() = _step

    fun nextStep() {
        _step.value = when (_step.value) {
            Step.WELCOME -> Step.ENTER_STUDY_ID
            Step.ENTER_STUDY_ID -> Step.ACCEPT_PERMISSIONS
            Step.ACCEPT_PERMISSIONS -> Step.START_STUDY
            Step.START_STUDY -> Step.WELCOME
        }
    }

    fun finishOnboarding(context: Context) {
        // save that we've finished onboarding
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
                        OnboardingViewModel.Step.WELCOME -> Text("Welcome")
                        OnboardingViewModel.Step.ENTER_STUDY_ID -> Text("Join Study")
                        OnboardingViewModel.Step.ACCEPT_PERMISSIONS -> Text("Permissions")
                        OnboardingViewModel.Step.START_STUDY -> Text("Start Study")
                    }
                }
            )
        }
    ) { innerPadding ->
        when (step.value) {
            OnboardingViewModel.Step.WELCOME -> WelcomeScreen(viewModel::nextStep, innerPadding)
            OnboardingViewModel.Step.ENTER_STUDY_ID -> EnterStudyIdScreen(viewModel::nextStep, innerPadding)
            OnboardingViewModel.Step.ACCEPT_PERMISSIONS -> AcceptPermissionsScreen(viewModel::nextStep, innerPadding)
            OnboardingViewModel.Step.START_STUDY -> StartStudyScreen({ viewModel.finishOnboarding(context) }, innerPadding)
        }
    }
}

@Composable
fun Heading(@DrawableRes id: Int, description: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painter = painterResource(id), contentDescription = description, modifier = Modifier.size(46.dp))
        Spacer(modifier = Modifier.padding(16.dp))
        Text(text, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun WelcomeScreen(nextStep: () -> Unit, innerPadding: PaddingValues) {
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(16.dp)) {
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

    private fun checkPermissions() {
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

        if (Settings.canDrawOverlays(getApplication())) {
            setPermission(Manifest.permission.SYSTEM_ALERT_WINDOW, true)
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
                    Log.d("AcceptPermissionsViewModel", "SYSTEM_ALERT_WINDOW permission already granted")
                }
            }
        } else {
            Log.e("AcceptPermissionsViewModel", "Could not get activity")
        }
    }
}

@Composable
fun AcceptPermissionsScreen(nextStep: () -> Unit, innerPadding: PaddingValues, viewModel: AcceptPermissionsViewModel = viewModel()) {
    val permissions = viewModel.permissions.collectAsState()
    val context = LocalContext.current
    val allAccepted = permissions.value.values.all { it }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(16.dp)) {
        Heading(id = R.drawable.rounded_key_vertical_24, description = "Lock symbol", text = "Necessary Permissions")
        Spacer(modifier = Modifier.padding(12.dp))
        Text("To participate in the study, we need access to some of your phone's sensors. All data will be pseudonymized and stored securely.")

        Spacer(modifier = Modifier.padding(12.dp))


        // Wake Lock
        Text("Wake Lock: ${permissions.value[Manifest.permission.WAKE_LOCK] ?: false}")
        if (permissions.value[Manifest.permission.WAKE_LOCK] == false) {
            Button(onClick = { viewModel.requestPermission(Manifest.permission.WAKE_LOCK, context) }) {
                Text("Request Permission")
            }
        }
        // Record Audio
        Text("Record Audio: ${permissions.value[Manifest.permission.RECORD_AUDIO] ?: false}")
        if (permissions.value[Manifest.permission.RECORD_AUDIO] == false) {
            Button(onClick = { viewModel.requestPermission(Manifest.permission.RECORD_AUDIO, context) }) {
                Text("Request Permission")
            }
        }
        // Access Wifi
        Text("Access Wifi: ${permissions.value[Manifest.permission.ACCESS_WIFI_STATE] ?: false}")
        if (permissions.value[Manifest.permission.ACCESS_WIFI_STATE] == false) {
            Button(onClick = { viewModel.requestPermission(Manifest.permission.ACCESS_WIFI_STATE, context) }) {
                Text("Request Permission")
            }
        }
        // Bluetooth Scan
        Text("Bluetooth Scan: ${permissions.value[Manifest.permission.BLUETOOTH_SCAN] ?: false}")
        if (permissions.value[Manifest.permission.BLUETOOTH_SCAN] == false) {
            Button(onClick = { viewModel.requestPermission(Manifest.permission.BLUETOOTH_SCAN, context) }) {
                Text("Request Permission")
            }
        }
        // Access Network State
        Text("Access Network State: ${permissions.value[Manifest.permission.ACCESS_NETWORK_STATE] ?: false}")
        if (permissions.value[Manifest.permission.ACCESS_NETWORK_STATE] == false) {
            Button(onClick = { viewModel.requestPermission(Manifest.permission.ACCESS_NETWORK_STATE, context) }) {
                Text("Request Permission")
            }
        }
        // Receive Boot Completed
        Text("Receive Boot Completed: ${permissions.value[Manifest.permission.RECEIVE_BOOT_COMPLETED] ?: false}")
        if (permissions.value[Manifest.permission.RECEIVE_BOOT_COMPLETED] == false) {
            Button(onClick = { viewModel.requestPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED, context) }) {
                Text("Request Permission")
            }
        }
        // Read Phone State
        Text("Read Phone State: ${permissions.value[Manifest.permission.READ_PHONE_STATE] ?: false}")
        if (permissions.value[Manifest.permission.READ_PHONE_STATE] == false) {
            Button(onClick = { viewModel.requestPermission(Manifest.permission.READ_PHONE_STATE, context) }) {
                Text("Request Permission")
            }
        }
        // Foreground Service
        Text("Foreground Service: ${permissions.value[Manifest.permission.FOREGROUND_SERVICE] ?: false}")
        if (permissions.value[Manifest.permission.FOREGROUND_SERVICE] == false) {
            Button(onClick = { viewModel.requestPermission(Manifest.permission.FOREGROUND_SERVICE, context) }) {
                Text("Request Permission")
            }
        }
        // Post Notifications
        Text("Notifications: ${permissions.value[Manifest.permission.POST_NOTIFICATIONS] ?: false}")
        if (permissions.value[Manifest.permission.FOREGROUND_SERVICE] == false) {
            Button(onClick = { viewModel.requestPermission(Manifest.permission.POST_NOTIFICATIONS, context) }) {
                Text("Request Permission")
            }
        }
        // Schedule Exact Alarm
        Text("Schedule Exact Alarm: ${permissions.value[Manifest.permission.SCHEDULE_EXACT_ALARM] ?: false}")
        if (permissions.value[Manifest.permission.SCHEDULE_EXACT_ALARM] == false) {
            Button(onClick = { viewModel.requestPermission(Manifest.permission.SCHEDULE_EXACT_ALARM, context) }) {
                Text("Request Permission")
            }
        }
        // Access Fine Location
        Text("Access Fine Location: ${permissions.value[Manifest.permission.ACCESS_FINE_LOCATION] ?: false}")
        if (permissions.value[Manifest.permission.ACCESS_FINE_LOCATION] == false) {
            Button(onClick = { viewModel.requestPermission(Manifest.permission.ACCESS_FINE_LOCATION, context) }) {
                Text("Request Permission")
            }
        }
        // Background Location
        Text("Background Location: ${permissions.value[Manifest.permission.ACCESS_BACKGROUND_LOCATION] ?: false}")
        if (permissions.value[Manifest.permission.ACCESS_BACKGROUND_LOCATION] == false) {
            Button(onClick = { viewModel.requestPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION, context) }) {
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

        Spacer(modifier = Modifier.padding(16.dp))
        Text("Please accept all permissions to continue. You can pause or stop the study at any time, and have the ability to delete all data collected.")

        Spacer(modifier = Modifier.padding(12.dp))
        Button(onClick = nextStep, enabled = allAccepted) {
            Text("Continue")
        }
    }
}

@HiltViewModel
class StartStudyViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager
) : AndroidViewModel(application) {
    fun scheduleTasks(context: Context, finish: () -> Unit) {
        viewModelScope.launch {
            getApplication<SEApplicationController>().esmHandler.schedulePeriodicQuestionnaires(context, dataStoreManager)
            val token = dataStoreManager.tokenFlow.first()
            enqueueSensorReadingsUploadWorker(context, token)
            enqueueUpdateQuestionnaireWorker(context)
            finish()
        }
    }
}

@Composable
fun StartStudyScreen(finish: () -> Unit, innerPadding: PaddingValues, viewModel: StartStudyViewModel = viewModel()) {
    val context = LocalContext.current

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(16.dp)) {
        Heading(id = R.drawable.rounded_sentiment_very_satisfied_24, description = "Happy face", text = "All set!")
        Spacer(modifier = Modifier.padding(12.dp))
        Text("Everything has been set up, you can now proceed and start your participation in the study.")

        Spacer(modifier = Modifier.padding(16.dp))
        Button(onClick = {
            viewModel.scheduleTasks(context, finish)
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Start Study")
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