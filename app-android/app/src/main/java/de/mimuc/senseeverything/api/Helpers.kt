package de.mimuc.senseeverything.api

import android.util.Log
import com.android.volley.VolleyError
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
            Log.e("Enrolment", "Error decoding error response: $e")
            ApiError(response.statusCode, "unknown", message)
        }
    } else {
        ApiError(-1, "unknown", message)
    }
}