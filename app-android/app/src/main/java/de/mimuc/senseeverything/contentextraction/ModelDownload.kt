package de.mimuc.senseeverything.contentextraction

import android.content.Context
import de.mimuc.senseeverything.api.model.ContentExtractionModel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * The model on disk: one file per SHA-256 in noBackupFilesDir. A download goes to `<sha256>.litertlm.part`
 * and is only renamed to `<sha256>.litertlm` after its hash matched, so the final name means verified.
 * The file names are the state; other processes (the extraction worker) only check them.
 */
object ModelStore {
    fun root(context: Context) = File(context.noBackupFilesDir, "content_extraction")

    fun modelFile(root: File, sha256: String) = File(root, "$sha256.litertlm")

    fun partFile(root: File, sha256: String) = File(root, "$sha256.litertlm.part")

    fun isReady(root: File, model: ContentExtractionModel): Boolean =
        modelFile(root, model.sha256).length() == model.sizeBytes
}

sealed interface DownloadResult {
    data object Ready : DownloadResult

    /** Try again later: network, server, not enough space, or the worker was stopped. */
    data class Retry(val reason: String) : DownloadResult

    /** Needs a fix on the server or in the study config; a new download starts from scratch. */
    data class Failed(val reason: String) : DownloadResult
}

/**
 * Downloads the model with resume: a partial file is continued with an HTTP Range request. Blocking,
 * call it on an IO thread. [usableSpace] is a parameter for tests.
 */
class ModelDownloader(
    private val client: OkHttpClient,
    private val usableSpace: (File) -> Long = { it.usableSpace }
) {
    companion object {
        /** kept free besides the model, so the phone isn't left without space */
        const val SPACE_MARGIN_BYTES = 256L * 1024 * 1024
        private const val BUFFER_BYTES = 64 * 1024
        private const val PROGRESS_EVERY_BYTES = 16L * 1024 * 1024
        private val CONTENT_RANGE = Regex("""bytes (\d+)-\d+/(\d+)""")
    }

    fun download(
        url: String,
        token: String,
        model: ContentExtractionModel,
        root: File,
        shouldContinue: () -> Boolean = { true },
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> }
    ): DownloadResult {
        root.mkdirs()
        if (ModelStore.isReady(root, model)) return DownloadResult.Ready
        val part = ModelStore.partFile(root, model.sha256)
        if (part.length() > model.sizeBytes) part.delete()

        if (part.length() < model.sizeBytes) {
            val offset = part.length()
            val needed = model.sizeBytes - offset + SPACE_MARGIN_BYTES
            val free = usableSpace(root)
            if (free < needed) return DownloadResult.Retry("not enough space: $needed bytes needed, $free free")

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                // no transparent gzip: ranges and lengths must refer to the file's bytes
                .header("Accept-Encoding", "identity")
                .apply { if (offset > 0) header("Range", "bytes=$offset-") }
                .build()

            val result = try {
                client.newCall(request).execute().use { response ->
                    receive(response, part, offset, model, shouldContinue, onProgress)
                }
            } catch (e: IOException) {
                // the bytes that arrived stay in the part file, the next attempt continues from there
                DownloadResult.Retry("${e.javaClass.simpleName}: ${e.message}")
            }
            if (result != null) return result
        }

        val length = part.length()
        if (length < model.sizeBytes) return DownloadResult.Retry("incomplete: $length of ${model.sizeBytes} bytes")
        val hash = sha256Hex(part)
        if (hash != model.sha256) {
            part.delete()
            return DownloadResult.Failed("SHA-256 mismatch: got $hash")
        }
        if (!part.renameTo(ModelStore.modelFile(root, model.sha256))) {
            return DownloadResult.Retry("could not move the verified file into place")
        }
        return DownloadResult.Ready
    }

    /** Writes the response into the part file; null once the body arrived completely. */
    private fun receive(
        response: Response,
        part: File,
        offset: Long,
        model: ContentExtractionModel,
        shouldContinue: () -> Boolean,
        onProgress: (downloaded: Long, total: Long) -> Unit
    ): DownloadResult? {
        val append = when (response.code) {
            206 -> {
                val range = response.header("Content-Range")?.let { CONTENT_RANGE.matchEntire(it) }
                val total = range?.groupValues?.get(2)?.toLong()
                if (total != null && total != model.sizeBytes) {
                    return DownloadResult.Failed("server file has $total bytes, the study config says ${model.sizeBytes}")
                }
                if (range == null || range.groupValues[1].toLong() != offset) {
                    part.delete()
                    return DownloadResult.Retry("unexpected Content-Range ${response.header("Content-Range")}, starting over")
                }
                true
            }
            200 -> {
                val length = response.body?.contentLength() ?: -1
                if (length >= 0 && length != model.sizeBytes) {
                    return DownloadResult.Failed("server file has $length bytes, the study config says ${model.sizeBytes}")
                }
                false // the server sent the whole file, it replaces the part
            }
            416 -> {
                part.delete()
                return DownloadResult.Retry("range not satisfiable, starting over")
            }
            401, 403, 404 -> return DownloadResult.Failed("HTTP ${response.code}")
            else -> return DownloadResult.Retry("HTTP ${response.code}")
        }

        val body = response.body ?: return DownloadResult.Retry("response without body")
        var downloaded = if (append) offset else 0L
        var reported = downloaded
        // use is inline: a return inside leaves receive() and still closes both streams
        FileOutputStream(part, append).use { out ->
            body.byteStream().use { input ->
                val buffer = ByteArray(BUFFER_BYTES)
                while (true) {
                    if (!shouldContinue()) return DownloadResult.Retry("stopped")
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    downloaded += n
                    if (downloaded - reported >= PROGRESS_EVERY_BYTES) {
                        onProgress(downloaded, model.sizeBytes)
                        reported = downloaded
                    }
                }
            }
        }
        onProgress(downloaded, model.sizeBytes)
        return null
    }
}

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            digest.update(buffer, 0, n)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
