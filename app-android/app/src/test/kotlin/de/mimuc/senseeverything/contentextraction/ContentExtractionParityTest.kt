package de.mimuc.senseeverything.contentextraction

import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.io.File

/**
 * The Kotlin port must produce byte-identical prompts to the harness (D / rows / v1), otherwise
 * device results can't be compared with the harness runs. Expected outputs come from
 * app-android/scripts/export_content_extraction_golden.py, which runs the harness itself.
 *
 * - synthetic_feed.json (committed): a hand-written feed screen, always checked.
 * - The 29 development screens: only when WHALE_CE_GOLDEN_DIR points to an export. They hold
 *   screen text from the staging test export, so they stay outside the repository.
 */
class ContentExtractionParityTest {

    private fun checkScreen(record: JSONObject, hintText: String?) {
        val id = record.getString("snapshot_id")
        val root = toTree(rawNodesFromJson(record.getJSONArray("nodes")))
        val plan = planCalls(root, record.getString("app_package"), hintText)

        val expectedScreen = record.getJSONObject("screen")
        val screen = plan.screen
        assertEquals(expectedScreen.getString("mode"), screen.mode.harnessName, "$id mode")
        assertEquals(expectedScreen.getInt("main_id"), screen.mainId, "$id main container")
        assertEquals(expectedScreen.getBoolean("main_fallback"), screen.mainFallback, "$id main fallback")
        assertEquals(
            if (expectedScreen.isNull("anchor_index")) null else expectedScreen.getInt("anchor_index"),
            screen.anchorIndex, "$id anchor row"
        )
        val signatures = expectedScreen.getJSONArray("row_signatures")
        assertEquals((0 until signatures.length()).map { signatures.getString(it) }, screen.rowSignatures, "$id row signatures")
        assertEquals(expectedScreen.getInt("segments"), screen.segments.size, "$id segments")
        assertEquals(record.getString("serialized"), serialize(screen, Params()), "$id serialized screen")

        val calls = record.getJSONArray("calls")
        assertEquals(calls.length(), plan.calls.size, "$id number of calls")
        plan.calls.forEachIndexed { i, call ->
            val expected = calls.getJSONObject(i)
            assertEquals(expected.getInt("segment"), call.segmentIndex, "$id call $i segment")
            assertEquals(expected.getString("body"), call.body, "$id call $i body")
            assertEquals(expected.getString("prompt"), call.prompt, "$id call $i prompt")
            assertEquals(expected.getString("template_hash"), call.templateHash, "$id call $i template hash")
        }
    }

    @Test
    fun syntheticFeed() {
        val text = javaClass.getResource("/contentextraction/synthetic_feed.json")!!.readText()
        checkScreen(JSONObject(text), hintText = null)
    }

    private fun goldenDir(): File {
        val dir = System.getenv("WHALE_CE_GOLDEN_DIR")?.let { File(it) }
        assumeTrue(dir != null && dir.isDirectory, "WHALE_CE_GOLDEN_DIR not set, development screens skipped")
        return dir!!
    }

    @TestFactory
    fun developmentScreens(): List<DynamicTest> {
        val dir = goldenDir()
        val hints = File(dir, "hints").listFiles().orEmpty().associate { it.nameWithoutExtension to it.readText() }
        return File(dir, "screens").listFiles().orEmpty().sorted().map { f ->
            DynamicTest.dynamicTest(f.nameWithoutExtension) {
                val record = JSONObject(f.readText())
                checkScreen(record, hints[record.getString("app_package")])
            }
        }
    }

    @Test
    fun responseSchema() {
        assertEquals(File(goldenDir(), "schema.json").readText(), RESPONSE_SCHEMA_JSON)
    }
}
