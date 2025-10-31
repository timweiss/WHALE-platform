@file:OptIn(ExperimentalMaterial3Api::class)

package de.mimuc.senseeverything.activity

import android.app.Application
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import de.mimuc.senseeverything.activity.esm.QuestionnaireActivity
import de.mimuc.senseeverything.activity.ui.theme.AppandroidTheme
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.fetchAndPersistQuestionnaires
import de.mimuc.senseeverything.api.model.Study
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.data.persistQuestionnaireElementContent
import de.mimuc.senseeverything.db.AppDatabase
import de.mimuc.senseeverything.db.models.PendingQuestionnaire
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import javax.inject.Inject

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
    private val _isEnrolled = MutableStateFlow(false)
    val isEnrolled: StateFlow<Boolean> get() = _isEnrolled

    private val _participantId = MutableStateFlow("")
    val participantId: StateFlow<String> get() = _participantId

    private val _study = MutableStateFlow(Study.empty)
    val study: StateFlow<Study> get() = _study

    private val _questionnaires = MutableStateFlow(mutableStateListOf<FullQuestionnaire>())
    val questionnaires: StateFlow<List<FullQuestionnaire>> get() = _questionnaires

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
                _isEnrolled.value = true
                _participantId.value = participantId
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
            WHALELog.i("Enrolment", "loading questionnaires")
            val studyId = dataStoreManager.studyIdFlow.first()
            val client = ApiClient.getInstance(getApplication())
            val questionnaires = fetchAndPersistQuestionnaires(studyId, dataStoreManager, client)
            persistQuestionnaireElementContent(context, questionnaires)

            WHALELog.d("Enrolment", questionnaires.toString())

            _questionnaires.value = questionnaires.toMutableStateList()
        }
    }

    fun removeEnrolment(context: Context) {
        viewModelScope.launch {
            dataStoreManager.eraseAllData()

            withContext(IO) {
                database.logDataDao().deleteAll()
                database.pendingQuestionnaireDao().deleteAll()
                database.notificationTriggerDao().deleteAll()
                database.generatedKeyDao().deleteAll()
                database.socialNetworkContactDao().deleteAll()
                database.scheduledAlarmDao().deleteAll()
                database.close()
            }

            _isEnrolled.value = false

            val activity = context.getActivity()
            if (activity != null) {
                activity.finish()
            } else {
                WHALELog.e("OnboardingViewModel", "Could not get activity")
            }
        }
    }

    fun openQuestionnaire(context: Context, questionnaire: FullQuestionnaire) {
        viewModelScope.launch {
            val pendingQuestionnaireId = withContext(IO) {
                PendingQuestionnaire.createEntry(database, dataStoreManager, questionnaire.triggers.first())?.uid
            }

            val activity = Intent(context, QuestionnaireActivity::class.java)
            activity.putExtra(QuestionnaireActivity.INTENT_QUESTIONNAIRE, fullQuestionnaireJson.encodeToString(questionnaire))
            activity.putExtra(QuestionnaireActivity.INTENT_PENDING_QUESTIONNAIRE_ID, pendingQuestionnaireId.toString())
            context.startActivity(activity)
        }
    }

}

@Composable
fun EnrolmentScreen(
    viewModel: EnrolmentViewModel = viewModel(),
    innerPadding: PaddingValues,
    finishedEnrolment: () -> Unit
) {
    val isEnrolled = viewModel.isEnrolled.collectAsState()
    val participantId = viewModel.participantId.collectAsState()
    val context = LocalContext.current
    val study = viewModel.study.collectAsState()
    val questionnaires by viewModel.questionnaires.collectAsState()
    val noQuestionnaires = questionnaires.isEmpty()

    Column(
        modifier = Modifier
            .padding(innerPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (isEnrolled.value) {
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