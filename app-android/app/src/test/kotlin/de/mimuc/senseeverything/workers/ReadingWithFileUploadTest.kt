package de.mimuc.senseeverything.workers

import com.android.volley.AuthFailureError
import com.android.volley.ClientError
import com.android.volley.NetworkError
import com.android.volley.ServerError
import com.android.volley.TimeoutError
import de.mimuc.senseeverything.db.models.LogData
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReadingWithFileUploadTest {
    @Test
    fun bodyHasTheReadingAndTheFile() {
        val row = LogData(5L, "Screenshot", """{"snapshotId":"a"}""", hasFile = true, filePath = "/a.jpg")
        val body = readingWithFileBody(row, "a.jpg", "image".toByteArray())

        val text = Buffer().also { body.writeTo(it) }.readUtf8()
        assertTrue(body.contentType().toString().startsWith("multipart/form-data; boundary="))
        assertEquals(2, body.size)
        assertTrue("name=\"reading\"" in text)
        val reading = """{"sensorType":"Screenshot","timestamp":5,"data":"{\"snapshotId\":\"a\"}","localId":"${row.localId}"}"""
        assertTrue(reading in text) { text }
        assertTrue("name=\"file\"; filename=\"a.jpg\"" in text)
        assertTrue("Content-Type: application/octet-stream" in text)
    }

    @Test
    fun onlyPermanentRejectionsDropTheReading() {
        listOf(400, 413, 415).forEach { assertTrue(isPermanentFileRejection(it)) }
        // not enrolled, endpoint missing, rate limited, server errors: kept
        listOf(401, 403, 404, 429, 500, 502, 503).forEach { assertFalse(isPermanentFileRejection(it)) }
    }

    @Test
    fun onlyServerFailuresAreServerFailures() {
        assertTrue(isServerFailure(ServerError()))
        listOf(ClientError(), AuthFailureError(), NetworkError(), TimeoutError()).forEach { assertFalse(isServerFailure(it)) }
    }
}
