package de.mimuc.senseeverything.activity

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.loadStudy
import de.mimuc.senseeverything.api.model.Study
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.service.LogService
import de.mimuc.senseeverything.service.SEApplicationController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
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
    private val dataStoreManager: DataStoreManager
) : AndroidViewModel(application) {
    private val _isEnrolled = MutableStateFlow(false)
    val isEnrolled: StateFlow<Boolean> get() = _isEnrolled

    private val _isStudyRunning = MutableStateFlow(false)
    val isStudyRunning: StateFlow<Boolean> get() = _isStudyRunning

    private val _isStudyPaused = MutableStateFlow(false)
    val isStudyPaused: StateFlow<Boolean> get() = _isStudyPaused

    private val _currentDay = MutableStateFlow(0)
    val currentDay: StateFlow<Int> get() = _currentDay

    private val _study = MutableStateFlow(Study("", -1, "", -1))
    val study: StateFlow<Study> get() = _study

    init {
        load()
    }

    fun load() {
        checkEnrolment()
        checkIfStudyIsRunning()
        getStudyDetails()
    }

    fun startOnboarding() {
        val intent = Intent(getApplication(), Onboarding::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        getApplication<Application>().startActivity(intent)
    }

    fun resumeStudy(context: Context) {
        if (isStudyRunning.value) {
            SEApplicationController.getInstance().samplingManager.resumeSampling(context)
        } else {
            SEApplicationController.getInstance().samplingManager.startSampling(context)
        }

        viewModelScope.launch {
            delay(1000)
            checkIfStudyIsRunning()
        }
    }

    fun pauseStudy(context: Context) {
        SEApplicationController.getInstance().samplingManager.pauseSampling(context)
        viewModelScope.launch {
            delay(1000)
            checkIfStudyIsRunning()
        }
    }

    fun openSettings(context: Context) {
        val intent = Intent(getApplication(), StudyEnrolment::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
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

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun getStudyDetails() {
        viewModelScope.launch {
            setCurrentStudyDay()
            val studyId = dataStoreManager.studyIdFlow.first()
            val api = ApiClient.getInstance(getApplication())
            val study = loadStudy(api, studyId)
            if (study != null) {
                _study.value = study
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyHome(viewModel: StudyHomeViewModel = viewModel()) {
    val isEnrolled = viewModel.isEnrolled.collectAsState()
    val isStudyRunning = viewModel.isStudyRunning.collectAsState()
    val isStudyPaused = viewModel.isStudyPaused.collectAsState()
    val currentDay = viewModel.currentDay.collectAsState()
    val study = viewModel.study.collectAsState()
    val context = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = { Text("WHALE") }
    )}, modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(modifier = Modifier
            .padding(innerPadding)
            .padding(16.dp)) {
            Text("Welcome to WHALE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontSize = 36.sp)

            Spacer(modifier = Modifier.height(16.dp))

            if (isEnrolled.value) {
                Text("Day ${currentDay.value} of ${study.value.durationDays}")

                StudyActivity(isRunning = isStudyRunning.value, isPaused = isStudyPaused.value, resumeStudy = { viewModel.resumeStudy(context) }, pauseStudy = { viewModel.pauseStudy(context) })
                SpacerLine(paddingValues = PaddingValues(vertical = 12.dp), width = 96.dp)
                FilledTonalButton(onClick = { viewModel.openSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Study Settings")
                }
            } else {
                Button(onClick = { viewModel.startOnboarding() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Enroll in Study")
                }
            }
        }
        LaunchedEffect(lifecycleState) {
            when(lifecycleState) {
                androidx.lifecycle.Lifecycle.State.RESUMED -> viewModel.load()
                else -> {}
            }
        }
    }
}

@Composable
fun StudyActivity(isRunning: Boolean, isPaused: Boolean, resumeStudy: () -> Unit, pauseStudy: () -> Unit) {
    if (isRunning) {
        Column {
            if (!isPaused) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIndicator(color = Color.hsl(80f, 1f, 0.33f, 1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Study is running")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { pauseStudy() }) {
                    Text(
                        "Pause Study",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusIndicator(color = Color.hsl(80f, 1f, 0.33f, 1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Study is paused")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { resumeStudy() }) {
                    Text(
                        "Resume Study",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    } else {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIndicator(color = Color.hsl(31f, 1f, 0.5f, 1f))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Study is not running")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { resumeStudy() }) {
                Text("Resume Study", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun StatusIndicator(color: Color) {
    Box(modifier = Modifier
        .size(16.dp)
        .clip(CircleShape)
        .background(color))
}

@Composable
fun SpacerLine(paddingValues: PaddingValues, width: Dp) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(paddingValues), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier
            .width(width)
            .height(1.dp)
            .background(Color.LightGray))
    }
}

@Preview(showBackground = true)
@Composable
fun StudyHomePreview() {
    AppandroidTheme {
        StudyHome()
    }
}