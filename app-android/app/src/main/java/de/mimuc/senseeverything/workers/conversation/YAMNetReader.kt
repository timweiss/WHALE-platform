package de.mimuc.senseeverything.workers.conversation

import android.content.Context
import android.util.Log
import com.konovalov.vad.yamnet.VadYamnet
import com.konovalov.vad.yamnet.config.FrameSize
import com.konovalov.vad.yamnet.config.Mode
import com.konovalov.vad.yamnet.config.SampleRate
import java.io.File

class YAMNetReader : VadReader() {
    override val TAG = "YAMNetReader"

    override fun detect(path: String, context: Context): List<AudioSegment> {
        val frames = arrayListOf<AudioSegment>()

        VadYamnet(
            context,
            sampleRate = SampleRate.SAMPLE_RATE_16K,
            frameSize = FrameSize.FRAME_SIZE_243,
            mode = Mode.NORMAL,
            silenceDurationMs = 600,
            speechDurationMs = 150
        ).use { vad ->
            val chunkSize = vad.frameSize.value * 2

            val file = File(path)
            Log.d(TAG, "size of file: " + file.length().toString())

            File(path).inputStream().use { input ->
                val audioHeader = ByteArray(44).apply { input.read(this) }
                val reader = AudioSegmentReader()

                while (input.available() > 0) {
                    val frameChunk = ByteArray(chunkSize).apply { input.read(this) }
                    val sc = vad.classifyAudio(frameChunk)

                    val segment = reader.processFrameChunk(frameChunk, sc)
                    if (segment != null) {
                        frames.add(segment)
                    }
                }

                // end of data
                frames.add(reader.getLastSection())
            }
        }

        return frames
    }
}
