package de.mimuc.senseeverything.contentextraction

import de.mimuc.senseeverything.api.model.ContentExtractionConfig
import de.mimuc.senseeverything.db.models.LogData
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.LocalTime

/** A held-back UI tree row. */
class StoredRow(val id: Int, val data: String)

/** What the runner needs from the database; Room in the app, a fake in tests. */
interface ExtractionStore {
    /** oldest UITree row without [FLAG_EXTRACTION_HANDLED], or null */
    fun nextUnhandledRow(): StoredRow?

    /** Adds the readings and flags the row, in one transaction. */
    fun saveHandled(rowId: Int, readings: List<LogData>)

    /** Flags every unhandled UITree row; returns how many. */
    fun flagAllUnhandled(): Int
}

/**
 * What a run is doing, on disk. A native crash or OOM kill skips every `finally`, so a marker left
 * behind means the process died in that phase; the next run counts it as an attempt.
 */
class RunState(private val file: File) {
    @Serializable
    data class Marker(val phase: String, val rowId: Int? = null, val attempts: Int)

    fun read(): Marker? = runCatching { Json.decodeFromString<Marker>(file.readText()) }.getOrNull()

    fun write(marker: Marker) {
        val tmp = File(file.path + ".tmp")
        tmp.writeText(Json.encodeToString(marker))
        tmp.renameTo(file)
    }

    fun clear() {
        file.delete()
    }
}

class RunSummary {
    var outcome = OUTCOME_NOTHING_TO_DO
    var backend: String? = null
    var gpuError: String? = null
    var engineInitMs: Long? = null
    val engineErrors = mutableListOf<String>()
    var rowsHandled = 0
    var rowsFlaggedWithoutExtraction = 0
    var rowsCrashed = 0
    var rowsUndecodable = 0
    var snapshotsSeen = 0
    var snapshotsOfConfiguredApps = 0
    var snapshotsExtracted = 0
    var calls = 0
    var callErrors = 0
}

const val OUTCOME_NOTHING_TO_DO = "nothing_to_do"
const val OUTCOME_DONE = "done"
const val OUTCOME_STOPPED = "stopped"
const val OUTCOME_ENGINE_FAILED = "engine_failed"
const val OUTCOME_MODEL_UNAVAILABLE = "model_unavailable"

/** After these outcomes no unhandled row is left (until new UI trees arrive). */
fun leavesNoBacklog(outcome: String) = outcome != OUTCOME_STOPPED

/**
 * When scheduled runs may extract: at night (local time), so phones in use are never slowed down;
 * Android's idle and charging constraints come on top. Manual runs ignore it.
 */
object ExtractionWindow {
    val START: LocalTime = LocalTime.MIDNIGHT
    val END: LocalTime = LocalTime.of(6, 0)

    fun contains(time: LocalTime): Boolean = !time.isBefore(START) && time.isBefore(END)
}

/**
 * Extracts held-back UI tree rows, oldest first, until none is left or [run]'s shouldContinue turns
 * false. A row is flagged with its readings only after all its snapshots are done, so a stopped run
 * leaves it for the next one. See docs/content-extraction.md.
 */
class ExtractionRunner(
    private val config: ContentExtractionConfig,
    private val store: ExtractionStore,
    private val state: RunState,
    private val openEngine: () -> EngineOpening,
    private val runId: String,
    private val now: () -> Long = System::currentTimeMillis
) {
    companion object {
        /** a phase (engine start or a row) the process died in this often is not tried again */
        const val MAX_ATTEMPTS = 2
        const val PHASE_ENGINE = "engine_start"
        const val PHASE_ROW = "row"
        private const val WATCH_INTERVAL_MS = 250L
    }

    private val hints = config.packages.associate { it.packageName to it.hint }

    fun run(shouldContinue: () -> Boolean): RunSummary {
        val summary = RunSummary()
        if (store.nextUnhandledRow() == null) return summary
        val previous = state.read()

        val engineAttempt = if (previous?.phase == PHASE_ENGINE) previous.attempts + 1 else 1
        if (engineAttempt > MAX_ATTEMPTS) {
            summary.engineErrors += "the process died while starting the engine $MAX_ATTEMPTS times"
            return engineFailed(summary)
        }
        state.write(RunState.Marker(PHASE_ENGINE, attempts = engineAttempt))
        val engine = when (val opening = openEngine()) {
            is EngineOpening.Failed -> {
                summary.engineErrors += opening.errors
                return engineFailed(summary)
            }
            is EngineOpening.Opened -> {
                summary.backend = opening.engine.backend
                summary.gpuError = opening.gpuError
                summary.engineInitMs = opening.initMs
                opening.engine
            }
        }

        // ends a running call when the work is stopped, so the run can finish before Android kills it
        val watcher = Thread {
            try {
                while (shouldContinue()) Thread.sleep(WATCH_INTERVAL_MS)
                engine.cancel()
            } catch (_: InterruptedException) {
            }
        }.apply { isDaemon = true; start() }

        try {
            var crashedRow = previous?.takeIf { it.phase == PHASE_ROW }
            while (true) {
                if (!shouldContinue()) {
                    summary.outcome = OUTCOME_STOPPED
                    break
                }
                val row = store.nextUnhandledRow()
                if (row == null) {
                    summary.outcome = OUTCOME_DONE
                    break
                }
                val attempt = if (crashedRow?.rowId == row.id) crashedRow.attempts + 1 else 1
                crashedRow = null
                if (!handleRow(row, attempt, engine, summary, shouldContinue)) {
                    summary.outcome = OUTCOME_STOPPED
                    break
                }
            }
        } finally {
            watcher.interrupt()
            engine.close()
            state.clear()
        }
        return summary
    }

    /**
     * The model can't be had (its download failed too often): every waiting row uploads without
     * results, like when the engine can't start.
     */
    fun flagWithoutModel(reason: String): RunSummary {
        val summary = RunSummary()
        if (store.nextUnhandledRow() == null) return summary
        summary.outcome = OUTCOME_MODEL_UNAVAILABLE
        summary.engineErrors += reason
        summary.rowsFlaggedWithoutExtraction = store.flagAllUnhandled()
        return summary
    }

    private fun engineFailed(summary: RunSummary): RunSummary {
        summary.outcome = OUTCOME_ENGINE_FAILED
        summary.rowsFlaggedWithoutExtraction = store.flagAllUnhandled()
        state.clear()
        return summary
    }

    /** false when the run was stopped before the row was done; the row then stays unhandled */
    private fun handleRow(
        row: StoredRow,
        attempt: Int,
        engine: ExtractionEngine,
        summary: RunSummary,
        shouldContinue: () -> Boolean
    ): Boolean {
        val snapshots = try {
            decodeUiTreeBatch(row.data)
        } catch (e: Exception) {
            summary.rowsUndecodable++
            store.saveHandled(row.id, emptyList())
            return true
        }
        summary.snapshotsSeen += snapshots.size
        val selected = snapshots.filter { it.appPackage in hints }
        summary.snapshotsOfConfiguredApps += selected.size
        val sampled = selected.filter { isSampled(it.snapshotId, config.sampleRate) }

        if (attempt > MAX_ATTEMPTS) {
            summary.rowsCrashed++
            store.saveHandled(row.id, sampled.map { crashedReading(it) })
            return true
        }
        state.write(RunState.Marker(PHASE_ROW, row.id, attempt))

        val readings = mutableListOf<LogData>()
        for (snapshot in sampled) {
            if (!shouldContinue()) return false
            readings += extract(snapshot, engine, summary)
            summary.snapshotsExtracted++
        }
        if (!shouldContinue()) return false
        store.saveHandled(row.id, readings)
        summary.rowsHandled++
        return true
    }

    private fun extract(snapshot: BatchSnapshot, engine: ExtractionEngine, summary: RunSummary): LogData {
        val start = System.nanoTime()
        val plan = planCalls(toTree(rawNodesFromJson(snapshot.nodes)), snapshot.appPackage, hints[snapshot.appPackage])
        val items = mutableListOf<JsonObject>()
        val calls = plan.calls.map { call ->
            val result = engine.generate(call.prompt)
            summary.calls++
            val (parsed, parseError) = parseItems(result.rawOutput)
            val error = result.error ?: parseError
            if (error != null) summary.callErrors++
            parsed.forEach { items += JsonObject(it + ("_segment" to JsonPrimitive(call.segmentIndex))) }
            ExtractionCallRecord(
                segment = call.segmentIndex,
                templateHash = call.templateHash,
                prompt = call.prompt,
                rawOutput = result.rawOutput,
                error = error,
                wallMs = result.wallMs,
                prefillTokens = result.prefillTokens,
                decodeTokens = result.decodeTokens,
                prefillTokensPerSecond = result.prefillTokensPerSecond,
                decodeTokensPerSecond = result.decodeTokensPerSecond,
                timeToFirstTokenSeconds = result.timeToFirstTokenSeconds
            )
        }
        val kept = postprocess(items)
        return reading(
            ContentExtractionReading(
                runId = runId,
                snapshotId = snapshot.snapshotId,
                snapshotTimestamp = snapshot.timestamp,
                appPackage = snapshot.appPackage,
                outcome = "extracted",
                modelSha256 = config.model.sha256,
                runtime = LiteRtExtractionEngine.RUNTIME,
                backend = engine.backend,
                rulesVersion = RULES_VERSION,
                segmentationMode = plan.screen.mode.harnessName,
                calls = calls,
                items = kept,
                droppedItems = items.size - kept.size,
                wallMs = (System.nanoTime() - start) / 1_000_000
            )
        )
    }

    private fun crashedReading(snapshot: BatchSnapshot) = reading(
        ContentExtractionReading(
            runId = runId,
            snapshotId = snapshot.snapshotId,
            snapshotTimestamp = snapshot.timestamp,
            appPackage = snapshot.appPackage,
            outcome = "crashed",
            modelSha256 = config.model.sha256,
            runtime = LiteRtExtractionEngine.RUNTIME,
            backend = null,
            rulesVersion = RULES_VERSION,
            segmentationMode = null,
            calls = emptyList(),
            items = emptyList(),
            droppedItems = 0,
            wallMs = 0
        )
    )

    private fun reading(r: ContentExtractionReading) =
        LogData(now(), SENSOR_CONTENT_EXTRACTION, Json.encodeToString(r))
}

/** The items of a raw model answer `{"items": [...]}`, or an error describing why there are none. */
internal fun parseItems(raw: String?): Pair<List<JsonObject>, String?> {
    if (raw == null) return emptyList<JsonObject>() to null
    return try {
        val items = Json.parseToJsonElement(raw).jsonObject["items"] as? JsonArray
            ?: return emptyList<JsonObject>() to "no items array in the answer"
        items.filterIsInstance<JsonObject>() to null
    } catch (e: Exception) {
        emptyList<JsonObject>() to "unparseable answer: ${e.javaClass.simpleName}: ${e.message}"
    }
}
