package de.mimuc.senseeverything.service.accessibility

import android.util.Base64
import de.mimuc.senseeverything.service.accessibility.model.InteractionEvent
import de.mimuc.senseeverything.service.accessibility.model.ScreenSnapshot
import de.mimuc.senseeverything.service.accessibility.model.SkeletonNode
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.Writer
import java.util.zip.GZIPOutputStream

/**
 * Encodes a snapshot batch as gzip-compressed, Base64 (NO_WRAP) JSON.
 *
 * The JSON is streamed straight into the gzip stream instead of building a JSONObject graph and a
 * full JSON string first, so a large batch never exists uncompressed in memory. The output text is
 * identical to `ScreenSnapshot.toJson()` wrapped in the batch object: same key order, and strings
 * and numbers are formatted with org.json's own [JSONObject.quote] and [JSONObject.numberToString].
 */
object SnapshotBatchEncoder {
    fun encode(timestamp: Long, snapshots: List<ScreenSnapshot>): String {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).bufferedWriter(Charsets.UTF_8).use { writer ->
            writeBatch(writer, timestamp, snapshots)
        }
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }

    /** Writes the uncompressed batch JSON, exposed for tests. */
    fun writeBatch(out: Writer, timestamp: Long, snapshots: List<ScreenSnapshot>) {
        val json = StreamingJson(out)
        json.beginObject()
        json.key("timestamp").number(timestamp)
        json.key("count").number(snapshots.size)
        json.key("snapshots").beginArray()
        snapshots.forEach { writeSnapshot(json, it) }
        json.endArray()
        json.endObject()
    }

    private fun writeSnapshot(json: StreamingJson, snapshot: ScreenSnapshot) {
        json.beginObject()
        json.key("timestamp").number(snapshot.timestamp)
        json.key("appPackage").string(snapshot.appPackage)
        json.key("framework").string(snapshot.framework)
        json.key("skeleton").beginObject()
        json.key("signature").string(snapshot.skeleton.signature)
        json.key("nodes").beginArray()
        snapshot.skeleton.nodes.forEach { writeNode(json, it) }
        json.endArray()
        json.endObject()
        snapshot.interaction?.let { writeInteraction(json.key("interaction"), it) }
        json.endObject()
    }

    private fun writeNode(json: StreamingJson, node: SkeletonNode) {
        json.beginObject()
        json.key("id").number(node.id)
        json.key("parentId").number(node.parentId ?: -1)
        json.key("type").string(node.type.name)
        json.key("depth").number(node.depth)
        json.key("region").string(node.region.name)
        json.key("sizeClass").string(node.sizeClass.name)
        json.key("relativeX").number(node.relativeX)
        json.key("relativeY").number(node.relativeY)
        json.key("relativeWidth").number(node.relativeWidth)
        json.key("relativeHeight").number(node.relativeHeight)
        json.key("clickable").bool(node.clickable)
        json.key("scrollable").bool(node.scrollable)
        json.key("editable").bool(node.editable)
        json.key("focusable").bool(node.focusable)
        json.key("hasText").bool(node.hasText)
        node.textCategory?.let { json.key("textCategory").string(it.name) }
        json.key("hasImage").bool(node.hasImage)
        node.role?.let { json.key("role").string(it) }
        json.endObject()
    }

    private fun writeInteraction(json: StreamingJson, interaction: InteractionEvent) {
        json.beginObject()
        json.key("type").string(interaction.type.name)
        json.key("targetNodeId").number(interaction.targetNodeId)
        json.key("tapX").number(interaction.tapX)
        json.key("tapY").number(interaction.tapY)
        json.endObject()
    }

    /** Minimal JSON writer producing the same compact text as org.json's toString(). */
    private class StreamingJson(private val out: Writer) {
        private var needsComma = false

        fun beginObject(): StreamingJson = open('{')
        fun endObject(): StreamingJson = close('}')
        fun beginArray(): StreamingJson = open('[')
        fun endArray(): StreamingJson = close(']')

        fun key(name: String): StreamingJson {
            separate()
            out.write(JSONObject.quote(name))
            out.write(':'.code)
            needsComma = false
            return this
        }

        fun string(value: String): StreamingJson = raw(JSONObject.quote(value))
        fun number(value: Number): StreamingJson = raw(JSONObject.numberToString(value))
        fun bool(value: Boolean): StreamingJson = raw(if (value) "true" else "false")

        private fun open(c: Char): StreamingJson {
            separate()
            out.write(c.code)
            needsComma = false
            return this
        }

        private fun close(c: Char): StreamingJson {
            out.write(c.code)
            needsComma = true
            return this
        }

        private fun raw(value: String): StreamingJson {
            separate()
            out.write(value)
            needsComma = true
            return this
        }

        private fun separate() {
            if (needsComma) out.write(','.code)
        }
    }
}
