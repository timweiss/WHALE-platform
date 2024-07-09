package de.mimuc.senseeverything.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.mimuc.senseeverything.api.model.FullQuestionnaire
import de.mimuc.senseeverything.api.model.makeFullQuestionnaireFromJson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

private val USER_PREFERENCES_NAME = "user_preferences"

private val Context.dataStore by preferencesDataStore(
        name = USER_PREFERENCES_NAME
)

@Singleton
class DataStoreManager @Inject constructor(@ApplicationContext context: Context) {

    companion object {
        val TOKEN = stringPreferencesKey("token")
        val PARTICIPANT_ID = stringPreferencesKey("participantId")
        val STUDY_ID = stringPreferencesKey("studyId")
        val QUESTIONNAIRES = stringPreferencesKey("questionnaires")
        val IN_INTERACTION = booleanPreferencesKey("inInteraction")
    }

    private val dataStore = context.dataStore

    suspend fun saveToken(token: String) {
        dataStore.edit { preferences ->
            preferences[TOKEN] = token
        }
    }

    val tokenFlow = dataStore.data.map { preferences ->
        preferences[TOKEN] ?: ""
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
        dataStore.edit { preferences ->
            Log.d("datastore", "saveParticipantId: $participantId")
            preferences[PARTICIPANT_ID] = participantId
        }
    }

    val participantIdFlow = dataStore.data.map { preferences ->
        preferences[PARTICIPANT_ID] ?: ""
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
        dataStore.edit { preferences ->
            preferences[STUDY_ID] = studyId.toString()
        }
    }

    val studyIdFlow = dataStore.data.map { preferences ->
        if(preferences[STUDY_ID] != null) {
            preferences[STUDY_ID]?.toInt() ?: -1
        } else {
            -1
        }
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
        dataStore.edit { preferences ->
            preferences[TOKEN] = token
            preferences[PARTICIPANT_ID] = participantId
            preferences[STUDY_ID] = studyId.toString()
        }
    }

    suspend fun saveQuestionnaires(fullQuestionnaires: List<FullQuestionnaire>) {
        val json = JSONArray()
        for (fullQuestionnaire in fullQuestionnaires) {
            json.put(fullQuestionnaire.toJson())
        }
        dataStore.edit { preferences ->
            preferences[QUESTIONNAIRES] = json.toString()
        }
    }

    val questionnairesFlow = dataStore.data.map { preferences ->
        val json = preferences[QUESTIONNAIRES] ?: "[]"
        val jsonArray = JSONArray(json)
        val fullQuestionnaires = mutableListOf<FullQuestionnaire>()
        for (i in 0 until jsonArray.length()) {
            val fullQuestionnaire = makeFullQuestionnaireFromJson(jsonArray.getJSONObject(i))
            fullQuestionnaires.add(fullQuestionnaire)
        }
        fullQuestionnaires.toList()
    }

    suspend fun setInInteraction(inInteraction: Boolean) {
        dataStore.edit { preferences ->
            preferences[IN_INTERACTION] = inInteraction
        }
    }

    val inInteractionFlow = dataStore.data.map { preferences ->
        preferences[IN_INTERACTION] ?: false
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
}