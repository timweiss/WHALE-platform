package de.mimuc.senseeverything.service.accessibility.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight representation of a screen state and optional interaction.
 * Designed to be privacy-preserving and small enough for efficient server transmission.
 */
data class ScreenSnapshot(
    val timestamp: Long,
    val appPackage: String,
    val framework: String,
    val skeleton: TreeSkeleton,
    val interaction: InteractionEvent? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("timestamp", timestamp)
            put("appPackage", appPackage)
            put("framework", framework)
            put("skeleton", skeleton.toJson())
            interaction?.let { put("interaction", it.toJson()) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): ScreenSnapshot {
            return ScreenSnapshot(
                timestamp = json.getLong("timestamp"),
                appPackage = json.getString("appPackage"),
                framework = json.getString("framework"),
                skeleton = TreeSkeleton.fromJson(json.getJSONObject("skeleton")),
                interaction = if (json.has("interaction")) {
                    InteractionEvent.fromJson(json.getJSONObject("interaction"))
                } else null
            )
        }
    }
}

/**
 * Structural skeleton of the UI tree.
 * Contains flattened node list with parent references for efficient serialization.
 */
data class TreeSkeleton(
    var signature: String,
    val nodes: List<SkeletonNode>
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("signature", signature)
            put("nodes", JSONArray().apply {
                nodes.forEach { put(it.toJson()) }
            })
        }
    }

    companion object {
        fun fromJson(json: JSONObject): TreeSkeleton {
            val nodesArray = json.getJSONArray("nodes")
            val nodes = mutableListOf<SkeletonNode>()
            for (i in 0 until nodesArray.length()) {
                nodes.add(SkeletonNode.fromJson(nodesArray.getJSONObject(i)))
            }
            return TreeSkeleton(
                signature = json.getString("signature"),
                nodes = nodes
            )
        }
    }
}

/**
 * Lightweight node representation capturing only structural and layout properties.
 * Privacy-safe: no actual text content, only categorization.
 */
data class SkeletonNode(
    val id: Int,
    val parentId: Int?,
    val type: NodeType,
    val depth: Int,
    val region: ScreenRegion,
    val sizeClass: SizeClass,
    val relativeX: Float,
    val relativeY: Float,
    val relativeWidth: Float,
    val relativeHeight: Float,
    val clickable: Boolean,
    val scrollable: Boolean,
    val editable: Boolean,
    val focusable: Boolean,
    val hasText: Boolean,
    val textCategory: TextCategory?,
    val hasImage: Boolean,
    val role: String?
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("parentId", parentId ?: -1)
            put("type", type.name)
            put("depth", depth)
            put("region", region.name)
            put("sizeClass", sizeClass.name)
            put("relativeX", relativeX)
            put("relativeY", relativeY)
            put("relativeWidth", relativeWidth)
            put("relativeHeight", relativeHeight)
            put("clickable", clickable)
            put("scrollable", scrollable)
            put("editable", editable)
            put("focusable", focusable)
            put("hasText", hasText)
            textCategory?.let { put("textCategory", it.name) }
            put("hasImage", hasImage)
            role?.let { put("role", it) }
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SkeletonNode {
            return SkeletonNode(
                id = json.getInt("id"),
                parentId = json.getInt("parentId").let { if (it == -1) null else it },
                type = NodeType.valueOf(json.getString("type")),
                depth = json.getInt("depth"),
                region = ScreenRegion.valueOf(json.getString("region")),
                sizeClass = SizeClass.valueOf(json.getString("sizeClass")),
                relativeX = json.getDouble("relativeX").toFloat(),
                relativeY = json.getDouble("relativeY").toFloat(),
                relativeWidth = json.getDouble("relativeWidth").toFloat(),
                relativeHeight = json.getDouble("relativeHeight").toFloat(),
                clickable = json.getBoolean("clickable"),
                scrollable = json.getBoolean("scrollable"),
                editable = json.getBoolean("editable"),
                focusable = json.getBoolean("focusable"),
                hasText = json.getBoolean("hasText"),
                textCategory = if (json.has("textCategory")) {
                    TextCategory.valueOf(json.getString("textCategory"))
                } else null,
                hasImage = json.getBoolean("hasImage"),
                role = if (json.has("role")) json.getString("role") else null
            )
        }
    }
}

enum class NodeType {
    CONTAINER,
    TEXT,
    IMAGE,
    BUTTON,
    INPUT,
    LIST,
    SCROLL,
    WEB,
    VIDEO,
    UNKNOWN
}

enum class TextCategory {
    EMPTY,
    SINGLE_WORD,
    SHORT_PHRASE,
    SENTENCE,
    PARAGRAPH,
    LONG_TEXT
}

enum class SizeClass {
    TINY,
    SMALL,
    MEDIUM,
    LARGE,
    FULLSCREEN
}

enum class ScreenRegion {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    MIDDLE_LEFT, CENTER, MIDDLE_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

data class InteractionEvent(
    val type: InteractionType,
    val targetNodeId: Int,
    val tapX: Float,
    val tapY: Float
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("type", type.name)
            put("targetNodeId", targetNodeId)
            put("tapX", tapX)
            put("tapY", tapY)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): InteractionEvent {
            return InteractionEvent(
                type = InteractionType.valueOf(json.getString("type")),
                targetNodeId = json.getInt("targetNodeId"),
                tapX = json.getDouble("tapX").toFloat(),
                tapY = json.getDouble("tapY").toFloat()
            )
        }
    }
}

enum class InteractionType {
    TAP,
    LONG_PRESS,
    SCROLL,
    TEXT_INPUT,
    SWIPE
}