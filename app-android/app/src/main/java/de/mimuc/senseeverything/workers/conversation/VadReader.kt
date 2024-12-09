package de.mimuc.senseeverything.workers.conversation

import android.content.Context
import android.util.Log

abstract class VadReader {
    open val TAG = "VadReader"

    abstract fun detect(path: String, context: Context): List<AudioSegment>

    companion object {
        fun calculateSpeechPercentage(segments: List<AudioSegment>): Double {
            val totalLength = segments
                .fold(0) { acc, segment -> acc + segment.length }
            val speechLength = segments
                .filter { segment -> segment.hasSpeech }
                .fold(0) { acc, segment -> acc + segment.length }

            Log.d("VadReader", "total $totalLength and speech $speechLength")

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
    }

    data class AudioSegment(
        val position: Int,
        val length: Int,
        val hasSpeech: Boolean,
        val label: String = ""
    )
}