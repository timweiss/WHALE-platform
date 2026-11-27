package de.mimuc.senseeverything.contentextraction

import java.security.MessageDigest

/*
 * Port of prompt.py for condition D with rules v1 (frozen 2026-11-18). The strings are verbatim
 * copies: any change makes device results incomparable with the harness runs. The per-app hint
 * block is not built here; it comes pre-rendered by the harness via the study config.
 */

const val RULES_VERSION = "v1"

/** RULES_V1 formatted with the app package (the template's {{ }} are already single braces). */
fun rulesV1(app: String): String = """You extract social media content items from one Android screen of the app $app.

Output: JSON {"items": [...]} with one object per content item, top to bottom. If there is no content, output {"items": []}.

Rules:
1. One post is one item. Its header, media, buttons, caption and date belong to the same item.
2. Navigation bars, tabs, toolbars, search fields and your own story button are not items.
3. username is the account handle: no spaces, usually lowercase, may contain "." and "_". A name with spaces or capital letters is display_name.
4. caption is the text the author wrote. Remove the leading username. If it ends with "…", "... mehr" or "more", remove that and set caption_truncated true.
5. A description of an image or video (for example "Photo 1 of 3 by …") is never the caption.
6. Counts are integers. Expand abbreviations: "12,5 Tsd." = 12500, "1,2 Mio." = 1200000, "1.032" = 1032. A number right after an icon (like, comment, share, save) is that icon's count. Prefer an exact number from a description over an abbreviated one.
7. is_advertisement is true if the item shows "Anzeige", "Gesponsert", "Sponsored", "Bezahlte Werbepartnerschaft" or a call-to-action button such as "Mehr dazu", "Registrieren", "Jetzt kaufen".
8. post_date is copied as written, e.g. "Vor 2 Tagen" or "16. September".
9. incomplete is true if the item is cut off at the screen edge.
10. content_type: post = photo or carousel; video = video in a feed; reel = full-screen short video; story; story_tray_profile = an account avatar in the story row; profile = a profile header; suggested_profile = a suggested account card; comment; chat_message; other.
11. Use null for anything not shown. Put other useful facts (repost count, location, audio, hashtags) into additional_metadata.
"""

const val FORMAT_LINES = """Input format:
- Each numbered line is one element on screen. Plain text is visible text. "desc:" is an accessibility description of the element. "media:" is the description of a large image or video.
- " · " separates elements shown side by side on the same line.
- SCREEN CHROME lists navigation and toolbars. Never extract items from it.
- CONTENT holds the content. "--- segment" lines separate parts that probably belong to different items.
- [clipped top] / [clipped bottom] mark content cut off by the screen edge.
"""

const val FORMAT_SEGMENT_V1 = """The CONTENT below is ONE segment of the screen. It usually holds one item or a part of one; output an empty list if it holds no content.
"""

/** RESPONSE_SCHEMA as the harness sends it (json.dumps), for constrained decoding. */
const val RESPONSE_SCHEMA_JSON = """{"type": "object", "properties": {"items": {"type": "array", "items": {"type": "object", "properties": {"content_type": {"type": "string", "enum": ["post", "video", "reel", "story", "story_tray_profile", "profile", "suggested_profile", "comment", "chat_message", "other"]}, "username": {"type": ["string", "null"]}, "display_name": {"type": ["string", "null"]}, "caption": {"type": ["string", "null"]}, "caption_truncated": {"type": "boolean"}, "is_advertisement": {"type": "boolean"}, "like_count": {"type": ["integer", "null"]}, "comment_count": {"type": ["integer", "null"]}, "share_count": {"type": ["integer", "null"]}, "bookmark_count": {"type": ["integer", "null"]}, "post_date": {"type": ["string", "null"]}, "incomplete": {"type": "boolean"}, "additional_metadata": {"type": "object"}}, "required": ["content_type", "username", "display_name", "caption", "caption_truncated", "is_advertisement", "like_count", "comment_count", "share_count", "bookmark_count", "post_date", "incomplete", "additional_metadata"]}}}, "required": ["items"]}"""

/** build_prompt("D", app, body, hints_text, "v1"): the single user message for one call. */
fun buildPrompt(app: String, body: String, hintText: String?): String {
    val parts = mutableListOf(rulesV1(app), FORMAT_LINES, FORMAT_SEGMENT_V1)
    if (!hintText.isNullOrEmpty()) parts.add(hintText)
    parts.add("=== Screen ===\n$body")
    return parts.joinToString("\n")
}

/**
 * The harness's prompt template hash (run.py): sha256 of the prompt without its screen part, first
 * 12 hex digits. Equal hashes mean the same rules, format text and hint block; the frozen
 * configuration has 77ef662cfb1d (Instagram) and e0c612c224ad (TikTok).
 */
fun templateHash(prompt: String, body: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(prompt.replace(body, "").toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }.take(12)
}

/** One model call: a segment of the screen (with chrome) inside the full prompt. */
class PlannedCall(
    val segmentIndex: Int,
    /** the serialized screen part, as the harness's `body` */
    val body: String,
    val prompt: String
) {
    val templateHash: String get() = templateHash(prompt, body)
}

class ScreenPlan(val screen: Screen, val calls: List<PlannedCall>)

/**
 * run.py's calls_for("D") + build_prompt: one call per segment, chrome included in each. A screen
 * without content makes no call, the answer is known to be no items.
 */
fun planCalls(root: Node, app: String, hintText: String?, p: Params = Params()): ScreenPlan {
    val screen = buildScreen(root, p)
    val calls = screen.segments.indices.map { i ->
        val body = serialize(screen, p, setOf(i))
        PlannedCall(i, body, buildPrompt(app, body, hintText))
    }
    return ScreenPlan(screen, calls)
}
