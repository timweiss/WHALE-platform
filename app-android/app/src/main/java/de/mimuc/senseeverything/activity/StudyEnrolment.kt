@file:OptIn(ExperimentalMaterial3Api::class)

package de.mimuc.senseeverything.activity

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.android.volley.VolleyError
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.activity.esm.QuestionnaireActivity
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.ApiResources
import de.mimuc.senseeverything.api.decodeError
import de.mimuc.senseeverything.api.fetchAndPersistQuestionnaires
import de.mimuc.senseeverything.api.model.EnrolmentResponse
import de.mimuc.senseeverything.api.model.FullQuestionnaire
import de.mimuc.senseeverything.api.model.Study
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.persistQuestionnaireElementContent
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
                    EnrolmentScreen(innerPadding = innerPadding, finishedEnrolment = {
                        // whatever
                    })
                }
            }
        }
    }
}

@HiltViewModel
class EnrolmentViewModel @Inject constructor(
    application: Application,
    private val dataStoreManager: DataStoreManager,
    private val database: AppDatabase
) : AndroidViewModel(application) {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    private val _isEnrolled = MutableStateFlow(false)
    val isEnrolled: StateFlow<Boolean> get() = _isEnrolled

    private val _participantId = MutableStateFlow("")
    val participantId: StateFlow<String> get() = _participantId

    private val _study = MutableStateFlow(Study("", -1, "", "", "", 0))
    val study: StateFlow<Study> get() = _study

    private val _questionnaires = MutableStateFlow(mutableStateListOf<FullQuestionnaire>())
    val questionnaires: StateFlow<List<FullQuestionnaire>> get() = _questionnaires

    private val _errorCode = MutableStateFlow("")
    val errorCode: StateFlow<String> get() = _errorCode

    private val _showErrorDialog = MutableStateFlow(false)
    val showErrorDialog: StateFlow<Boolean> get() = _showErrorDialog

    init {
        viewModelScope.launch {
            val token = dataStoreManager.tokenFlow.first()
            val participantId = dataStoreManager.participantIdFlow.first()
            val studyId = dataStoreManager.studyIdFlow.first()

            if (token.isNotEmpty()) {
                _isEnrolled.value = true
                loadStudy(getApplication(), studyId)
            }

            if (participantId.isNotEmpty()) {
                _participantId.value = participantId
            }
        }
    }

    fun performAction(context: Context, text: String, finishedEnrolment: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            val client = ApiClient.getInstance(context)
            val body = JSONObject()
            body.put("enrolmentKey", text)

            val response = suspendCoroutine { continuation ->
                client.post(ApiResources.enrolment(), body, emptyMap(),
                    { response ->
                        continuation.resume(response)
                    },
                    { error ->
                        continuation.resume(error)
                    })
            }

            if (response is VolleyError) {
                _isEnrolled.value = false
                _isLoading.value = false

                val error = decodeError(response)
                Log.e("Enrolment", "Error: ${error.httpCode} ${error.appCode} ${error.message}")
                _errorCode.value = error.appCode
                _showErrorDialog.value = true
            } else if (response is JSONObject) {
                val enrolmentResponse = EnrolmentResponse.fromJson(response)

                val participantId = enrolmentResponse.participantId
                val studyId = enrolmentResponse.studyId

                _isLoading.value = false
                _isEnrolled.value = true
                _participantId.value = participantId

                dataStoreManager.saveEnrolment(
                    enrolmentResponse.token,
                    participantId,
                    studyId,
                    enrolmentResponse.phases
                )
                finishedEnrolment()
            }
        }
    }

    private fun loadStudy(context: Context, studyId: Int) {
        if (studyId <= 0) {
            return
        }

        viewModelScope.launch {
            val client = ApiClient.getInstance(context)
            val study = de.mimuc.senseeverything.api.loadStudy(client, studyId)

            if (study != null) {
                _study.value = study

                dataStoreManager.saveStudy(study)
                dataStoreManager.saveStudyDays(study.durationDays)
                dataStoreManager.saveRemainingStudyDays(study.durationDays)
            }
        }
    }

    fun fetchQuestionnaires(context: Context) {
        viewModelScope.launch {
            Log.d("Enrolment", "loading questionnaires")
            val studyId = dataStoreManager.studyIdFlow.first()
            val client = ApiClient.getInstance(getApplication())
            val questionnaires = fetchAndPersistQuestionnaires(studyId, dataStoreManager, client)
            persistQuestionnaireElementContent(context, questionnaires)

            Log.d("Enrolment", questionnaires.toString())

            _questionnaires.value = questionnaires.toMutableStateList()
        }
    }

    fun removeEnrolment(context: Context) {
        viewModelScope.launch {
            dataStoreManager.eraseAllData()

            withContext(IO) {
                database.logDataDao().deleteAll()
                database.close()
            }

            _isEnrolled.value = false

            val activity = context.getActivity()
            if (activity != null) {
                activity.finish()
            } else {
                Log.e("OnboardingViewModel", "Could not get activity")
            }
        }
    }

    fun openQuestionnaire(context: Context, questionnaire: FullQuestionnaire) {
        viewModelScope.launch {
            val pendingQuestionnaireId = withContext(IO) {
                PendingQuestionnaire.createEntry(database, dataStoreManager, questionnaire.triggers.first())
            }

            val activity = Intent(context, QuestionnaireActivity::class.java)
            activity.putExtra("questionnaire", questionnaire.toJson().toString())
            activity.putExtra("pendingQuestionnaireId", pendingQuestionnaireId)
            context.startActivity(activity)
        }
    }

    fun closeError() {
        _showErrorDialog.value = false
        _errorCode.value = ""
    }
}

@Composable
fun EnrolmentScreen(
    viewModel: EnrolmentViewModel = viewModel(),
    innerPadding: PaddingValues,
    finishedEnrolment: () -> Unit
) {
    val textState = remember { mutableStateOf("") }
    val isLoading = viewModel.isLoading.collectAsState()
    val isEnrolled = viewModel.isEnrolled.collectAsState()
    val participantId = viewModel.participantId.collectAsState()
    val context = LocalContext.current
    val study = viewModel.study.collectAsState()
    val questionnaires by viewModel.questionnaires.collectAsState()
    val noQuestionnaires = questionnaires.isEmpty()
    val errorCode = viewModel.errorCode.collectAsState()
    val showErrorDialog = viewModel.showErrorDialog.collectAsState()

    Column(
        modifier = Modifier
            .padding(innerPadding)
            .padding(16.dp)
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
                        viewModel.performAction(context, textState.value, finishedEnrolment)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enrol in Study")
                }
            }

            if (showErrorDialog.value && errorCode.value.isNotEmpty()) {
                AlertDialog(
                    title = {
                        if (errorCode.value == "full" || errorCode.value == "closed") {
                            Text("Study closed")
                        } else {
                            Text("Incorrect enrolment key")
                        }
                    },
                    text = {
                        if (errorCode.value == "full" || errorCode.value == "closed") {
                            Text("The study no longer accepts new participants.")
                        } else {
                            Text("The study could not be joined. Please check if the enrolment key is correct.")
                        }
                    },
                    onDismissRequest = {
                        viewModel.closeError()
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.closeError()
                            }
                        ) {
                            Text("Confirm")
                        }
                    }
                )
            }
        } else {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "success!",
                modifier = Modifier.size(32.dp)
            )
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
                    viewModel.fetchQuestionnaires(context)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Fetch Questionnaires")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (noQuestionnaires) {
                Text("No questionnaires available")
            } else {
                Column {
                    for (questionnaire in questionnaires) {
                        Text(questionnaire.questionnaire.name)
                        Button(onClick = {
                            viewModel.openQuestionnaire(context, questionnaire)
                        }) {
                            Text("Start")
                        }
                    }
                }
            }

            Button(
                onClick = {
                    throw RuntimeException("Crashlytics Test")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Make a Crash")
            }

            Spacer(modifier = Modifier.height(36.dp))
            Button(
                onClick = {
                    viewModel.removeEnrolment(context)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Remove Enrolment")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                finishedEnrolment()
            }) {
                Text("Continue")
            }
        }
    }
}