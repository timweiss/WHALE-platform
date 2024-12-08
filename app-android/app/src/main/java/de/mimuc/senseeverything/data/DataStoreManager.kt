package de.mimuc.senseeverything.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import de.mimuc.senseeverything.activity.OnboardingStep
import de.mimuc.senseeverything.api.model.FullQuestionnaire
import de.mimuc.senseeverything.api.model.Study
import de.mimuc.senseeverything.api.model.StudyConfiguration
import de.mimuc.senseeverything.api.model.makeFullQuestionnaireFromJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.decodeFromString as decodeFromStringJson

fun Context.appSettingsDataStoreFile(name: String): File =
    this.dataStoreFile("$name.appsettings.json")

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
    val studyConfiguration: StudyConfiguration?,
    val interactionWidgetTimeBucket: HashMap<String, Boolean>
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
    val studyConfiguration: StudyConfiguration? = null,
    val interactionWidgetTimeBucket: HashMap<String, Boolean>? = null
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
    studyConfiguration = null,
    interactionWidgetTimeBucket = hashMapOf()
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
        studyConfiguration = optionalAppSettings.studyConfiguration
            ?: defaultAppSettings.studyConfiguration,
        interactionWidgetTimeBucket = optionalAppSettings.interactionWidgetTimeBucket
            ?: defaultAppSettings.interactionWidgetTimeBucket
    )
}

@Singleton
class SettingsSerializer @Inject constructor() : Serializer<AppSettings> {

    override val defaultValue = DEFAULT_APP_SETTINGS

    override suspend fun readFrom(input: InputStream): AppSettings =
        try {
            recoverFromOptionalOrUseDefault(
                Json.decodeFromStringJson<OptionalAppSettings>(
                    input.readBytes().decodeToString()
                )
            )
        } catch (serialization: SerializationException) {
            throw CorruptionException("Unable to read Settings", serialization)
        }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(
                Json.encodeToString(t)
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

    suspend fun saveToken(token: String) {
        dataStore.updateData { preferences ->
            preferences.copy(lastUpdate = System.currentTimeMillis(), token = token)
        }
    }

    val tokenFlow = dataStore.data.map { preferences ->
        preferences.token ?: ""
    }

    fun getTokenSync(callback: (String) -> Unit) {
        runBlocking {
            tokenFlow.first { token ->
                callback(token)
                true
            }
        }
    }

    suspend fun saveParticipantId(participantId: String) {
        dataStore.updateData { preferences ->
            Log.d("datastore", "saveParticipantId: $participantId")
            preferences.copy(lastUpdate = System.currentTimeMillis(), participantId = participantId)
        }
    }

    val participantIdFlow = dataStore.data.map { preferences ->
        preferences.participantId ?: ""
    }

    fun getParticipantIdSync(callback: (String) -> Unit) {
        runBlocking {
            participantIdFlow.first { participantId ->
                callback(participantId)
                true
            }
        }
    }

    suspend fun saveStudyId(studyId: Int) {
        dataStore.updateData { preferences ->
            preferences.copy(lastUpdate = System.currentTimeMillis(), studyId = studyId)
        }
    }

    val studyIdFlow = dataStore.data.map { preferences ->
        preferences.studyId ?: -1
    }

    suspend fun saveEnrolment(
        token: String,
        participantId: String,
        studyId: Int,
        studyConfiguration: StudyConfiguration
    ) {
        dataStore.updateData { preferences ->
            preferences.copy(
                lastUpdate = System.currentTimeMillis(),
                token = token,
                participantId = participantId,
                studyId = studyId,
                studyConfiguration = studyConfiguration
            )
        }
    }

    suspend fun saveQuestionnaires(fullQuestionnaires: List<FullQuestionnaire>) {
        val json = JSONArray()
        for (fullQuestionnaire in fullQuestionnaires) {
            json.put(fullQuestionnaire.toJson())
        }
        dataStore.updateData {
            it.copy(
                lastUpdate = System.currentTimeMillis(),
                questionnaires = json.toString()
            )
        }
    }

    val questionnairesFlow = dataStore.data.map { preferences ->
        val json = preferences.questionnaires ?: "[]"
        val jsonArray = JSONArray(json)
        val fullQuestionnaires = mutableListOf<FullQuestionnaire>()
        for (i in 0 until jsonArray.length()) {
            val fullQuestionnaire = makeFullQuestionnaireFromJson(jsonArray.getJSONObject(i))
            fullQuestionnaires.add(fullQuestionnaire)
        }
        fullQuestionnaires.toList()
    }

    fun getQuestionnairesSync(callback: (List<FullQuestionnaire>) -> Unit) {
        runBlocking {
            questionnairesFlow.first { fullQuestionnaires ->
                callback(fullQuestionnaires)
                true
            }
        }
    }

    suspend fun setInInteraction(inInteraction: Boolean) {
        dataStore.updateData {
            it.copy(lastUpdate = System.currentTimeMillis(), inInteraction = inInteraction)
        }
    }

    val inInteractionFlow = dataStore.data.map { preferences ->
        preferences.inInteraction
    }

    fun getInInteractionSync(callback: (Boolean) -> Unit) {
        runBlocking {
            inInteractionFlow.first { inInteraction ->
                callback(inInteraction)
                true
            }
        }
    }

    fun setInInteractionSync(inInteraction: Boolean) {
        runBlocking {
            setInInteraction(inInteraction)
        }
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

    val remainingStudyDaysFlow = dataStore.data.map { preferences ->
        preferences.remainingStudyDays
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

    val studyConfigurationFlow = dataStore.data.map { preferences ->
        preferences.studyConfiguration
    }

    fun getStudyConfigurationSync(callback: (StudyConfiguration?) -> Unit) {
        runBlocking {
            studyConfigurationFlow.first { studyConfiguration ->
                callback(studyConfiguration)
                true
            }
        }
    }

    suspend fun saveStudyConfiguration(studyConfiguration: StudyConfiguration) {
        dataStore.updateData {
            it.copy(
                lastUpdate = System.currentTimeMillis(),
                studyConfiguration = studyConfiguration
            )
        }
    }

    val interactionWidgetTimeBucketFlow = dataStore.data.map { preferences ->
        preferences.interactionWidgetTimeBucket
    }

    suspend fun setInteractionWidgetTimeBucket(interactionWidgetTimeBucket: HashMap<String, Boolean>) {
        dataStore.updateData {
            it.copy(
                lastUpdate = System.currentTimeMillis(),
                interactionWidgetTimeBucket = interactionWidgetTimeBucket
            )
        }
    }

    fun getInteractionWidgetTimeBucketSync(callback: (HashMap<String, Boolean>) -> Unit) {
        runBlocking {
            interactionWidgetTimeBucketFlow.first { interactionWidgetTimeBucket ->
                callback(interactionWidgetTimeBucket)
                true
            }
        }
    }
}