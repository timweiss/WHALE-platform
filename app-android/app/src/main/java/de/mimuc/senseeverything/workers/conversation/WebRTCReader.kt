package de.mimuc.senseeverything.workers.conversation

import android.content.Context
import android.util.Log
import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
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
            speechDurationMs = 50
        ).use { vad ->
            val chunkSize = vad.frameSize.value * 2

            val file = File(path)
            Log.d(TAG, "size of file: " + file.length().toString())

            File(path).inputStream().use { input ->
                val audioHeader = ByteArray(44).apply { input.read(this) }
                var speechData = byteArrayOf()

                var position = 0
                var currentSectionLength = 0
                var isSpeech = false

                while (input.available() > 0) {
                    val frameChunk = ByteArray(chunkSize).apply { input.read(this) }

                    if (vad.isSpeech(frameChunk)) {
                        if (!isSpeech) {
                            frames.add(AudioSegment(position, currentSectionLength, false))
                            // was no speech, new section arrives
                            currentSectionLength = 0
                        }

                        currentSectionLength += frameChunk.size
                        isSpeech = true
                    } else {
                        if (isSpeech) {
                            frames.add(AudioSegment(position, currentSectionLength, true))
                            currentSectionLength = 0
                        }

                        currentSectionLength += frameChunk.size
                        isSpeech = false
                    }

                    position += frameChunk.size
                }

                // end of data
                frames.add(AudioSegment(position, currentSectionLength, isSpeech))
            }
        }

        return frames
    }

}