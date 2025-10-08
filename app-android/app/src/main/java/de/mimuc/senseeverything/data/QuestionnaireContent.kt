package de.mimuc.senseeverything.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.android.volley.NetworkResponse
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.Response.ErrorListener
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import de.mimuc.senseeverything.api.ApiClient
import de.mimuc.senseeverything.api.model.ema.CircumplexElement
import de.mimuc.senseeverything.api.model.ema.FullQuestionnaire
import de.mimuc.senseeverything.api.model.ema.QuestionnaireElementType
import de.mimuc.senseeverything.logging.WHALELog
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

fun getCircumplexFilename(element: CircumplexElement): String {
    return "questionnaire_circumplex_element_${element.questionnaireId}_${element.id}.png"
}

suspend fun persistQuestionnaireElementContent(context: Context, questionnaires: List<FullQuestionnaire>) {
    for (qq in questionnaires) {
        for (element in qq.elements) {
            when (element.type) {
                QuestionnaireElementType.CIRCUMPLEX -> {
                    val circumplexElement = element as CircumplexElement
                    val imageUrl = circumplexElement.configuration.imageUrl

                    val bytes = ApiClient.getInstance(context).getRawBytes(imageUrl)
                    if (bytes != null) {
                        val fileName = getCircumplexFilename(element)
                        saveImageToFile(context, bytes, fileName)
                    }
                }

                else -> return // nothing to cache for the other elements
            }
        }
    }
}

suspend fun ApiClient.getRawBytes(url: String): ByteArray? {
    val bytes = suspendCoroutine { continuation ->
        val imageRequest =
            object : Request<ByteArray>(Method.GET, url, ErrorListener {
            }) {
                override fun deliverResponse(response: ByteArray) {
                    return continuation.resume(response)
                }

                override fun parseNetworkResponse(response: NetworkResponse): Response<ByteArray> {
                    return Response.success(response.data, HttpHeaderParser.parseCacheHeaders(response))
                }

                override fun deliverError(error: VolleyError) {
                    continuation.resume(null)
                }
            }

        this.addToRequestQueue(imageRequest)
    }

    return bytes
}

suspend fun getCircumplexImageBitmap(context: Context, element: CircumplexElement): Bitmap? {
    var bitmap = suspendCoroutine { continuation ->
        val fileName = getCircumplexFilename(element)
        try {
            context.openFileInput(fileName).use { fis ->
                continuation.resume(BitmapFactory.decodeStream(fis))
            }
        } catch (e: IOException) {
            e.printStackTrace()
            continuation.resume(null)
        }
    }

    // file not found, try downloading it
    if (bitmap == null) {
        val bytes = ApiClient.getInstance(context).getRawBytes(element.configuration.imageUrl)
        if (bytes != null) {
            saveImageToFile(context, bytes, getCircumplexFilename(element))
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }

    return bitmap
}

private fun saveImageToFile(context: Context, imageData: ByteArray, fileName: String) {
    try {
        context.openFileOutput(fileName, Context.MODE_PRIVATE).use { fos ->
            fos.write(imageData)
        }
    } catch (e: IOException) {
        WHALELog.e("QuestionnaireContent", "Error saving image to file: $fileName", e)
        e.printStackTrace()
    }
}