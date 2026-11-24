package com.example.yst.audio

import com.example.yst.detection.SpeechFrame
import kotlin.math.log10
import kotlin.math.sqrt

object AudioLevels {
    const val SAMPLE_RATE_HZ = 16_000
    const val FRAME_SAMPLES = 320
    const val FRAME_DURATION_MS = 20
    const val FULL_SCALE = 32_768.0
    const val SILENCE_DBFS = -120f

    fun dbfs(samples: ShortArray): Float {
        if (samples.isEmpty()) {
            return SILENCE_DBFS
        }
        var sumOfSquares = 0.0
        for (sample in samples) {
            val value = sample.toDouble()
            sumOfSquares += value * value
        }
        val meanSquare = sumOfSquares / samples.size
        if (meanSquare <= 0.0) {
            return SILENCE_DBFS
        }
        val rms = sqrt(meanSquare)
        return (20.0 * log10(rms / FULL_SCALE)).toFloat()
    }

    fun speechFrame(samples: ShortArray, timestampMs: Long, isSpeech: Boolean): SpeechFrame {
        return SpeechFrame(
            timestampMs = timestampMs,
            levelDbFs = dbfs(samples),
            isSpeech = isSpeech,
        )
    }
}
