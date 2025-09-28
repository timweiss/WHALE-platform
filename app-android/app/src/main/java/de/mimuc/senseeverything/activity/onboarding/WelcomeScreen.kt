package de.mimuc.senseeverything.activity.onboarding

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.loadStudyByEnrolmentKey
import de.mimuc.senseeverything.data.DataStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WelcomeScreenViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager
) : AndroidViewModel(application) {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    private val _errorCode = MutableStateFlow("")
    val errorCode: StateFlow<String> get() = _errorCode

    private val _showErrorDialog = MutableStateFlow(false)
    val showErrorDialog: StateFlow<Boolean> get() = _showErrorDialog

    fun fetchStudyInfo(context: Context, enrolmentKey: String, continueOnSuccess: () -> Unit = {}) {
        _isLoading.value = true
        val client = ApiClient.Companion.getInstance(context)

        viewModelScope.launch {
            val study = loadStudyByEnrolmentKey(client, enrolmentKey)

            if (study != null) {
                dataStoreManager.saveStudy(study)
                dataStoreManager.saveStudyId(study.id)
                continueOnSuccess()
            } else {
                _errorCode.value = "not_found"
                _showErrorDialog.value = true
                _isLoading.value = false
                return@launch
            }
        }
    }

    fun closeError() {
        _showErrorDialog.value = false
        _errorCode.value = ""
    }
}

@Composable
fun WelcomeScreen(nextStep: () -> Unit, innerPadding: PaddingValues, viewModel: WelcomeScreenViewModel = viewModel()) {
    val context = LocalContext.current
    val enrolmentKeyState = remember { mutableStateOf("") }
    val isLoading = viewModel.isLoading.collectAsState()
    val errorCode = viewModel.errorCode.collectAsState()
    val showErrorDialog = viewModel.showErrorDialog.collectAsState()

    Column(
        modifier = Modifier.Companion
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)
    ) {
        Heading(R.drawable.rounded_waving_hand_24, "Home symbol", "Welcome")
        Spacer(modifier = Modifier.Companion.padding(12.dp))

        Text(stringResource(R.string.onboarding_app_explanation))
        Spacer(modifier = Modifier.Companion.padding(16.dp))

        TextField(
            value = enrolmentKeyState.value,
            onValueChange = { enrolmentKeyState.value = it },
            label = { Text("Enrolment Key") },
            modifier = Modifier.Companion.fillMaxWidth()
        )

        Spacer(modifier = Modifier.Companion.padding(8.dp))

        EnrolmentErrorDialog(showErrorDialog.value, errorCode.value, { viewModel.closeError() })

        Row {
            if (isLoading.value) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        viewModel.fetchStudyInfo(context, enrolmentKeyState.value, { nextStep() })
                    },
                    modifier = Modifier.Companion.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.onboarding_continue))
                }
            }
        }
    }
}