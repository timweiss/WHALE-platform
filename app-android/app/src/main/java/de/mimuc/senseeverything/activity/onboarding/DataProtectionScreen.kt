package de.mimuc.senseeverything.activity.onboarding

import android.app.Application
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.volley.VolleyError
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.R
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.ApiResources
import de.mimuc.senseeverything.api.decodeError
import de.mimuc.senseeverything.api.model.CreateEnrolmentRequest
import de.mimuc.senseeverything.api.model.CreateEnrolmentResponse
import de.mimuc.senseeverything.api.model.Study
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataProtectionViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager
) : AndroidViewModel(application) {
    private val _study = MutableStateFlow<Study?>(null)
    val study: StateFlow<Study?> = _study.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _dataProtectionNotice = MutableStateFlow<String?>(null)
    val dataProtectionNotice: StateFlow<String?> = _dataProtectionNotice.asStateFlow()

    private val _isEnrolling = MutableStateFlow(false)
    val isEnrolling: StateFlow<Boolean> = _isEnrolling.asStateFlow()

    private val _errorCode = MutableStateFlow("")
    val errorCode: StateFlow<String> = _errorCode.asStateFlow()

    private val _showErrorDialog = MutableStateFlow(false)
    val showErrorDialog: StateFlow<Boolean> = _showErrorDialog.asStateFlow()

    init {
        loadStudy()
    }

    private fun loadStudy() {
        viewModelScope.launch {
            _isLoading.value = true

            val study = dataStoreManager.studyFlow.first()
            _study.value = study
            _dataProtectionNotice.value = study?.dataProtectionNotice
            
            _isLoading.value = false
        }
    }

    fun createEnrolment(context: Context, onSuccess: () -> Unit) {
        val currentStudy = _study.value
        if (currentStudy == null) {
            _errorCode.value = "no_study"
            _showErrorDialog.value = true
            return
        }

        viewModelScope.launch {
            _isEnrolling.value = true

            val source = dataStoreManager.onboardingSourceFlow.first()

            val client = ApiClient.getInstance(context)
            val request = CreateEnrolmentRequest(currentStudy.enrolmentKey, source)

            try {
                val createEnrolmentResponse = client.postSerialized<CreateEnrolmentRequest, CreateEnrolmentResponse>(
                    url = ApiResources.enrolment(),
                    requestData = request
                )

                dataStoreManager.saveEnrolment(
                    createEnrolmentResponse.token,
                    createEnrolmentResponse.participantId,
                    createEnrolmentResponse.studyId,
                    createEnrolmentResponse.phases
                )

                _isEnrolling.value = false
                onSuccess()
            } catch (error: Exception) {
                _isEnrolling.value = false

                val volleyError = error as? VolleyError
                if (volleyError != null) {
                    val decodedError = decodeError(volleyError)
                    WHALELog.e("DataProtectionViewModel", "Enrolment Error: ${decodedError.httpCode} ${decodedError.appCode} ${decodedError.message}")
                    _errorCode.value = decodedError.appCode
                } else {
                    WHALELog.e("DataProtectionViewModel", "Enrolment Error: ${error.message}")
                    _errorCode.value = "unknown"
                }
                _showErrorDialog.value = true
            }
        }
    }

    fun closeError() {
        _showErrorDialog.value = false
        _errorCode.value = ""
    }
}

@Composable
fun DataProtectionScreen(nextStep: () -> Unit, innerPadding: PaddingValues) {
    val viewModel: DataProtectionViewModel = viewModel()
    val context = LocalContext.current
    val dataProtectionNotice = viewModel.dataProtectionNotice.collectAsState()
    val isLoading = viewModel.isLoading.collectAsState()
    val isEnrolling = viewModel.isEnrolling.collectAsState()
    val errorCode = viewModel.errorCode.collectAsState()
    val showErrorDialog = viewModel.showErrorDialog.collectAsState()
    val isAccepted = remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)
    ) {
        Heading(
            id = R.drawable.baseline_privacy_tip_24,
            description = stringResource(R.string.onboarding_data_protection),
            text = stringResource(R.string.onboarding_data_protection_header)
        )
        Spacer(modifier = Modifier.padding(6.dp))

        if (isLoading.value) {
            Text("Loading...")
        } else {
            dataProtectionNotice.value?.let { notice ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(AnnotatedString.fromHtml(notice))
                }
            } ?: Text(stringResource(R.string.onboarding_data_protection_not_available))
        }

        Spacer(modifier = Modifier.padding(8.dp))

        // Show enrollment error dialog
        EnrolmentErrorDialog(showErrorDialog.value, errorCode.value, { viewModel.closeError() })

        // Show loading or acceptance controls
        if (isEnrolling.value) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.padding(8.dp))
                Text("Creating enrollment...")
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isAccepted.value,
                    onCheckedChange = { isAccepted.value = it }
                )
                Spacer(modifier = Modifier.padding(4.dp))
                Text(stringResource(R.string.onboarding_data_protection_accept), modifier = Modifier.clickable(onClick = {
                    isAccepted.value = !isAccepted.value
                }))
            }

            Spacer(modifier = Modifier.padding(8.dp))

            Button(
                onClick = {
                    viewModel.createEnrolment(context, nextStep)
                },
                enabled = isAccepted.value,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.onboarding_continue))
            }
        }
    }
}