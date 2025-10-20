package de.mimuc.senseeverything.api

import android.content.Context
import com.android.volley.NetworkResponse
import com.android.volley.ParseError
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.Charset
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class ApiClient private constructor(context: Context) {
    private val requestQueue: RequestQueue = Volley.newRequestQueue(context.applicationContext)

    companion object {
        @Volatile
        private var INSTANCE: ApiClient? = null

        fun getInstance(context: Context): ApiClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ApiClient(context).also { INSTANCE = it }
            }
        }
    }

    fun <T> addToRequestQueue(request: Request<T>) {
        requestQueue.add(request)
    }

    // GET request
    fun get(url: String, listener: Response.Listener<String>, errorListener: Response.ErrorListener) {
        val stringRequest = StringRequest(Request.Method.GET, url, listener, errorListener)
        addToRequestQueue(stringRequest)
    }

    // Kotlinx.serialization support functions

    /**
     * POST request with kotlinx.serialization support
     * Serializes the request object to JSON and deserializes the response
     */
    suspend inline fun <reified T, reified R> postSerialized(
        url: String,
        requestData: T,
        headers: Map<String, String> = emptyMap()
    ): R = postSerialized(url, requestData, headers, Json)

    /**
     * POST request with kotlinx.serialization support using a custom JSON instance
     * Serializes the request object to JSON and deserializes the response
     */
    suspend inline fun <reified T, reified R> postSerialized(
        url: String,
        requestData: T,
        headers: Map<String, String> = emptyMap(),
        json: Json
    ): R = suspendCoroutine { continuation ->
        val request = object : Request<String>(Method.POST, url, Response.ErrorListener { error ->
            continuation.resumeWithException(error)
        }) {
            override fun getHeaders(): Map<String, String> {
                val requestHeaders = HashMap<String, String>()
                requestHeaders["Content-Type"] = "application/json"
                requestHeaders["Accept"] = "application/json"
                if (headers.isNotEmpty()) {
                    requestHeaders.putAll(headers)
                }
                return requestHeaders
            }

            override fun getBody(): ByteArray {
                return try {
                    val jsonString = json.encodeToString(requestData)
                    jsonString.toByteArray(Charsets.UTF_8)
                } catch (e: Exception) {
                    throw Exception("Failed to serialize request: ${e.message}")
                }
            }

            override fun parseNetworkResponse(response: NetworkResponse): Response<String> {
                return try {
                    val charset = HttpHeaderParser.parseCharset(response.headers, "utf-8")
                    val responseString = String(response.data, Charset.forName(charset))
                    Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response))
                } catch (e: Exception) {
                    Response.error(ParseError(e))
                }
            }

            override fun deliverResponse(response: String) {
                try {
                    val deserializedResponse = json.decodeFromString<R>(response)
                    continuation.resume(deserializedResponse)
                } catch (e: Exception) {
                    continuation.resumeWithException(Exception("Failed to deserialize response: ${e.message}"))
                }
            }
        }
        
        addToRequestQueue(request)
    }

    /**
     * GET request with kotlinx.serialization support
     * Deserializes the JSON response to the specified type
     */
    suspend inline fun <reified T> getSerialized(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): T = getSerialized(url, headers, Json)

    /**
     * GET request with kotlinx.serialization support using a custom JSON instance
     * Deserializes the JSON response to the specified type
     */
    suspend inline fun <reified T> getSerialized(
        url: String,
        headers: Map<String, String> = emptyMap(),
        json: Json
    ): T = suspendCoroutine { continuation ->
        val request = object : Request<String>(Method.GET, url, Response.ErrorListener { error ->
            continuation.resumeWithException(error)
        }) {
            override fun getHeaders(): Map<String, String> {
                val requestHeaders = HashMap<String, String>()
                requestHeaders["Content-Type"] = "application/json"
                requestHeaders["Accept"] = "application/json"
                if (headers.isNotEmpty()) {
                    requestHeaders.putAll(headers)
                }
                return requestHeaders
            }

            override fun parseNetworkResponse(response: NetworkResponse): Response<String> {
                return try {
                    val charset = HttpHeaderParser.parseCharset(response.headers, "utf-8")
                    val responseString = String(response.data, Charset.forName(charset))
                    Response.success(responseString, HttpHeaderParser.parseCacheHeaders(response))
                } catch (e: Exception) {
                    Response.error(ParseError(e))
                }
            }

            override fun deliverResponse(response: String) {
                try {
                    val deserializedResponse = json.decodeFromString<T>(response)
                    continuation.resume(deserializedResponse)
                } catch (e: Exception) {
                    continuation.resumeWithException(Exception("Failed to deserialize response: ${e.message}"))
                }
            }
        }

        addToRequestQueue(request)
    }

    /**
     * GET request with kotlinx.serialization support using endpoint and token
     * Automatically constructs the full URL from baseUrl + endpoint and adds Authorization header
     */
    suspend inline fun <reified T> getSerialized(
        endpoint: String,
        token: String
    ): T {
        val headers = mapOf("Authorization" to "Bearer $token")
        return getSerialized(endpoint, headers)
    }
}