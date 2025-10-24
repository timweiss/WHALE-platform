package de.mimuc.senseeverything.api

import de.mimuc.senseeverything.logging.WHALELog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Result of a chunked upload operation
 */
data class UploadResult(
    val totalItems: Int,
    val chunksUploaded: Int,
    val totalBytesUploaded: Long,
    val usedFastPath: Boolean,
    val errors: List<String> = emptyList()
)

/**
 * Helper for uploading data with automatic size-based chunking
 *
 * Uses actual serialized payload size to determine if chunking is needed.
 * Fast path: small payloads uploaded in single batch
 * Slow path: large payloads split into multiple chunks
 */
object ChunkedUploadHelper {
    const val TAG = "ChunkedUploadHelper"

    // 90% of 1MB nginx default limit - provides safety buffer
    const val DEFAULT_SAFE_THRESHOLD = 900_000

    // Minimum items per chunk before giving up
    const val MIN_CHUNK_SIZE = 10

    // fallback for very large individual items
    const val MINIMUM_CHUNK_SIZE = 1

    /**
     * Upload data with automatic size-based chunking to prevent the backend rejecting uploads.
     *
     * @param data List of items to upload
     * @param maxBatchSize Maximum items per batch (default batch size)
     * @param safeThresholdBytes Maximum safe payload size in bytes
     * @param client ApiClient instance for making requests
     * @param url Target URL for upload
     * @param headers Request headers (including auth)
     * @param json Json serializer instance
     * @return UploadResult with statistics
     */
    suspend inline fun <reified T> uploadWithSizeBasedChunking(
        data: List<T>,
        maxBatchSize: Int,
        safeThresholdBytes: Int = DEFAULT_SAFE_THRESHOLD,
        client: ApiClient,
        url: String,
        headers: Map<String, String>,
        json: Json = Json
    ): UploadResult {
        if (data.isEmpty()) {
            WHALELog.d(TAG, "No data to upload")
            return UploadResult(0, 0, 0, true)
        }

        // Serialize to measure actual size
        val jsonString = json.encodeToString(data)
        val sizeBytes = jsonString.toByteArray(Charsets.UTF_8).size

        WHALELog.d(TAG, "Batch of ${data.size} items serializes to $sizeBytes bytes")

        // FAST PATH: payload fits within threshold
        if (sizeBytes < safeThresholdBytes) {
            WHALELog.i(TAG, "Using fast path: uploading ${data.size} items ($sizeBytes bytes) in single batch")

            try {
                client.postJsonString(url, jsonString, headers)
                return UploadResult(
                    totalItems = data.size,
                    chunksUploaded = 1,
                    totalBytesUploaded = sizeBytes.toLong(),
                    usedFastPath = true
                )
            } catch (e: Exception) {
                WHALELog.e(TAG, "Fast path upload failed: ${e.message}")
                throw e
            }
        }

        // SLOW PATH: payload too large, needs chunking
        WHALELog.w(TAG, "Payload too large ($sizeBytes bytes), using slow path with chunking")

        return uploadInChunks(
            data = data,
            maxBatchSize = maxBatchSize,
            safeThresholdBytes = safeThresholdBytes,
            client = client,
            url = url,
            headers = headers,
            json = json
        )
    }

    /**
     * Upload data in size-appropriate chunks
     */
    suspend inline fun <reified T> uploadInChunks(
        data: List<T>,
        maxBatchSize: Int,
        safeThresholdBytes: Int,
        client: ApiClient,
        url: String,
        headers: Map<String, String>,
        json: Json
    ): UploadResult {
        // Calculate optimal chunk size based on measured size
        val totalSize = json.encodeToString(data).toByteArray(Charsets.UTF_8).size
        val bytesPerItem = totalSize.toDouble() / data.size

        var itemsPerChunk = (safeThresholdBytes / bytesPerItem).toInt()
            .coerceAtMost(maxBatchSize)
            .coerceAtLeast(MIN_CHUNK_SIZE)

        WHALELog.i(TAG, "Calculated $bytesPerItem bytes/item, using chunks of $itemsPerChunk items")

        var totalBytesUploaded = 0L
        var chunksUploaded = 0
        val errors = mutableListOf<String>()

        var remainingData = data
        while (remainingData.isNotEmpty()) {
            val chunk = remainingData.take(itemsPerChunk)

            try {
                val result = uploadSingleChunk(
                    chunk = chunk,
                    safeThresholdBytes = safeThresholdBytes,
                    client = client,
                    url = url,
                    headers = headers,
                    json = json
                )

                totalBytesUploaded += result.bytesUploaded
                chunksUploaded++
                remainingData = remainingData.drop(chunk.size)

                WHALELog.i(TAG, "Chunk $chunksUploaded uploaded: ${chunk.size} items, ${result.bytesUploaded} bytes")

            } catch (e: Exception) {
                // If chunk is still too large and we can split it further
                if (isPayloadTooLargeError(e) && chunk.size > MINIMUM_CHUNK_SIZE) {
                    WHALELog.w(TAG, "Chunk still too large, reducing size further")
                    itemsPerChunk = (itemsPerChunk / 2).coerceAtLeast(MINIMUM_CHUNK_SIZE)
                    // Don't drop the data, retry with smaller chunk on next iteration
                    continue
                } else if (isPayloadTooLargeError(e) && chunk.size == MINIMUM_CHUNK_SIZE) {
                    // Last resort: single item (size 1) still fails with 413
                    // This item is too large, drop it and continue
                    val errorMsg = "Failed to upload chunk of ${chunk.size} item(s): ${e.javaClass.simpleName} - ${e.message}"
                    WHALELog.e(TAG, "Dropping unuploadable chunk as last resort (payload too large): $errorMsg")
                    errors.add(errorMsg)

                    // Drop the failed chunk by advancing past it
                    remainingData = remainingData.drop(chunk.size)

                    // Continue processing remaining chunks
                    continue
                } else {
                    // Non-size-related error (network, timeout, auth, etc.)
                    // Don't drop data, propagate the error to let worker handle retry logic
                    WHALELog.e(TAG, "Non-recoverable error uploading chunk: ${e.javaClass.simpleName} - ${e.message}")
                    throw e
                }
            }
        }

        return UploadResult(
            totalItems = data.size,
            chunksUploaded = chunksUploaded,
            totalBytesUploaded = totalBytesUploaded,
            usedFastPath = false,
            errors = errors
        )
    }

    /**
     * Upload a single chunk with size verification
     */
    suspend inline fun <reified T> uploadSingleChunk(
        chunk: List<T>,
        safeThresholdBytes: Int,
        client: ApiClient,
        url: String,
        headers: Map<String, String>,
        json: Json
    ): ChunkUploadResult {
        val chunkJson = json.encodeToString(chunk)
        val chunkSize = chunkJson.toByteArray(Charsets.UTF_8).size

        // Safety check: warn if chunk is close to threshold
        if (chunkSize >= safeThresholdBytes) {
            WHALELog.w(TAG, "Warning: chunk size ($chunkSize bytes) at or above threshold ($safeThresholdBytes bytes)")
        }

        client.postJsonString(url, chunkJson, headers)

        return ChunkUploadResult(
            itemsUploaded = chunk.size,
            bytesUploaded = chunkSize.toLong()
        )
    }

    /**
     * Check if error is a "payload too large" error (HTTP 413)
     */
    fun isPayloadTooLargeError(e: Exception): Boolean {
        return e is com.android.volley.ClientError && e.networkResponse?.statusCode == 413
    }

    /**
     * Result of uploading a single chunk
     */
    data class ChunkUploadResult(
        val itemsUploaded: Int,
        val bytesUploaded: Long
    )
}
