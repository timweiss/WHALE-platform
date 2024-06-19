package de.mimuc.senseeverything.sensor.implementation.conversation

import android.util.Log
import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate
import java.io.File

class VadReader {
    fun detect(path: String): String {
        var output  = ""

        VadWebRTC(
            sampleRate = SampleRate.SAMPLE_RATE_16K,
            frameSize = FrameSize.FRAME_SIZE_320,
            mode = Mode.VERY_AGGRESSIVE,
            silenceDurationMs = 600,
            speechDurationMs = 50
        ).use { vad ->
            val chunkSize = vad.frameSize.value * 2

            val file = File(path)
            Log.d("VadReader", "size of file: " + file.length().toString())

            File(path).inputStream().use { input ->
                val audioHeader = ByteArray(44).apply { input.read(this) }
                var speechData = byteArrayOf()

                while (input.available() > 0) {
                    val frameChunk = ByteArray(chunkSize).apply { input.read(this) }
                    Log.d("VadReader", "reading chunk " + frameChunk.size.toString())

                    if (vad.isSpeech(frameChunk)) {
                        speechData += frameChunk
                        Log.d("VadReader", "chunk with speech " + speechData.size.toString())
                    } else {
                        Log.d("VadReader", "chunk no speech " + speechData.size.toString())
                        if (speechData.isNotEmpty()) {
                            output += speechData.size.toString() + "speech"

                            speechData = byteArrayOf()
                        }
                    }
                }
            }
        }

        return output
    }
}