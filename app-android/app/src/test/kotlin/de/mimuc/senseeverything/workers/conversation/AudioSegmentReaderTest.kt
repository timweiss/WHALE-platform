package de.mimuc.senseeverything.workers.conversation

import com.konovalov.vad.yamnet.SoundCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AudioSegmentReaderTest {

    @Test
    fun testProcessFrameChunk() {
        val soundCategories = arrayListOf(
            SoundCategory("Speech"),
            SoundCategory("Silence"),
            SoundCategory("Silence"),
            SoundCategory("Silence"),
            SoundCategory("Silence"),
            SoundCategory("Speech"),
            SoundCategory("Speech"),
            SoundCategory("Speech"),
            SoundCategory("Speech"),
            SoundCategory("Silence"),
            SoundCategory("Silence"),
            SoundCategory("Speech"),
            SoundCategory("Speech")
        )
        val frameChunkDummy = ByteArray(243 * 2)

        val reader = AudioSegmentReader()
        val segments = arrayListOf<VadReader.AudioSegment>()
        for (sc in soundCategories) {
            val segment = reader.processFrameChunk(frameChunkDummy, sc)
            if (segment != null) {
                segments.add(segment)
            }
        }
        segments.add(reader.getLastSection())

        assertEquals(segments.filter { it.label == "Speech" }.size, 3)
        assertEquals(segments.filter { it.label == "Silence" }.size, 2)
        assertEquals(segments.filter { it.hasSpeech }.size, segments.filter { it.label == "Speech" }.size) // Label "Speech" is always isSpeech
        assertEquals(segments.groupBy { it.label }.mapValues { (_, segments) ->
            segments.fold(0) { acc, segment -> acc + segment.length }
        }["Speech"], soundCategories.filter { it.label == "Speech" }.size * frameChunkDummy.size) // speech duration
        assertEquals(soundCategories.size * frameChunkDummy.size, segments.fold(0) { acc, segment -> acc + segment.length }) // all sections
    }
}