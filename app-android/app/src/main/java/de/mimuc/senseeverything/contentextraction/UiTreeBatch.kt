package de.mimuc.senseeverything.contentextraction

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.GZIPInputStream

/** One snapshot of a stored UI tree batch, as SnapshotBatchEncoder wrote it. */
class BatchSnapshot(
    val snapshotId: String,
    val timestamp: Long,
    val appPackage: String,
    /** the skeleton's node list; read it with [rawNodesFromJson] */
    val nodes: JSONArray
)

/** A UITree reading's data: Base64 (no wrap) of the gzip-compressed batch JSON. */
fun decodeUiTreeBatch(data: String): List<BatchSnapshot> {
    val json = GZIPInputStream(Base64.getDecoder().decode(data).inputStream())
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
    val snapshots = JSONObject(json).getJSONArray("snapshots")
    return (0 until snapshots.length()).map { i ->
        val s = snapshots.getJSONObject(i)
        BatchSnapshot(
            snapshotId = s.getString("snapshotId"),
            timestamp = s.getLong("timestamp"),
            appPackage = s.getString("appPackage"),
            nodes = s.getJSONObject("skeleton").getJSONArray("nodes")
        )
    }
}

/**
 * Whether a snapshot is in the sample. Reproducible offline:
 * `(int.from_bytes(hashlib.sha256(snapshot_id.encode()).digest()[:8], "big") >> 11) / 2**53 < rate`
 */
fun isSampled(snapshotId: String, rate: Double): Boolean {
    if (rate >= 1.0) return true
    return sampleFraction(snapshotId) < rate
}

/** The top 53 bits of SHA-256(snapshotId) as a fraction in [0, 1). */
internal fun sampleFraction(snapshotId: String): Double {
    val digest = MessageDigest.getInstance("SHA-256").digest(snapshotId.toByteArray(Charsets.UTF_8))
    val top = ByteBuffer.wrap(digest, 0, 8).long ushr 11
    return top.toDouble() / (1L shl 53).toDouble()
}
