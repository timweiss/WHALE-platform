package de.mimuc.senseeverything.contentextraction

import de.mimuc.senseeverything.api.model.ContentExtractionConfig
import de.mimuc.senseeverything.api.model.ContentExtractionModel
import de.mimuc.senseeverything.api.model.ContentExtractionPackage
import de.mimuc.senseeverything.db.models.LogData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalTime
import java.util.Base64
import java.util.zip.GZIPOutputStream

class ExtractionRunnerTest {
    @TempDir
    lateinit var dir: File

    private val fixture = JSONObject(javaClass.getResource("/contentextraction/synthetic_feed.json")!!.readText())
    private val feedApp = fixture.getString("app_package")
    private val expectedPrompts = fixture.getJSONArray("calls").let { c -> (0 until c.length()).map { c.getJSONObject(it).getString("prompt") } }

    private val config = ContentExtractionConfig(
        model = ContentExtractionModel("ab".repeat(32), 1),
        packages = listOf(ContentExtractionPackage(feedApp, hint = null))
    )

    private class FakeStore(rows: List<StoredRow>) : ExtractionStore {
        val unhandled = rows.toMutableList()
        val saved = linkedMapOf<Int, List<LogData>>()
        var flaggedAll = 0

        override fun nextUnhandledRow() = unhandled.firstOrNull()
        override fun saveHandled(rowId: Int, readings: List<LogData>) {
            unhandled.removeIf { it.id == rowId }
            saved[rowId] = readings
        }
        override fun flagAllUnhandled(): Int = unhandled.size.also { flaggedAll += it; unhandled.clear() }
    }

    private class FakeEngine(val answer: (Int) -> EngineCall) : ExtractionEngine {
        override val backend = "cpu"
        val prompts = mutableListOf<String>()
        var closed = false
        var cancelled = false
        override fun generate(prompt: String): EngineCall = answer(prompts.size).also { prompts += prompt }
        override fun cancel() { cancelled = true }
        override fun close() { closed = true }
    }

    private val post = """{"items": [{"content_type": "post", "username": "anna.k", "caption": null}, {"content_type": "other", "username": null}]}"""
    private fun ok(raw: String = post) = EngineCall(raw, null, wallMs = 5, prefillTokens = 900, decodeTokens = 40)

    /** A UITree row as UITreeSensor stores it: gzip + Base64 of the batch JSON. */
    private fun row(id: Int, vararg snapshots: Pair<String, String>): StoredRow {
        val batch = JSONObject().put("timestamp", 1).put("count", snapshots.size).put("snapshots", JSONArray(
            snapshots.map { (snapshotId, app) ->
                JSONObject().put("snapshotId", snapshotId).put("timestamp", 1_000).put("appPackage", app)
                    .put("framework", "VIEW").put("skeleton", JSONObject().put("signature", "s").put("nodes", fixture.getJSONArray("nodes")))
            }
        ))
        val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(batch.toString().toByteArray()) } }
        return StoredRow(id, Base64.getEncoder().encodeToString(gz.toByteArray()))
    }

    private lateinit var state: RunState
    private var engineOpened = 0

    @BeforeEach
    fun setUp() {
        state = RunState(File(dir, "run-state.json"))
        engineOpened = 0
    }

    private fun runner(store: ExtractionStore, opening: () -> EngineOpening) =
        ExtractionRunner(config, store, state, { engineOpened++; opening() }, runId = "run-1", now = { 42 })

    private fun readingsOf(store: FakeStore, rowId: Int) =
        store.saved.getValue(rowId).map { Json.decodeFromString<ContentExtractionReading>(it.data!!) }

    @Test
    fun extractsTheConfiguredAppsSnapshotsAndFlagsTheRow() {
        val store = FakeStore(listOf(row(1, "feed-1" to feedApp, "other-1" to "com.other")))
        val engine = FakeEngine { ok() }

        val summary = runner(store) { EngineOpening.Opened(engine, initMs = 7, gpuError = "gpu: no OpenCL") }.run { true }

        assertEquals(OUTCOME_DONE, summary.outcome)
        assertEquals(expectedPrompts, engine.prompts) // the harness's prompts, one per segment
        val reading = readingsOf(store, 1).single()
        assertEquals("feed-1", reading.snapshotId)
        assertEquals("extracted", reading.outcome)
        assertEquals("run-1", reading.runId)
        assertEquals(expectedPrompts, reading.calls.map { it.prompt })
        assertEquals(900, reading.calls.first().prefillTokens)
        // postprocessed: the filler "other" item without content is dropped; items carry their segment
        assertEquals(3, reading.items.size)
        assertEquals(3, reading.droppedItems)
        assertEquals(listOf(0, 1, 2), reading.items.map { (it["_segment"] as JsonPrimitive).content.toInt() })
        assertEquals(42, store.saved.getValue(1).single().timestamp)
        assertEquals(SENSOR_CONTENT_EXTRACTION, store.saved.getValue(1).single().sensorName)
        assertEquals(2, summary.snapshotsSeen)
        assertEquals(1, summary.snapshotsOfConfiguredApps)
        assertEquals("gpu: no OpenCL", summary.gpuError)
        assertTrue(engine.closed)
        assertNull(state.read())
    }

    @Test
    fun recordsEngineErrorsAndUnusableAnswersAndStillHandlesTheRow() {
        val store = FakeStore(listOf(row(1, "feed-1" to feedApp)))
        val engine = FakeEngine { i ->
            when (i) {
                0 -> EngineCall(null, "LiteRtLmJniException: boom", wallMs = 1)
                1 -> ok("{\"items\": [") // cut off
                else -> ok()
            }
        }

        val summary = runner(store) { EngineOpening.Opened(engine, 1, null) }.run { true }

        val calls = readingsOf(store, 1).single().calls
        assertEquals("LiteRtLmJniException: boom", calls[0].error)
        assertTrue(calls[1].error!!.startsWith("unparseable answer"))
        assertEquals("{\"items\": [", calls[1].rawOutput)
        assertNull(calls[2].error)
        assertEquals(2, summary.callErrors)
        assertEquals(1, summary.rowsHandled)
    }

    @Test
    fun anEngineThatCannotStartLetsAllRowsUploadWithoutResults() {
        val store = FakeStore(listOf(row(1, "feed-1" to feedApp), row(2, "feed-2" to feedApp)))

        val summary = runner(store) { EngineOpening.Failed(listOf("gpu: x", "cpu: y")) }.run { true }

        assertEquals(OUTCOME_ENGINE_FAILED, summary.outcome)
        assertEquals(2, summary.rowsFlaggedWithoutExtraction)
        assertEquals(listOf("gpu: x", "cpu: y"), summary.engineErrors)
        assertTrue(store.saved.isEmpty())
        assertNull(state.read())
    }

    @Test
    fun anEngineStartThatKilledTheProcessTwiceIsNotTriedAgain() {
        state.write(RunState.Marker(ExtractionRunner.PHASE_ENGINE, attempts = 2))
        val store = FakeStore(listOf(row(1, "feed-1" to feedApp)))

        val summary = runner(store) { error("must not be opened") }.run { true }

        assertEquals(0, engineOpened)
        assertEquals(OUTCOME_ENGINE_FAILED, summary.outcome)
        assertEquals(1, summary.rowsFlaggedWithoutExtraction)
    }

    @Test
    fun aRowThatKilledTheProcessOnceIsTriedAgain() {
        state.write(RunState.Marker(ExtractionRunner.PHASE_ROW, rowId = 1, attempts = 1))
        val store = FakeStore(listOf(row(1, "feed-1" to feedApp)))
        val engine = FakeEngine { ok() }

        runner(store) { EngineOpening.Opened(engine, 1, null) }.run { true }

        assertEquals("extracted", readingsOf(store, 1).single().outcome)
    }

    @Test
    fun aRowThatKilledTheProcessTwiceIsSkippedAsCrashed() {
        state.write(RunState.Marker(ExtractionRunner.PHASE_ROW, rowId = 1, attempts = 2))
        val store = FakeStore(listOf(row(1, "feed-1" to feedApp, "other-1" to "com.other"), row(2, "feed-2" to feedApp)))
        val engine = FakeEngine { ok() }

        val summary = runner(store) { EngineOpening.Opened(engine, 1, null) }.run { true }

        assertEquals(listOf("crashed"), readingsOf(store, 1).map { it.outcome })
        assertEquals(listOf("extracted"), readingsOf(store, 2).map { it.outcome })
        assertEquals(expectedPrompts, engine.prompts) // only row 2 was sent to the model
        assertEquals(1, summary.rowsCrashed)
    }

    @Test
    fun aStoppedRunLeavesTheRowForTheNextOne() {
        val store = FakeStore(listOf(row(1, "feed-1" to feedApp)))
        var stop = false
        val engine = FakeEngine { ok().also { stop = true } }

        val summary = runner(store) { EngineOpening.Opened(engine, 1, null) }.run { !stop }

        assertEquals(OUTCOME_STOPPED, summary.outcome)
        assertTrue(store.saved.isEmpty())
        assertEquals(1, store.unhandled.size)
        assertNull(state.read()) // a stop is not a crash
        assertTrue(engine.closed)
    }

    @Test
    fun withoutAModelAllRowsUploadWithoutResults() {
        val store = FakeStore(listOf(row(1, "feed-1" to feedApp), row(2, "feed-2" to feedApp)))

        val summary = runner(store) { error("must not be opened") }.flagWithoutModel("the model download failed 3 times")

        assertEquals(OUTCOME_MODEL_UNAVAILABLE, summary.outcome)
        assertEquals(2, summary.rowsFlaggedWithoutExtraction)
        assertEquals(listOf("the model download failed 3 times"), summary.engineErrors)
        assertTrue(leavesNoBacklog(summary.outcome))
        assertEquals(OUTCOME_NOTHING_TO_DO, runner(FakeStore(emptyList())) { error("") }.flagWithoutModel("x").outcome)
    }

    @Test
    fun onlyAStoppedRunLeavesABacklog() {
        assertFalse(leavesNoBacklog(OUTCOME_STOPPED))
        listOf(OUTCOME_DONE, OUTCOME_NOTHING_TO_DO, OUTCOME_ENGINE_FAILED, OUTCOME_MODEL_UNAVAILABLE)
            .forEach { assertTrue(leavesNoBacklog(it), it) }
    }

    @Test
    fun scheduledRunsOnlyAtNight() {
        assertTrue(ExtractionWindow.contains(LocalTime.MIDNIGHT))
        assertTrue(ExtractionWindow.contains(LocalTime.of(5, 59)))
        assertFalse(ExtractionWindow.contains(LocalTime.of(6, 0)))
        assertFalse(ExtractionWindow.contains(LocalTime.of(14, 0)))
        assertFalse(ExtractionWindow.contains(LocalTime.of(23, 59)))
    }

    @Test
    fun nothingToDoDoesNotStartTheEngine() {
        val summary = runner(FakeStore(emptyList())) { error("must not be opened") }.run { true }

        assertEquals(OUTCOME_NOTHING_TO_DO, summary.outcome)
        assertEquals(0, engineOpened)
        assertFalse(File(dir, "run-state.json").exists())
    }
}
