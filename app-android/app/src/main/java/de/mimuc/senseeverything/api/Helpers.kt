package de.mimuc.senseeverything.api

import com.android.volley.VolleyError
import de.mimuc.senseeverything.api.model.Study
import de.mimuc.senseeverything.api.model.studyJson
import de.mimuc.senseeverything.data.DataStoreManager
import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.coroutines.flow.first
import org.json.JSONObject


data class ApiError(val httpCode: Int, val appCode: String, val message: String)

fun decodeError(error: VolleyError): ApiError {
    val response = error.networkResponse
    val message = error.message ?: "Unknown error"
    return if (response != null) {
        try {
            val data = String(response.data)
            val json = JSONObject(data)
            ApiError(response.statusCode, json.getString("code"), json.getString("error"))
        } catch (e: Exception) {
            WHALELog.e("Enrolment", "Error decoding error response: $e")
            ApiError(response.statusCode, "unknown", message)
        }
    } else {
        ApiError(-1, "unknown", message)
    }
}

suspend fun loadStudy(apiClient: ApiClient, studyId: Int): Study? {
    if (studyId == -1) {
        return null
    }

    return try {
        WHALELog.i("Api", "Loading study $studyId")
        apiClient.getSerialized<Study>(ApiResources.studyById(studyId), emptyMap(), studyJson)
    } catch (e: Exception) {
        WHALELog.e("Api", "Error loading study: ${e.message}")
        null
    }
}

suspend fun loadStudyByEnrolmentKey(apiClient: ApiClient, enrolmentKey: String): Study? {
    return try {
        WHALELog.i("Api", "Loading study by enrolment key $enrolmentKey")
        apiClient.getSerialized<Study>(ApiResources.studyByEnrolmentKey(enrolmentKey), emptyMap(), studyJson)
    } catch (e: Exception) {
        WHALELog.e("Api", "Error loading study: ${e.message}")
        null
    }
}

suspend fun fetchCompletionStatus(apiClient: ApiClient, dataStoreManager: DataStoreManager): Map<String, Boolean> {
    val token = dataStoreManager.tokenFlow.first()
    if (token.isEmpty()) {
        return emptyMap()
    }

    val headers = mapOf("Authorization" to "Bearer $token")

    return try {
        WHALELog.i("Api", "Fetching completion status")
        val status = apiClient.getSerialized<Map<String, Boolean>>(
            url = ApiResources.completionStatus(),
            headers = headers
        )
        WHALELog.i("Api", "Received completion status: $status")
        status
    } catch (e: Exception) {
        WHALELog.e("Api", "Error fetching completion status: ${e.message}")
        emptyMap()
    }
}