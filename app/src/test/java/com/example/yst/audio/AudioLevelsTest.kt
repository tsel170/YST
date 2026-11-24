package com.example.yst.audio

import com.example.yst.detection.SpeechFrame
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class AudioLevelsTest {
    @Test
    fun frameContractMatchesWebRtcVad() {
        assertEquals(16_000, AudioLevels.SAMPLE_RATE_HZ)
        assertEquals(320, AudioLevels.FRAME_SAMPLES)
        assertEquals(20, AudioLevels.FRAME_DURATION_MS)
        assertEquals(1_000, AudioLevels.SAMPLE_RATE_HZ / AudioLevels.FRAME_SAMPLES * AudioLevels.FRAME_DURATION_MS)
    }

    @Test
    fun digitalSilenceIsTheFloor() {
        val silence = ShortArray(AudioLevels.FRAME_SAMPLES)
        assertEquals(AudioLevels.SILENCE_DBFS, AudioLevels.dbfs(silence))
        assertEquals(AudioLevels.SILENCE_DBFS, AudioLevels.dbfs(ShortArray(0)))
    }

    @Test
    fun halfScaleSineReplacementIsAboutMinusSixDb() {
        val samples = ShortArray(AudioLevels.FRAME_SAMPLES) { 16_384 }
        val level = AudioLevels.dbfs(samples)
        assertEquals(-6.0206f, level, 0.02f)
    }

    @Test
    fun fullScaleIsJustUnderZeroDb() {
        val samples = ShortArray(AudioLevels.FRAME_SAMPLES) { 32_767 }
        val level = AudioLevels.dbfs(samples)
        assertEquals(true, level < 0f)
        assertEquals(0f, level, 0.01f)
    }

    @Test
    fun speechFrameKeepsLevelAndClassifierResult() {
        val samples = ShortArray(AudioLevels.FRAME_SAMPLES) { 16_384 }
        val frame = AudioLevels.speechFrame(
            samples = samples,
            timestampMs = 1_200L,
            isSpeech = true,
        )
        assertEquals(
            SpeechFrame(
                timestampMs = 1_200L,
                levelDbFs = AudioLevels.dbfs(samples),
                isSpeech = true,
            ),
            frame,
        )
        val noise = AudioLevels.speechFrame(samples, timestampMs = 40L, isSpeech = false)
        assertEquals(false, noise.isSpeech)
        assertEquals(true, abs(noise.levelDbFs - frame.levelDbFs) < 0.001f)
    }
}
