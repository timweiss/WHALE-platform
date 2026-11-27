package de.mimuc.senseeverything.contentextraction

import de.mimuc.senseeverything.service.accessibility.SnapshotBatchEncoder
import de.mimuc.senseeverything.service.accessibility.model.NodeType
import de.mimuc.senseeverything.service.accessibility.model.ScreenRegion
import de.mimuc.senseeverything.service.accessibility.model.ScreenSnapshot
import de.mimuc.senseeverything.service.accessibility.model.SizeClass
import de.mimuc.senseeverything.service.accessibility.model.SkeletonNode
import de.mimuc.senseeverything.service.accessibility.model.TreeSkeleton
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.StringWriter
import java.util.Base64
import java.util.zip.GZIPOutputStream

class UiTreeBatchTest {
    private fun node(id: Int, parentId: Int?, x: Float, text: String?) = SkeletonNode(
        id = id, parentId = parentId, type = NodeType.TEXT, className = null, depth = id,
        region = ScreenRegion.CENTER, sizeClass = SizeClass.SMALL,
        relativeX = x, relativeY = 0.5f, relativeWidth = 0.25f, relativeHeight = 0.0625f,
        clickable = false, scrollable = false, editable = false, focusable = false,
        hasText = text != null, textCategory = null, hasImage = false, role = null,
        text = text, contentDescription = null
    )

    /** What UITreeSensor stores: the encoder's JSON, gzip-compressed, Base64 without line breaks. */
    private fun stored(snapshots: List<ScreenSnapshot>): String {
        val json = StringWriter().also { SnapshotBatchEncoder.writeBatch(it, 1_762_819_260_000, snapshots) }.toString()
        val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(json.toByteArray()) } }
        return Base64.getEncoder().encodeToString(gz.toByteArray())
    }

    @Test
    fun decodesTheStoredBatchWithBoundsFromTheDecimalText() {
        val snapshots = listOf(
            ScreenSnapshot(1_000, "com.instagram.android", "VIEW",
                TreeSkeleton("sig", listOf(node(0, null, 0f, null), node(1, 0, 0.152f, "Hallo"))), snapshotId = "s1"),
            ScreenSnapshot(2_000, "com.other", "VIEW", TreeSkeleton("sig2", listOf(node(0, null, 0f, null))), snapshotId = "s2")
        )

        val decoded = decodeUiTreeBatch(stored(snapshots))

        assertEquals(listOf("s1", "s2"), decoded.map { it.snapshotId })
        assertEquals(listOf(1_000L, 2_000L), decoded.map { it.timestamp })
        assertEquals(listOf("com.instagram.android", "com.other"), decoded.map { it.appPackage })
        val text = rawNodesFromJson(decoded[0].nodes)[1]
        assertEquals("Hallo", text.text)
        // the harness sees 0.152 (parsed from "0.152"), not the Float widened to 0.15199999511
        assertEquals(0.152, text.relativeX)
        assertNotEquals(0.152f.toDouble(), text.relativeX)
    }

    @Test
    fun samplingMatchesThePythonFormula() {
        // (int.from_bytes(hashlib.sha256(id.encode()).digest()[:8], "big") >> 11) / 2**53
        assertEquals(0.7913742705223815, sampleFraction("a"))
        assertEquals(0.6142942201375813, sampleFraction("synthetic-feed"))
        assertEquals(0.2120652409932905, sampleFraction("3747165040430959000"))
        assertTrue(isSampled("3747165040430959000", 0.25))
        assertFalse(isSampled("a", 0.25))
        assertTrue(isSampled("a", 1.0))
    }
}
