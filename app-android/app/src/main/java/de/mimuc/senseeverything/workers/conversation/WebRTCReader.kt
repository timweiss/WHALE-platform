package de.mimuc.senseeverything.workers.conversation

import android.content.Context
import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import com.konovalov.vad.yamnet.SoundCategory
import de.mimuc.senseeverything.logging.WHALELog
import java.io.File

class WebRTCReader : VadReader() {
    override val TAG = "WebRTCReader"

    override fun detect(path: String, context: Context): List<AudioSegment> {
        val frames = arrayListOf<AudioSegment>()

        VadWebRTC(
            sampleRate = SampleRate.SAMPLE_RATE_16K,
            frameSize = FrameSize.FRAME_SIZE_320,
            mode = Mode.VERY_AGGRESSIVE,
            silenceDurationMs = 600,
            speechDurationMs = 150
        ).use { vad ->
            val chunkSize = vad.frameSize.value * 2

            val file = File(path)
            WHALELog.i(TAG, "size of file: " + file.length().toString())

            File(path).inputStream().use { input ->
                val audioHeader = ByteArray(44).apply { input.read(this) }
                val reader = AudioSegmentReader()

                while (input.available() > 0) {
                    val frameChunk = ByteArray(chunkSize).apply { input.read(this) }
                    val label = if (vad.isSpeech(frameChunk)) {
                        "Speech"
                    } else {
                        "Silence"
                    }
                    val sc = SoundCategory(label)

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