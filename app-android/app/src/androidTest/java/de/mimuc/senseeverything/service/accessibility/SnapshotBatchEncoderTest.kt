package de.mimuc.senseeverything.service.accessibility

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.mimuc.senseeverything.service.accessibility.model.InteractionEvent
import de.mimuc.senseeverything.service.accessibility.model.InteractionType
import de.mimuc.senseeverything.service.accessibility.model.NodeType
import de.mimuc.senseeverything.service.accessibility.model.ScreenRegion
import de.mimuc.senseeverything.service.accessibility.model.ScreenSnapshot
import de.mimuc.senseeverything.service.accessibility.model.SizeClass
import de.mimuc.senseeverything.service.accessibility.model.SkeletonNode
import de.mimuc.senseeverything.service.accessibility.model.TextCategory
import de.mimuc.senseeverything.service.accessibility.model.TreeSkeleton
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringWriter
import java.util.zip.GZIPInputStream
import kotlin.random.Random

/**
 * Runs on a device because the reference output comes from Android's own org.json implementation.
 */
@RunWith(AndroidJUnit4::class)
class SnapshotBatchEncoderTest {
    private val trickyStrings = listOf(
        "com.instagram.android", "", "quote\"inside", "back\\slash", "slash/and</script>",
        "tab\tnew\nline\rcarriage\u000cfeed\bback", "control\u0001\u001f", "umlaut äöü ß",
        "emoji 🐳", "line separator", "non breaking"
    )
    private val floats = listOf(0f, -0f, 1f, 0.5f, 1f / 3f, 0.46296296f, 1.0000001f, 1e-7f, 123.25f)

    /** The format produced before streaming: JSONObject graph, then toString(). */
    private fun legacyJson(timestamp: Long, snapshots: List<ScreenSnapshot>): String =
        JSONObject().apply {
            put("timestamp", timestamp)
            put("count", snapshots.size)
            put("snapshots", JSONArray().apply { snapshots.forEach { put(it.toJson()) } })
        }.toString()

    private fun randomNode(random: Random, id: Int) = SkeletonNode(
        id = id,
        parentId = if (id == 0) null else random.nextInt(id),
        type = NodeType.values().random(random),
        depth = random.nextInt(40),
        region = ScreenRegion.values().random(random),
        sizeClass = SizeClass.values().random(random),
        relativeX = if (random.nextBoolean()) floats.random(random) else random.nextFloat(),
        relativeY = random.nextFloat(),
        relativeWidth = floats.random(random),
        relativeHeight = random.nextFloat() * 2,
        clickable = random.nextBoolean(),
        scrollable = random.nextBoolean(),
        editable = random.nextBoolean(),
        focusable = random.nextBoolean(),
        hasText = random.nextBoolean(),
        textCategory = if (random.nextBoolean()) TextCategory.values().random(random) else null,
        hasImage = random.nextBoolean(),
        role = if (random.nextInt(4) == 0) trickyStrings.random(random) else null
    )

    private fun randomSnapshot(random: Random): ScreenSnapshot {
        val interaction = random.nextInt(3) == 0
        return ScreenSnapshot(
            timestamp = random.nextLong(0, 4_000_000_000_000L),
            appPackage = trickyStrings.random(random),
            framework = if (interaction) "" else "NATIVE",
            skeleton = TreeSkeleton(
                signature = "%064x".format(random.nextLong()),
                nodes = if (interaction) emptyList() else List(random.nextInt(0, 200)) { randomNode(random, it) }
            ),
            interaction = if (interaction) InteractionEvent(
                type = InteractionType.values().random(random),
                targetNodeId = random.nextInt(200),
                tapX = floats.random(random),
                tapY = random.nextFloat()
            ) else null
        )
    }

    @Test
    fun streamedJsonMatchesLegacyJsonExactly() {
        val random = Random(1234)
        repeat(100) {
            val timestamp = random.nextLong(0, 4_000_000_000_000L)
            val snapshots = List(random.nextInt(0, 6)) { randomSnapshot(random) }

            val streamed = StringWriter().also { SnapshotBatchEncoder.writeBatch(it, timestamp, snapshots) }.toString()

            assertEquals(legacyJson(timestamp, snapshots), streamed)
        }
    }

    @Test
    fun encodedPayloadDecompressesToLegacyJson() {
        val random = Random(99)
        val timestamp = 1_759_000_000_000L
        val snapshots = List(4) { randomSnapshot(random) }

        val payload = SnapshotBatchEncoder.encode(timestamp, snapshots)
        val json = GZIPInputStream(Base64.decode(payload, Base64.NO_WRAP).inputStream())
            .bufferedReader(Charsets.UTF_8).readText()

        assertEquals(legacyJson(timestamp, snapshots), json)
    }
}
