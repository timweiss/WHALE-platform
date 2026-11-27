package de.mimuc.senseeverything.contentextraction

import de.mimuc.senseeverything.api.model.ContentExtractionModel
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import kotlin.random.Random

class ModelDownloaderTest {
    @TempDir
    lateinit var root: File

    private val server = MockWebServer()
    private val bytes = Random(42).nextBytes(300 * 1024)
    private val model = ContentExtractionModel(
        sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        sizeBytes = bytes.size.toLong()
    )
    private val plenty: (File) -> Long = { Long.MAX_VALUE }

    @BeforeEach
    fun start() = server.start()

    @AfterEach
    fun stop() = server.shutdown()

    private fun download(
        usableSpace: (File) -> Long = plenty,
        shouldContinue: () -> Boolean = { true }
    ) = ModelDownloader(OkHttpClient(), usableSpace)
        .download(server.url("/model").toString(), "token", model, root, shouldContinue)

    private val modelFile get() = ModelStore.modelFile(root, model.sha256)
    private val partFile get() = ModelStore.partFile(root, model.sha256)

    private fun body(from: Int = 0, to: Int = bytes.size) = Buffer().write(bytes.copyOfRange(from, to))

    @Test
    fun downloadsVerifiesAndMovesTheFileIntoPlace() {
        server.enqueue(MockResponse().setBody(body()))

        assertEquals(DownloadResult.Ready, download())

        assertArrayEquals(bytes, modelFile.readBytes())
        assertFalse(partFile.exists())
        val request = server.takeRequest()
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertEquals("identity", request.getHeader("Accept-Encoding"))
        assertNull(request.getHeader("Range"))
        assertTrue(ModelStore.isReady(root, model))
    }

    @Test
    fun resumesFromThePartialFile() {
        partFile.writeBytes(bytes.copyOfRange(0, 100_000))
        server.enqueue(
            MockResponse().setResponseCode(206)
                .setHeader("Content-Range", "bytes 100000-${bytes.size - 1}/${bytes.size}")
                .setBody(body(from = 100_000))
        )

        assertEquals(DownloadResult.Ready, download())

        assertEquals("bytes=100000-", server.takeRequest().getHeader("Range"))
        assertArrayEquals(bytes, modelFile.readBytes())
    }

    @Test
    fun startsOverWhenTheServerIgnoresTheRange() {
        partFile.writeBytes(ByteArray(100_000)) // whatever was there is replaced
        server.enqueue(MockResponse().setBody(body()))

        assertEquals(DownloadResult.Ready, download())

        assertArrayEquals(bytes, modelFile.readBytes())
    }

    @Test
    fun keepsWhatArrivedWhenTheConnectionBreaks() {
        server.enqueue(MockResponse().setBody(body()).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))

        assertTrue(download() is DownloadResult.Retry)

        assertTrue(partFile.length() in 1 until bytes.size, "part file has ${partFile.length()} bytes")
        assertFalse(modelFile.exists())
    }

    @Test
    fun stopsWhenTheWorkerIsStoppedAndKeepsThePart() {
        server.enqueue(MockResponse().setBody(body()))
        var reads = 0

        val result = download(shouldContinue = { reads++ < 2 })

        assertEquals(DownloadResult.Retry("stopped"), result)
        assertTrue(partFile.length() in 1 until bytes.size)
    }

    @Test
    fun dropsAFileWithTheWrongHash() {
        val corrupted = bytes.copyOf().also { it[1234] = (it[1234] + 1).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(corrupted)))

        assertTrue(download() is DownloadResult.Failed)

        assertFalse(partFile.exists())
        assertFalse(modelFile.exists())
    }

    @Test
    fun failsBeforeDownloadingAFileOfAnotherSize() {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(10))))

        assertTrue(download() is DownloadResult.Failed)
        assertFalse(modelFile.exists())
    }

    @Test
    fun failsWhenTheServerHasNoSuchModelAndRetriesOnServerErrors() {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(DownloadResult.Failed("HTTP 404"), download())

        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(DownloadResult.Retry("HTTP 503"), download())
    }

    @Test
    fun waitsForSpaceWithoutRequesting() {
        val result = download(usableSpace = { model.sizeBytes })

        assertTrue(result is DownloadResult.Retry && result.reason.startsWith("not enough space"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun failedDownloadsAreCountedUntilASuccessClearsThem() {
        val failures = DownloadFailures(root)
        assertEquals(0, failures.read().count)

        repeat(DownloadFailures.MAX - 1) { failures.record("HTTP 404") }
        assertFalse(failures.exhausted())
        assertEquals(DownloadFailures.State(DownloadFailures.MAX, "SHA-256 mismatch"), failures.record("SHA-256 mismatch"))
        assertTrue(failures.exhausted())

        failures.clear()
        assertEquals(0, failures.read().count)
    }

    @Test
    fun doesNothingWhenTheModelIsThere() {
        modelFile.writeBytes(bytes)

        assertEquals(DownloadResult.Ready, download())
        assertEquals(0, server.requestCount)
    }
}
