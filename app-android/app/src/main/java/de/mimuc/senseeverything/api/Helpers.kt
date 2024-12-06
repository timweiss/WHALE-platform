package de.mimuc.senseeverything.api

import android.util.Log
import com.android.volley.VolleyError
import de.mimuc.senseeverything.api.model.Study
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
        apiClient.getJson("https://sisensing.medien.ifi.lmu.de/v1/study/$studyId",
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
            response.getInt("durationDays")
        )

        return study
    }

    return null
}