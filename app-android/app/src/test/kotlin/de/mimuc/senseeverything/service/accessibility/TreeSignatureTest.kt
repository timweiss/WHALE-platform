package de.mimuc.senseeverything.service.accessibility

import de.mimuc.senseeverything.service.accessibility.model.NodeType
import de.mimuc.senseeverything.service.accessibility.model.ScreenRegion
import de.mimuc.senseeverything.service.accessibility.model.SizeClass
import de.mimuc.senseeverything.service.accessibility.model.SkeletonNode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import kotlin.random.Random

class TreeSignatureTest {
    /** The implementation used before TreeSignature, kept here to prove the output is unchanged. */
    private fun legacySignature(nodes: List<SkeletonNode>): String {
        val structureString = nodes.joinToString("|") { node ->
            "${node.type.name}:${node.depth}:${node.region.name}:${node.sizeClass.name}:${node.clickable}"
        }
        val bytes = MessageDigest.getInstance("SHA-256").digest(structureString.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun randomNode(random: Random, id: Int) = SkeletonNode(
        id = id,
        parentId = if (id == 0) null else random.nextInt(id),
        type = NodeType.values().random(random),
        depth = random.nextInt(30),
        region = ScreenRegion.values().random(random),
        sizeClass = SizeClass.values().random(random),
        relativeX = random.nextFloat(),
        relativeY = random.nextFloat(),
        relativeWidth = random.nextFloat(),
        relativeHeight = random.nextFloat(),
        clickable = random.nextBoolean(),
        scrollable = random.nextBoolean(),
        editable = random.nextBoolean(),
        focusable = random.nextBoolean(),
        hasText = random.nextBoolean(),
        textCategory = null,
        hasImage = random.nextBoolean(),
        role = null
    )

    @Test
    fun matchesLegacyImplementationForRandomTrees() {
        val random = Random(42)
        repeat(200) {
            val nodes = List(random.nextInt(1, 300)) { id -> randomNode(random, id) }
            assertEquals(legacySignature(nodes), TreeSignature.compute(nodes))
        }
    }

    @Test
    fun matchesLegacyImplementationForEmptyAndSingleNode() {
        val random = Random(7)
        assertEquals(legacySignature(emptyList()), TreeSignature.compute(emptyList()))
        val single = listOf(randomNode(random, 0))
        assertEquals(legacySignature(single), TreeSignature.compute(single))
    }
}
