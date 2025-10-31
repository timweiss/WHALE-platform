package de.mimuc.senseeverything.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import de.mimuc.senseeverything.activity.onboarding.OnboardingStep
import de.mimuc.senseeverything.api.model.ExperimentalGroupPhase
import de.mimuc.senseeverything.api.model.Study
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.ema.fullQuestionnaireJson
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString as decodeFromStringJson

fun Context.appSettingsDataStoreFile(name: String): File =
    this.dataStoreFile("$name.appsettings.json")

enum class StudyState {
    LOADING,
    NOT_ENROLLED,
    RUNNING,
    ENDED,
    CANCELLED
}

@Serializable
data class AppSettings(
    val lastUpdate: Long,
    val token: String?,
    val participantId: String?,
    val studyId: Int,
    val questionnaires: String?,
    val inInteraction: Boolean,
    val studyDays: Int,
    val remainingStudyDays: Int,
    val timestampStudyStarted: Long,
    val studyPaused: Boolean,
    val studyPausedUntil: Long,
    val onboardingStep: OnboardingStep,
    val study: Study?,
    val phases: List<ExperimentalGroupPhase>? = null,
    val studyState: StudyState,
    val sensitiveDataSalt: String? = null,
    val lastPermissionNotificationTime: Long = 0L,
    val lastRevokedPermissions: Set<String> = emptySet()
)

@Serializable
data class OptionalAppSettings(
    val lastUpdate: Long? = null,
    val token: String? = null,
    val participantId: String? = null,
    val studyId: Int? = null,
    val questionnaires: String? = null,
    val inInteraction: Boolean? = null,
    val studyDays: Int? = null,
    val remainingStudyDays: Int? = null,
    val timestampStudyStarted: Long? = null,
    val studyPaused: Boolean? = null,
    val studyPausedUntil: Long? = null,
    val onboardingStep: OnboardingStep? = null,
    val study: Study? = null,
    val phases: List<ExperimentalGroupPhase>? = null,
    val studyState: StudyState? = null,
    val sensitiveDataSalt: String? = null,
    val lastPermissionNotificationTime: Long? = null,
    val lastRevokedPermissions: Set<String>? = null
)

val DEFAULT_APP_SETTINGS = AppSettings(
    lastUpdate = 0,
    token = "",
    participantId = "",
    studyId = -1,
    questionnaires = "",
    inInteraction = false,
    studyDays = -1,
    remainingStudyDays = -1,
    timestampStudyStarted = -1,
    studyPaused = false,
    studyPausedUntil = -1,
    onboardingStep = OnboardingStep.WELCOME,
    study = null,
    phases = null,
    studyState = StudyState.NOT_ENROLLED,
    lastPermissionNotificationTime = 0L,
    lastRevokedPermissions = emptySet()
)

fun recoverFromOptionalOrUseDefault(optionalAppSettings: OptionalAppSettings): AppSettings {
    val defaultAppSettings = DEFAULT_APP_SETTINGS

    return AppSettings(
        lastUpdate = optionalAppSettings.lastUpdate ?: defaultAppSettings.lastUpdate,
        token = optionalAppSettings.token ?: defaultAppSettings.token,
        participantId = optionalAppSettings.participantId ?: defaultAppSettings.participantId,
        studyId = optionalAppSettings.studyId ?: defaultAppSettings.studyId,
        questionnaires = optionalAppSettings.questionnaires ?: defaultAppSettings.questionnaires,
        inInteraction = optionalAppSettings.inInteraction ?: defaultAppSettings.inInteraction,
        studyDays = optionalAppSettings.studyDays ?: defaultAppSettings.studyDays,
        remainingStudyDays = optionalAppSettings.remainingStudyDays
            ?: defaultAppSettings.remainingStudyDays,
        timestampStudyStarted = optionalAppSettings.timestampStudyStarted
            ?: defaultAppSettings.timestampStudyStarted,
        studyPaused = optionalAppSettings.studyPaused ?: defaultAppSettings.studyPaused,
        studyPausedUntil = optionalAppSettings.studyPausedUntil
            ?: defaultAppSettings.studyPausedUntil,
        onboardingStep = optionalAppSettings.onboardingStep ?: defaultAppSettings.onboardingStep,
        study = optionalAppSettings.study ?: defaultAppSettings.study,
        phases = optionalAppSettings.phases ?: defaultAppSettings.phases,
        studyState = optionalAppSettings.studyState ?: defaultAppSettings.studyState,
        lastPermissionNotificationTime = optionalAppSettings.lastPermissionNotificationTime
            ?: defaultAppSettings.lastPermissionNotificationTime,
        lastRevokedPermissions = optionalAppSettings.lastRevokedPermissions
            ?: defaultAppSettings.lastRevokedPermissions
    )
}

@Singleton
class SettingsSerializer @Inject constructor() : Serializer<AppSettings> {

    private val json = Json { ignoreUnknownKeys = true }
    override val defaultValue = DEFAULT_APP_SETTINGS

    override suspend fun readFrom(input: InputStream): AppSettings =
        try {
            val string = input.readBytes().decodeToString()
            recoverFromOptionalOrUseDefault(
                json.decodeFromStringJson<OptionalAppSettings>(
                    string
                )
            )
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read Settings", serialization)
        }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        withContext(Dispatchers.IO) {
            val encoded = json.encodeToString(t)
            output.write(
                encoded
                    .encodeToByteArray()
            )
        }
    }
}

@Singleton
class DataStoreManager @Inject constructor(@ApplicationContext context: Context) {

    companion object {
        private const val DATASTORE_NAME = "app_preferences"
    }

    private val dataStore: DataStore<AppSettings> = MultiProcessDataStoreFactory.create(
        serializer = SettingsSerializer(),
        produceFile = { context.appSettingsDataStoreFile(DATASTORE_NAME) }
    )

    suspend fun eraseAllData() {
        dataStore.updateData {
            DEFAULT_APP_SETTINGS
        }
    }

    val tokenFlow = dataStore.data.map { preferences ->
        preferences.token ?: ""
    }

    suspend fun saveParticipantId(participantId: String) {
        dataStore.updateData { preferences ->
            WHALELog.i("datastore", "saveParticipantId: $participantId")
            preferences.copy(lastUpdate = System.currentTimeMillis(), participantId = participantId)
        }
    }

    val participantIdFlow = dataStore.data.map { preferences ->
        preferences.participantId ?: ""
    }

    suspend fun saveStudyId(studyId: Int) {
        dataStore.updateData { preferences ->
            preferences.copy(lastUpdate = System.currentTimeMillis(), studyId = studyId)
        }
    }

    val studyIdFlow = dataStore.data.map { preferences ->
        preferences.studyId
    }

    suspend fun saveEnrolment(
        token: String,
        participantId: String,
        studyId: Int,
        phases: List<ExperimentalGroupPhase>
    ) {
        dataStore.updateData { preferences ->
            preferences.copy(
                lastUpdate = System.currentTimeMillis(),
                token = token,
                participantId = participantId,
                studyId = studyId,
                phases = phases
            )
        }
    }

    suspend fun saveQuestionnaires(fullQuestionnaires: List<FullQuestionnaire>) {
        val json = fullQuestionnaireJson.encodeToString(fullQuestionnaires)
        dataStore.updateData {
            it.copy(
                lastUpdate = System.currentTimeMillis(),
                questionnaires = json
            )
        }
    }

    val questionnairesFlow = dataStore.data.map { preferences ->
        val json = preferences.questionnaires ?: "[]"
        if (json.length < 2) {
            return@map emptyList()
        }
        val parsed = fullQuestionnaireJson.decodeFromString<List<FullQuestionnaire>>(json)
        return@map parsed
    }

    suspend fun saveStudyDays(studyDays: Int) {
        dataStore.updateData { preferences ->
            preferences.copy(lastUpdate = System.currentTimeMillis(), studyDays = studyDays)
        }
    }

    val studyDaysFlow = dataStore.data.map { preferences ->
        preferences.studyDays
    }

    suspend fun saveRemainingStudyDays(remainingStudyDays: Int) {
        dataStore.updateData { preferences ->
            preferences.copy(
                lastUpdate = System.currentTimeMillis(),
                remainingStudyDays = remainingStudyDays
            )
        }
    }

    suspend fun saveTimestampStudyStarted(timestamp: Long) {
        dataStore.updateData { preferences ->
            preferences.copy(
                lastUpdate = System.currentTimeMillis(),
                timestampStudyStarted = timestamp
            )
        }
    }

    val timestampStudyStartedFlow = dataStore.data.map { preferences ->
        preferences.timestampStudyStarted
    }

    suspend fun saveStudyPaused(studyPaused: Boolean) {
        dataStore.updateData {
            it.copy(lastUpdate = System.currentTimeMillis(), studyPaused = studyPaused)
        }
    }

    val studyPausedFlow = dataStore.data.map { preferences ->
        preferences.studyPaused
    }

    suspend fun saveStudyPausedUntil(studyPausedUntil: Long) {
        dataStore.updateData {
            it.copy(lastUpdate = System.currentTimeMillis(), studyPausedUntil = studyPausedUntil)
        }
    }

    val studyPausedUntilFlow = dataStore.data.map { preferences ->
        preferences.studyPausedUntil
    }

    val onboardingStepFlow = dataStore.data.map { preferences ->
        preferences.onboardingStep
    }

    suspend fun saveOnboardingStep(onboardingStep: OnboardingStep) {
        dataStore.updateData {
            it.copy(lastUpdate = System.currentTimeMillis(), onboardingStep = onboardingStep)
        }
    }

    val studyFlow = dataStore.data.map { preferences ->
        preferences.study
    }

    suspend fun saveStudy(study: Study) {
        dataStore.updateData {
            it.copy(lastUpdate = System.currentTimeMillis(), study = study)
        }
    }

    val studyPhasesFlow = dataStore.data.map { preferences ->
        preferences.phases
    }

    suspend fun saveStudyPhases(phases: List<ExperimentalGroupPhase>) {
        dataStore.updateData {
            it.copy(lastUpdate = System.currentTimeMillis(), phases = phases)
        }
    }

    suspend fun saveStudyState(studyState: StudyState) {
        dataStore.updateData {
            it.copy(lastUpdate = System.currentTimeMillis(), studyState = studyState)
        }
    }

    val studyStateFlow = dataStore.data.map { preferences ->
        preferences.studyState
    }

    suspend fun saveSensitiveDataSalt(sensitiveDataSalt: String) {
        dataStore.updateData {
            it.copy(lastUpdate = System.currentTimeMillis(), sensitiveDataSalt = sensitiveDataSalt)
        }
    }

    val sensitiveDataSaltFlow = dataStore.data.map { preferences ->
        preferences.sensitiveDataSalt
    }

    fun getSensitiveDataSaltSync(callback: (String) -> Unit) {
        runBlocking {
            sensitiveDataSaltFlow.first { sensitiveDataSalt ->
                callback(sensitiveDataSalt ?: "")
                true
            }
        }
    }

    val lastPermissionNotificationTimeFlow = dataStore.data.map { preferences ->
        preferences.lastPermissionNotificationTime
    }

    suspend fun saveLastPermissionNotificationTime(timestamp: Long) {
        dataStore.updateData {
            it.copy(
                lastUpdate = System.currentTimeMillis(),
                lastPermissionNotificationTime = timestamp
            )
        }
    }

    val lastRevokedPermissionsFlow = dataStore.data.map { preferences ->
        preferences.lastRevokedPermissions
    }

    suspend fun saveLastRevokedPermissions(revokedPermissions: Set<String>) {
        dataStore.updateData {
            it.copy(
                lastUpdate = System.currentTimeMillis(),
                lastRevokedPermissions = revokedPermissions
            )
        }
    }
}

suspend fun DataStoreManager.currentStudyDay(): Long {
    val unixStarted = timestampStudyStartedFlow.first()
    val date = Instant.ofEpochMilli(unixStarted).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = Instant.now().atZone(ZoneId.systemDefault()).toLocalDate()
    val days = today.toEpochDay() - date.toEpochDay() + 1
    return days
}

suspend fun DataStoreManager.getCurrentStudyPhase(): ExperimentalGroupPhase? {
    val phases = studyPhasesFlow.first()
    val currentDay = currentStudyDay() - 1

    return phases?.firstOrNull { phase ->
        phase.fromDay <= currentDay && phase.fromDay + phase.durationDays > currentDay
    }
}

suspend fun DataStoreManager.getQuestionnaireById(id: Int): FullQuestionnaire? {
    val questionnaires = questionnairesFlow.first()
    return questionnaires.firstOrNull { questionnaire ->
        questionnaire.questionnaire.id == id
    }
}
