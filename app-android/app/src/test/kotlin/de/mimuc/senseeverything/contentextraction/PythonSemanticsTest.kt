package de.mimuc.senseeverything.contentextraction

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Where Kotlin/Java defaults differ from the harness's Python. Every expected value was produced
 * by the harness functions themselves (CPython 3.14), not derived by hand.
 */
class PythonSemanticsTest {
    private val p = Params()

    @Test
    fun cleanUsesPythonWhitespace() {
        // NBSP, figure space (U+2007) and the information separator U+001C are whitespace in Python
        assertEquals("a b c", clean("  a  b c\u001c\n"))
        // the zero-width space is not
        assertEquals("​", clean("​"))
        assertNull(clean(" \t "))
        assertNull(clean(null))
    }

    @Test
    fun dedupeLengthCountsCodePoints() {
        // two emoji: 2 code points (below minDedupeLen 3), but 4 UTF-16 units
        assertFalse(contained("😀😀", listOf("x 😀😀 y"), p))
        assertFalse(contained("ab", listOf("ab"), p))
    }

    @Test
    fun dedupeWordBoundariesFollowPythonUnicodeWordChars() {
        // U+1D400 MATHEMATICAL BOLD CAPITAL A is a letter (outside the BMP): no boundary
        assertFalse(contained("abc", listOf("𝐀abc"), p))
        // an emoji is not a word character: boundary
        assertTrue(contained("abc", listOf("😀abc"), p))
        // superscript two is a number (No) in Python's \w
        assertFalse(contained("Mehr", listOf("Mehr²"), p))
        assertTrue(contained("Mehr", listOf("Mehr."), p))
        // a later occurrence with boundaries still counts
        assertTrue(contained("abc", listOf("xabc abc"), p))
        assertFalse(contained("abc", listOf("xabcx"), p))
    }

    @Test
    fun round2MatchesPythonRound() {
        // exact binary ties round half-even; others by their exact binary value
        assertEquals(listOf(0.12, 0.14, 2.67, 0.0, 0.01), listOf(0.125, 0.135, 2.675, -0.001, 0.005).map(::round2))
        // Python's -0.0 equals 0.0 in the reading order
        assertEquals(0, pyCompare(-0.0, 0.0))
    }

    @Test
    fun postprocessDropsItemsWithoutContent() {
        fun item(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject
        val filler = item("""{"content_type": "other", "username": null, "caption": null, "additional_metadata": {}}""")
        val zeroLikes = item("""{"content_type": "post", "username": null, "like_count": 0}""")
        val emptyCaption = item("""{"content_type": "post", "caption": ""}""")
        assertEquals(listOf(zeroLikes), postprocess(listOf(filler, zeroLikes, emptyCaption)))
    }
}
