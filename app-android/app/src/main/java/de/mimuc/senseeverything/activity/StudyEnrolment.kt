@file:OptIn(ExperimentalMaterial3Api::class)

package de.mimuc.senseeverything.activity

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.model.Study
import de.mimuc.senseeverything.data.DataStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@AndroidEntryPoint
class StudyEnrolment : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                                        Text("Enrolment")
                                    }
                            )
                        }
                ) { innerPadding ->
                    EnrolmentScreen(innerPadding = innerPadding)
                }
            }
        }
    }
}

@HiltViewModel
class EnrolmentViewModel @Inject constructor(
        application: Application,
        private val dataStoreManager: DataStoreManager
) : AndroidViewModel(application) {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    private val _isEnrolled = MutableStateFlow(false)
    val isEnrolled: StateFlow<Boolean> get() = _isEnrolled

    private val _participantId = MutableStateFlow("")
    val participantId: StateFlow<String> get() = _participantId

    private val _study = MutableStateFlow(Study("", -1, ""))
    val study: StateFlow<Study> get() = _study

    init {
        viewModelScope.launch {
            combine(
                    dataStoreManager.tokenFlow,
                    dataStoreManager.participantIdFlow,
                    dataStoreManager.studyIdFlow
            ) { token, participantId, studyId ->
                if (token.isNotEmpty()) {
                    _isEnrolled.value = true
                    loadStudy(getApplication(), studyId)
                }

                if (participantId.isNotEmpty()) {
                    _participantId.value = participantId
                }
            }.collect()
        }
    }

    fun performAction(context: Context, text: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val client = ApiClient.getInstance(context)
            val body = JSONObject()
            body.put("enrolmentKey", text)

            val response = suspendCoroutine { continuation ->
                client.post("https://siapi.timweiss.dev/v1/enrolment", body, emptyMap(),
                    { response ->
                        continuation.resume(response)
                    },
                    { error ->
                        continuation.resume(null)
                    })
            }

            if (response == null) {
                _isEnrolled.value = false
                _isLoading.value = false
            } else {
                val token = response.getString("token")
                val participantId = response.getString("participantId")
                val studyId = response.getInt("studyId")

                _isLoading.value = false
                _isEnrolled.value = true
                _participantId.value = participantId

                dataStoreManager.saveEnrolment(token, participantId, studyId)
                loadStudy(context, studyId)
            }
        }
    }

    private fun loadStudy(context: Context, studyId: Int) {
        if (studyId <= 0) {
            return
        }

        viewModelScope.launch {
            val client = ApiClient.getInstance(context)
            val response = suspendCoroutine { continuation ->
                client.getJson("https://siapi.timweiss.dev/v1/study/$studyId",
                    { response ->
                        continuation.resume(response)
                    },
                    { error ->
                        continuation.resume(null)
                    })
            }

            if (response != null) {
                val study = Study(
                    response.getString("name"),
                    response.getInt("id"),
                    response.getString("enrolmentKey")
                )

                _study.value = study
            }
        }
    }

    fun removeEnrolment() {
        viewModelScope.launch {
            dataStoreManager.saveToken("")
            dataStoreManager.saveParticipantId("")
            dataStoreManager.saveStudyId(-1)
            _isEnrolled.value = false
        }
    }
}

@Composable
fun EnrolmentScreen(viewModel: EnrolmentViewModel = viewModel(), innerPadding : PaddingValues) {
    val textState = remember { mutableStateOf("") }
    val isLoading = viewModel.isLoading.collectAsState()
    val isEnrolled = viewModel.isEnrolled.collectAsState()
    val participantId = viewModel.participantId.collectAsState()
    val context = LocalContext.current
    val study = viewModel.study.collectAsState()

    Column(
        modifier = Modifier.padding(innerPadding).padding(16.dp)
    ) {
        if (!isEnrolled.value) {
            TextField(
                    value = textState.value,
                    onValueChange = { textState.value = it },
                    label = { Text("Enrolment Key") },
                    modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading.value) {
                CircularProgressIndicator()
            } else {
                Button(
                        onClick = {
                            viewModel.performAction(context, textState.value)
                        },
                        modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enrol in Study")
                }
            }
        } else {
            Icon(Icons.Rounded.Check, contentDescription = "success!", modifier = Modifier.size(32.dp))
            Text("Enrolment Successful!")
            if (study.value.id != -1) {
                Text("Current Study: ${study.value.name}")
            } else {
                Text("Fetching study information...")
            }
            Text("Participant ID: ${participantId.value}")
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                    onClick = {
                        viewModel.removeEnrolment()
                    },
                    modifier = Modifier.fillMaxWidth()
            ) {
                Text("Remove Enrolment")
            }
        }
    }
}