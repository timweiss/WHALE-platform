package de.mimuc.senseeverything.activity.onboarding

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.activity.getActivity
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.data.DataStoreManager
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
    DATA_PROTECTION,
    ACCEPT_PERMISSIONS,
    START_STUDY,
    COMPLETED
}

fun OnboardingStep.startedButIncomplete(): Boolean {
    return when (this) {
        OnboardingStep.WELCOME -> false
        OnboardingStep.DATA_PROTECTION -> false
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
            OnboardingStep.WELCOME -> OnboardingStep.DATA_PROTECTION
            OnboardingStep.DATA_PROTECTION -> OnboardingStep.ACCEPT_PERMISSIONS
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
                        OnboardingStep.DATA_PROTECTION -> Text(stringResource(R.string.onboarding_data_protection))
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
            OnboardingStep.DATA_PROTECTION -> DataProtectionScreen(viewModel::nextStep, innerPadding)
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

// Step 1: Welcome + Enter Study ID
// Step 2: Data Protection
// Step 3: Accept all permissions
// Step 4: Start the study

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AppandroidTheme {
        OnboardingView()
    }
}

