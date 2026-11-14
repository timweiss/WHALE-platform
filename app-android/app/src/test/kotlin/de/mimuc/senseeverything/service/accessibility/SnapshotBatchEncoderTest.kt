package de.mimuc.senseeverything.service.accessibility

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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.StringWriter

/**
 * The encoder writes every field by hand, so it must be kept in sync with the model's toJson().
 * Compares parsed JSON: the org.json used in JVM tests does not keep key order.
 */
class SnapshotBatchEncoderTest {
    // floats that are exact in binary, so Float and parsed Double compare equal
    private fun node(id: Int, withOptionals: Boolean) = SkeletonNode(
        id = id,
        parentId = if (id == 0) null else 0,
        type = NodeType.TEXT,
        className = if (withOptionals) "android.widget.TextView" else null,
        depth = id,
        region = ScreenRegion.CENTER,
        sizeClass = SizeClass.SMALL,
        relativeX = 0.25f,
        relativeY = 0.5f,
        relativeWidth = 0.125f,
        relativeHeight = 0.0625f,
        clickable = true,
        scrollable = false,
        editable = false,
        focusable = true,
        hasText = withOptionals,
        textCategory = if (withOptionals) TextCategory.SHORT_PHRASE else null,
        hasImage = false,
        role = if (withOptionals) "button" else null,
        text = if (withOptionals) "Hello \"world\"\n" else null,
        contentDescription = if (withOptionals) "Send message" else null
    )

    private fun snapshot(withOptionals: Boolean) = ScreenSnapshot(
        timestamp = 1_762_819_200_000,
        appPackage = "com.instagram.android",
        framework = "VIEW",
        skeleton = TreeSkeleton("abc123", listOf(node(0, withOptionals), node(1, withOptionals))),
        interaction = if (withOptionals) InteractionEvent(InteractionType.TAP, 1, 0.5f, 0.75f) else null
    )

    private fun assertEncodesLikeToJson(snapshots: List<ScreenSnapshot>) {
        val timestamp = 1_762_819_260_000
        val expected = JSONObject()
            .put("timestamp", timestamp)
            .put("count", snapshots.size)
            .put("snapshots", JSONArray(snapshots.map { it.toJson() }))
        val actual = JSONObject(StringWriter().also { SnapshotBatchEncoder.writeBatch(it, timestamp, snapshots) }.toString())

        assertTrue(expected.similar(actual), "encoder output differs from toJson()\nexpected: $expected\nactual:   $actual")
    }

    @Test
    fun encodesAllFieldsLikeToJson() {
        assertEncodesLikeToJson(listOf(snapshot(withOptionals = true)))
    }

    @Test
    fun omitsAbsentOptionalFieldsLikeToJson() {
        assertEncodesLikeToJson(listOf(snapshot(withOptionals = false)))
    }

    @Test
    fun encodesSeveralSnapshotsAndAnEmptyBatch() {
        assertEncodesLikeToJson(listOf(snapshot(withOptionals = true), snapshot(withOptionals = false)))
        assertEncodesLikeToJson(emptyList())
    }
}
