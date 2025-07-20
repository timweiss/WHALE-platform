package de.mimuc.senseeverything.api

import android.util.Log
import com.android.volley.VolleyError
import de.mimuc.senseeverything.api.model.Study
import de.mimuc.senseeverything.data.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume


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
            Log.e("Enrolment", "Error decoding error response: $e")
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

    val response = suspendCancellableCoroutine { continuation ->
        Log.d("Api", "Loading study $studyId")
        apiClient.getJson(ApiResources.studyById(studyId),
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
            response.getString("enrolmentKey"),
            response.getString("description"),
            response.getString("contactEmail"),
            response.getInt("durationDays")
        )

        return study
    }

    return null
}

suspend fun fetchCompletionStatus(apiClient: ApiClient, dataStoreManager: DataStoreManager): Map<String, Boolean> {
    val token = dataStoreManager.tokenFlow.first()
    if (token.isEmpty()) {
        return emptyMap()
    }

    val headers = mapOf("Authorization" to "Bearer $token")

    val response = suspendCancellableCoroutine { continuation ->
        Log.d("Api", "Fetching completion status")
        apiClient.getJson(ApiResources.completionStatus(), headers,
            { response ->
                Log.d("Api", "Received completion status: $response")
                continuation.resume(response)
            },
            { error ->
                Log.d("Api", "Error fetching completion status: $error")
                continuation.resume(null)
            })
    }

    if (response != null) {
        val completionStatus = mutableMapOf<String, Boolean>()
        val keys = response.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            completionStatus[key] = response.getBoolean(key)
        }
        return completionStatus
    }

    return emptyMap()
}