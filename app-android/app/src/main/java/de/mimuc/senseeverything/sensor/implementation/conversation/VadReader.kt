package de.mimuc.senseeverything.sensor.implementation.conversation

import android.util.Log
import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import java.io.File

class VadReader {
    val TAG = "VadReader"

    fun detect(path: String): List<AudioSegment> {
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

    fun calculateSpeechPercentage(segments: List<AudioSegment>): Double {
        val totalLength = segments
            .fold(0) { acc, segment -> acc + segment.length }
        val speechLength = segments
            .filter { segment -> segment.hasSpeech }
            .fold(0) { acc, segment -> acc + segment.length }

        Log.d(TAG, "total $totalLength and speech $speechLength")

        if (totalLength == 0) return 0.0

        if (speechLength == 0) return 0.0

        return speechLength.toDouble() / totalLength.toDouble()
    }

    fun calculateLength(segments: List<AudioSegment>, sampleRate: Int, depth: Int): Double {
        val totalBytes = segments
            .fold(0) { acc, segment -> acc + segment.length }

        val byterate = depth * sampleRate / 8

        return totalBytes.toDouble() / byterate.toDouble()
    }

    data class AudioSegment(val position: Int, val length: Int, val hasSpeech: Boolean)
}