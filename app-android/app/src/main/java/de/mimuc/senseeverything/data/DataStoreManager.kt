package de.mimuc.senseeverything.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
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

    suspend fun saveParticipantId(participantId: String) {
        dataStore.edit { preferences ->
            Log.d("datastore", "saveParticipantId: $participantId")
            preferences[PARTICIPANT_ID] = participantId
        }
    }

    val participantIdFlow = dataStore.data.map { preferences ->
        preferences[PARTICIPANT_ID] ?: ""
    }

    suspend fun saveTokenAndParticipantId(token: String, participantId: String) {
        dataStore.edit { preferences ->
            preferences[TOKEN] = token
            preferences[PARTICIPANT_ID] = participantId
        }
    }
}