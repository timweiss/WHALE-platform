package de.mimuc.senseeverything.contentextraction

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import java.io.File

/** One model call; [rawOutput] null when it failed. Token counts and speeds come from LiteRT-LM. */
class EngineCall(
    val rawOutput: String?,
    val error: String?,
    val wallMs: Long,
    val prefillTokens: Int? = null,
    val decodeTokens: Int? = null,
    val prefillTokensPerSecond: Double? = null,
    val decodeTokensPerSecond: Double? = null,
    val timeToFirstTokenSeconds: Double? = null
)

interface ExtractionEngine : AutoCloseable {
    /** "gpu" or "cpu" */
    val backend: String

    /** One user message, answered under [RESPONSE_SCHEMA_JSON]. Blocking. */
    fun generate(prompt: String): EngineCall

    /** Ends a running [generate] early; from any thread. */
    fun cancel()
}

sealed interface EngineOpening {
    class Opened(val engine: ExtractionEngine, val initMs: Long, val gpuError: String?) : EngineOpening

    class Failed(val errors: List<String>) : EngineOpening
}

/**
 * LiteRT-LM with the harness's generation settings: greedy (temperature 0), no thinking (the harness
 * ran Ollama with think: false), JSON-schema constrained output, 8192 tokens of context and at most
 * 4096 output tokens. A fresh conversation per call, as every harness call is a single user message.
 */
@OptIn(ExperimentalApi::class)
class LiteRtExtractionEngine private constructor(
    private val engine: Engine,
    override val backend: String
) : ExtractionEngine {

    companion object {
        const val RUNTIME = "litert-lm 0.20.0"
        const val MAX_NUM_TOKENS = 8192
        const val MAX_OUTPUT_TOKENS = 4096

        /** GPU first, CPU if the GPU engine can't be created; null cacheDir keeps LiteRT-LM's default. */
        fun open(modelFile: File, cacheDir: File?): EngineOpening {
            // read when an engine is created
            ExperimentalFlags.enableBenchmark = true
            val errors = mutableListOf<String>()
            for ((name, backend) in listOf("gpu" to Backend.GPU(), "cpu" to Backend.CPU())) {
                val start = System.nanoTime()
                var engine: Engine? = null
                try {
                    engine = Engine(
                        EngineConfig(
                            modelPath = modelFile.path,
                            backend = backend,
                            maxNumTokens = MAX_NUM_TOKENS,
                            cacheDir = cacheDir?.path
                        )
                    )
                    engine.initialize()
                    return EngineOpening.Opened(
                        LiteRtExtractionEngine(engine, name),
                        initMs = (System.nanoTime() - start) / 1_000_000,
                        gpuError = errors.firstOrNull()
                    )
                } catch (e: Exception) {
                    runCatching { engine?.close() }
                    errors += "$name: ${e.javaClass.simpleName}: ${e.message}"
                } catch (e: LinkageError) {
                    // UnsatisfiedLinkError: a native library is missing for this backend or device
                    runCatching { engine?.close() }
                    errors += "$name: ${e.javaClass.simpleName}: ${e.message}"
                }
            }
            return EngineOpening.Failed(errors)
        }
    }

    @Volatile
    private var current: Conversation? = null

    override fun generate(prompt: String): EngineCall {
        val start = System.nanoTime()
        fun elapsed() = (System.nanoTime() - start) / 1_000_000
        return try {
            engine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0),
                    maxOutputToken = MAX_OUTPUT_TOKENS,
                    thinkingConfig = ThinkingConfig(enableThinking = false),
                    enableResponseFormat = true
                )
            ).use { conversation ->
                current = conversation
                val message = conversation.sendMessage(
                    prompt,
                    responseFormat = ResponseFormat.json(RESPONSE_SCHEMA_JSON)
                )
                val text = message.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }
                val wallMs = elapsed()
                val bench = runCatching { conversation.getBenchmarkInfo() }.getOrNull()
                EngineCall(
                    rawOutput = text,
                    error = null,
                    wallMs = wallMs,
                    prefillTokens = bench?.lastPrefillTokenCount,
                    decodeTokens = bench?.lastDecodeTokenCount,
                    prefillTokensPerSecond = bench?.lastPrefillTokensPerSecond,
                    decodeTokensPerSecond = bench?.lastDecodeTokensPerSecond,
                    timeToFirstTokenSeconds = bench?.timeToFirstTokenInSecond
                )
            }
        } catch (e: Exception) {
            EngineCall(rawOutput = null, error = "${e.javaClass.simpleName}: ${e.message}", wallMs = elapsed())
        } finally {
            current = null
        }
    }

    override fun cancel() {
        runCatching { current?.cancelProcess() }
    }

    override fun close() = engine.close()
}
