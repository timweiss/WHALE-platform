package de.mimuc.senseeverything.contentextraction

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Port of postprocess.py: code-side clean-up of the model's items. The raw model output is kept
 * untouched in the transcript; this runs on top of it.
 */

/** An item needs at least one of these to say anything about the content. */
val CONTENT_FIELDS = listOf(
    "username", "display_name", "caption", "like_count", "comment_count",
    "share_count", "bookmark_count", "post_date"
)

/** `item.get(k) not in (None, "")` for any content field: 0 and false count as content. */
fun hasContent(item: JsonObject): Boolean = CONTENT_FIELDS.any { k ->
    when (val v = item[k]) {
        null, JsonNull -> false
        is JsonPrimitive -> !(v.isString && v.content.isEmpty())
        else -> true
    }
}

/** Drop filler items (e.g. content_type "other" with every field null). */
fun postprocess(items: List<JsonObject>): List<JsonObject> = items.filter { hasContent(it) }
