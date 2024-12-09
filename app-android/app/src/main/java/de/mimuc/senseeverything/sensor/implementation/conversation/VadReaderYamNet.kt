package de.mimuc.senseeverything.sensor.implementation.conversation

import android.content.Context
import android.util.Log
import com.konovalov.vad.yamnet.VadYamnet
import com.konovalov.vad.yamnet.config.FrameSize
import com.konovalov.vad.yamnet.config.Mode
import com.konovalov.vad.yamnet.config.SampleRate
import de.mimuc.senseeverything.sensor.implementation.conversation.VadReader.AudioSegment
import java.io.File

class VadReaderYamNet {
    val TAG = "VadReaderYamNet"

    fun detectWithLabels(path: String, context: Context): List<AudioSegment> {
        val frames = arrayListOf<AudioSegment>()

        VadYamnet(
            context,
            sampleRate = SampleRate.SAMPLE_RATE_16K,
            frameSize = FrameSize.FRAME_SIZE_243,
            mode = Mode.NORMAL,
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
                    val sc = vad.classifyAudio(frameChunk)

                    if (sc.label == "Speech") {
                        if (!isSpeech) {
                            frames.add(
                                AudioSegment(
                                    position,
                                    currentSectionLength,
                                    false,
                                    label = sc.label
                                )
                            )
                            // was no speech, new section arrives
                            currentSectionLength = 0
                        }

                        currentSectionLength += frameChunk.size
                        isSpeech = true
                    } else {
                        if (isSpeech) {
                            frames.add(
                                AudioSegment(
                                    position,
                                    currentSectionLength,
                                    true,
                                    label = sc.label
                                )
                            )
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

        val labels = frames.map { it.label }
        Log.d(TAG, "All detected labels: $labels")

        return frames
    }
}