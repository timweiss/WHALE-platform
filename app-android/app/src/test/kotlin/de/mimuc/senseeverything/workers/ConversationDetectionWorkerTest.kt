package de.mimuc.senseeverything.workers

import de.mimuc.senseeverything.workers.conversation.VadReader
import de.mimuc.senseeverything.workers.conversation.VadReader.Companion.percentagePerLabel
import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test

class ConversationDetectionWorkerTest {

    @Test
    fun testPercentagePerLabel() {
        val speech = VadReader.AudioSegment(0, 10, true, "speech")
        val music = VadReader.AudioSegment(10, 20, false, "music")
        val silence = VadReader.AudioSegment(80,70, false, "silence")

        val total = speech.length + music.length + silence.length
        val speechPercentage = speech.length.toFloat() / total
        val musicPercentage = music.length.toFloat() / total
        val silencePercentage = silence.length.toFloat() / total

        val result = percentagePerLabel(listOf(speech, music, silence))
        assertEquals(speechPercentage, result["speech"])
        assertEquals(musicPercentage, result["music"])
        assertEquals(silencePercentage, result["silence"])
    }
}