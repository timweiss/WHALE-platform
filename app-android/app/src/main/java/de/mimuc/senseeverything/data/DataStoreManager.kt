package de.mimuc.senseeverything.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import de.mimuc.senseeverything.api.model.FullQuestionnaire
import de.mimuc.senseeverything.api.model.makeFullQuestionnaireFromJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

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
    val studyPaused: Boolean
)

@Singleton
class SettingsSerializer @Inject constructor() : Serializer<AppSettings> {

    override val defaultValue = AppSettings(
        lastUpdate = 0,
        token = "",
        participantId = "",
        studyId = -1,
        questionnaires = "",
        inInteraction = false,
        studyDays = -1,
        remainingStudyDays = -1,
        timestampStudyStarted = -1,
        studyPaused = false
    )

    override suspend fun readFrom(input: InputStream): AppSettings =
        try {
            Json.decodeFromString(input.readBytes().decodeToString())
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

    fun getStudyIdSync(callback: (Int) -> Unit) {
        runBlocking {
            studyIdFlow.first { studyId ->
                callback(studyId)
                true
            }
        }
    }

    suspend fun saveEnrolment(token: String, participantId: String, studyId: Int) {
        dataStore.updateData { preferences ->
            preferences.copy(
                lastUpdate = System.currentTimeMillis(),
                token = token,
                participantId = participantId,
                studyId = studyId
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

    fun getStudyDaysSync(callback: (Int) -> Unit) {
        runBlocking {
            studyDaysFlow.first { studyDays ->
                callback(studyDays)
                true
            }
        }
    }

    suspend fun saveRemainingStudyDays(remainingStudyDays: Int) {
        dataStore.updateData { preferences ->
            preferences.copy(lastUpdate = System.currentTimeMillis(), remainingStudyDays = remainingStudyDays)
        }
    }

    val remainingStudyDaysFlow = dataStore.data.map { preferences ->
        preferences.remainingStudyDays
    }

    fun getRemainingStudyDaysSync(callback: (Int) -> Unit) {
        runBlocking {
            remainingStudyDaysFlow.first { remainingStudyDays ->
                callback(remainingStudyDays)
                true
            }
        }
    }

    suspend fun saveTimestampStudyStarted(timestamp: Long) {
        dataStore.updateData { preferences ->
            preferences.copy(lastUpdate = System.currentTimeMillis(), timestampStudyStarted = timestamp)
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

    fun getStudyPausedSync(callback: (Boolean) -> Unit) {
        runBlocking {
            studyPausedFlow.first { studyPaused ->
                callback(studyPaused)
                true
            }
        }
    }
}