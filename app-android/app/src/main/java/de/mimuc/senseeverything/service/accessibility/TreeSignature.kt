package de.mimuc.senseeverything.service.accessibility

import de.mimuc.senseeverything.service.accessibility.model.SkeletonNode
import java.security.MessageDigest

/**
 * Structural signature of a skeleton, used for deduplication and to link interactions to screens.
 *
 * Hashes exactly the same bytes as the former `joinToString("|")` implementation
 * ("TYPE:depth:REGION:SIZE:clickable" joined by "|"), but feeds them into the digest node by node
 * instead of building one large string first. Output must stay identical so signatures remain
 * comparable across study waves.
 */
object TreeSignature {
    private val HEX = "0123456789abcdef".toCharArray()
    private val SEPARATOR = "|".toByteArray(Charsets.UTF_8)

    fun compute(nodes: List<SkeletonNode>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        nodes.forEachIndexed { index, node ->
            if (index > 0) digest.update(SEPARATOR)
            val part = "${node.type.name}:${node.depth}:${node.region.name}:${node.sizeClass.name}:${node.clickable}"
            digest.update(part.toByteArray(Charsets.UTF_8))
        }
        return toHex(digest.digest())
    }

    private fun toHex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        bytes.forEachIndexed { i, b ->
            val v = b.toInt() and 0xff
            chars[i * 2] = HEX[v ushr 4]
            chars[i * 2 + 1] = HEX[v and 0x0f]
        }
        return String(chars)
    }
}
