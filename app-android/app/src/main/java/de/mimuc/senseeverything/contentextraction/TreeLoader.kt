package de.mimuc.senseeverything.contentextraction

import org.json.JSONArray
import org.json.JSONObject

/* Port of loader.py's to_tree: the skeleton node list (UITreeConsumer.buildSkeleton) as a tree. */

/** The fields of one skeleton node that the pipeline reads. */
class RawNode(
    val id: Int,
    val parentId: Int?,
    val type: String?,
    val text: String?,
    val contentDescription: String?,
    val relativeX: Double,
    val relativeY: Double,
    val relativeWidth: Double,
    val relativeHeight: Double,
    val scrollable: Boolean
)

private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

/**
 * Reads the node list of a snapshot in the uploaded batch JSON (or a harness export). Bounds are
 * parsed from their decimal text into Double, exactly like the harness gets them from the server,
 * NOT widened from the capture's Float: 0.152f is 0.15199999511 as a Double, and the prompt must
 * come out of the same numbers as in the harness.
 */
fun rawNodesFromJson(nodes: JSONArray): List<RawNode> = (0 until nodes.length()).map { i ->
    val o = nodes.getJSONObject(i)
    RawNode(
        id = o.getInt("id"),
        parentId = if (o.isNull("parentId")) null else o.getInt("parentId"),
        type = o.optStringOrNull("type"),
        text = o.optStringOrNull("text"),
        contentDescription = o.optStringOrNull("contentDescription"),
        relativeX = o.getDouble("relativeX"),
        relativeY = o.getDouble("relativeY"),
        relativeWidth = o.getDouble("relativeWidth"),
        relativeHeight = o.getDouble("relativeHeight"),
        scrollable = o.optBoolean("scrollable", false)
    )
}

/**
 * Ids are pre-order DFS (UITreeConsumer.buildSkeleton), so attaching in id order gives on-screen
 * child order. Nodes whose parent is missing (parentId -1 or null) become roots; several roots get
 * a synthetic full-screen parent with id -1.
 */
fun toTree(raw: List<RawNode>): Node {
    val nodes = raw.associate { r ->
        r.id to Node(
            id = r.id,
            type = r.type ?: "UNKNOWN",
            text = r.text,
            desc = r.contentDescription,
            x = r.relativeX,
            y = r.relativeY,
            w = r.relativeWidth,
            h = r.relativeHeight,
            scrollable = r.scrollable
        )
    }
    val roots = mutableListOf<Node>()
    for (r in raw.sortedBy { it.id }) {
        val parent = r.parentId?.let { nodes[it] }
        (parent?.children ?: roots).add(nodes.getValue(r.id))
    }
    if (roots.size == 1) return roots[0]
    return Node(id = -1, type = "CONTAINER", text = null, desc = null, x = 0.0, y = 0.0, w = 1.0, h = 1.0, children = roots)
}
