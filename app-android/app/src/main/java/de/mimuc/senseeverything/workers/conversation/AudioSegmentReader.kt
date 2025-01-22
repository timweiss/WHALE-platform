package de.mimuc.senseeverything.workers.conversation

import com.konovalov.vad.yamnet.SoundCategory

class AudioSegmentReader {
    private var position = 0
    private var currentSectionLength = 0
    private var previousSectionLabel = ""

    internal fun processFrameChunk(frameChunk: ByteArray, sc: SoundCategory): VadReader.AudioSegment? {
        var segment: VadReader.AudioSegment? = null

        if (previousSectionLabel == "") {
            previousSectionLabel = sc.label
        } else if (sc.label != previousSectionLabel) {
            segment =
                VadReader.AudioSegment(
                    position,
                    currentSectionLength,
                    isSpeech(previousSectionLabel),
                    label = previousSectionLabel
                )
            currentSectionLength = 0
            previousSectionLabel = sc.label
        }

        currentSectionLength += frameChunk.size

        position += frameChunk.size

        return segment
    }

    internal fun getLastSection(): VadReader.AudioSegment {
        return VadReader.AudioSegment(
            position,
            currentSectionLength,
            isSpeech(previousSectionLabel),
            label = previousSectionLabel
        )
    }

    private fun isSpeech(label: String): Boolean {
        return label == "Speech"
    }
}