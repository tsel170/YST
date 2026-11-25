package com.example.yst.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechDetectorTest {
    private val settings = DetectionSettings(
        volumeThresholdDbFs = -30f,
        requiredLoudSpeechMs = 1_200L,
        silenceTimeoutMs = 1_500L,
    )

    @Test
    fun loudSpeechShorterThanRequiredDurationDoesNotStartCycle() {
        val detector = SpeechDetector(settings)

        val step = detector.onFrame(frame(timestampMs = 1_199L, levelDbFs = -20f, isSpeech = true))

        assertEquals(DetectionPhase.Arming, step.phase)
        assertFalse(step.cycleStarted)
    }

    @Test
    fun droppingBelowThresholdBeforeDurationResetsArming() {
        val detector = SpeechDetector(settings)
        detector.onFrame(frame(timestampMs = 0L, levelDbFs = -20f, isSpeech = true))

        val dipped = detector.onFrame(frame(timestampMs = 600L, levelDbFs = -40f, isSpeech = true))
        val dropped = detector.onFrame(frame(timestampMs = 901L, levelDbFs = -40f, isSpeech = true))
        val restarted = detector.onFrame(frame(timestampMs = 1_700L, levelDbFs = -20f, isSpeech = true))

        assertEquals(DetectionPhase.Arming, dipped.phase)
        assertEquals(DetectionPhase.Idle, dropped.phase)
        assertEquals(DetectionPhase.Arming, restarted.phase)
        assertFalse(restarted.cycleStarted)
    }

    @Test
    fun speechLostBeforeDurationResetsArming() {
        val detector = SpeechDetector(settings)
        detector.onFrame(frame(timestampMs = 0L, levelDbFs = -20f, isSpeech = true))
        detector.onFrame(frame(timestampMs = 400L, levelDbFs = -10f, isSpeech = false))

        val step = detector.onFrame(frame(timestampMs = 701L, levelDbFs = -10f, isSpeech = false))

        assertEquals(DetectionPhase.Idle, step.phase)
        assertFalse(step.cycleStarted)
    }

    @Test
    fun cycleStartsWhenLoudSpeechReachesRequiredDuration() {
        val detector = SpeechDetector(settings)
        detector.onFrame(frame(timestampMs = 0L, levelDbFs = -30f, isSpeech = true))

        val step = detector.onFrame(frame(timestampMs = 1_200L, levelDbFs = -30f, isSpeech = true))

        assertEquals(DetectionPhase.Active, step.phase)
        assertTrue(step.cycleStarted)
        assertFalse(step.cycleEnded)
    }

    @Test
    fun briefDipWhileArmingDoesNotReturnToIdle() {
        val detector = SpeechDetector(settings)
        detector.onFrame(frame(timestampMs = 0L, levelDbFs = -20f, isSpeech = true))
        val dipped = detector.onFrame(frame(timestampMs = 900L, levelDbFs = -40f, isSpeech = true))

        detector.onFrame(frame(timestampMs = 1_100L, levelDbFs = -20f, isSpeech = true))
        val step = detector.onFrame(frame(timestampMs = 1_200L, levelDbFs = -20f, isSpeech = true))

        assertEquals(DetectionPhase.Arming, dipped.phase)
        assertTrue(step.cycleStarted)
    }

    @Test
    fun nextCycleStartsQuicklyAfterThePreviousOneEnds() {
        val detector = startedDetector()
        detector.onFrame(frame(timestampMs = 2_000L, levelDbFs = -60f, isSpeech = false))
        val ended = detector.onFrame(frame(timestampMs = 3_500L, levelDbFs = -60f, isSpeech = false))
        detector.onFrame(frame(timestampMs = 4_000L, levelDbFs = -10f, isSpeech = true))

        val step = detector.onFrame(frame(timestampMs = 4_200L, levelDbFs = -10f, isSpeech = true))

        assertTrue(ended.cycleEnded)
        assertEquals(DetectionPhase.Idle, ended.phase)
        assertTrue(step.cycleStarted)
        assertEquals(DetectionPhase.Active, step.phase)
    }

    @Test
    fun shortDropBelowThresholdDoesNotEndActiveCycle() {
        val detector = startedDetector()

        val step = detector.onFrame(frame(timestampMs = 2_000L, levelDbFs = -80f, isSpeech = true))

        assertEquals(DetectionPhase.Active, step.phase)
        assertFalse(step.cycleEnded)
    }

    @Test
    fun sustainedLevelBelowThresholdEndsActiveCycle() {
        val detector = startedDetector()
        detector.onFrame(frame(timestampMs = 2_000L, levelDbFs = -80f, isSpeech = true))

        val step = detector.onFrame(frame(timestampMs = 3_500L, levelDbFs = -80f, isSpeech = true))

        assertEquals(DetectionPhase.Idle, step.phase)
        assertTrue(step.cycleEnded)
    }

    @Test
    fun shortSilenceDoesNotEndActiveCycle() {
        val detector = startedDetector()
        detector.onFrame(frame(timestampMs = 2_000L, levelDbFs = -60f, isSpeech = false))

        val step = detector.onFrame(frame(timestampMs = 3_499L, levelDbFs = -60f, isSpeech = false))

        assertEquals(DetectionPhase.Active, step.phase)
        assertFalse(step.cycleEnded)
    }

    @Test
    fun silenceTimeoutEndsCycle() {
        val detector = startedDetector()
        detector.onFrame(frame(timestampMs = 2_000L, levelDbFs = -60f, isSpeech = false))

        val step = detector.onFrame(frame(timestampMs = 3_500L, levelDbFs = -60f, isSpeech = false))

        assertEquals(DetectionPhase.Idle, step.phase)
        assertTrue(step.cycleEnded)
    }

    @Test
    fun speechBelowThresholdDoesNotResetSilenceTimer() {
        val detector = startedDetector()
        detector.onFrame(frame(timestampMs = 2_000L, levelDbFs = -55f, isSpeech = false))

        val stillActive = detector.onFrame(frame(timestampMs = 3_499L, levelDbFs = -55f, isSpeech = true))
        val ended = detector.onFrame(frame(timestampMs = 3_500L, levelDbFs = -55f, isSpeech = true))

        assertEquals(DetectionPhase.Active, stillActive.phase)
        assertTrue(ended.cycleEnded)
    }

    @Test
    fun loudSpeechReturnsBeforeSilenceTimeoutKeepsCycle() {
        val detector = startedDetector()
        detector.onFrame(frame(timestampMs = 2_000L, levelDbFs = -55f, isSpeech = true))
        detector.onFrame(frame(timestampMs = 3_000L, levelDbFs = -20f, isSpeech = true))
        detector.onFrame(frame(timestampMs = 3_100L, levelDbFs = -55f, isSpeech = true))

        val step = detector.onFrame(frame(timestampMs = 4_599L, levelDbFs = -55f, isSpeech = true))

        assertEquals(DetectionPhase.Active, step.phase)
        assertFalse(step.cycleEnded)
    }

    @Test
    fun newCycleCanStartAfterPreviousCycleEnds() {
        val detector = startedDetector()
        detector.onFrame(frame(timestampMs = 2_000L, levelDbFs = -60f, isSpeech = false))
        detector.onFrame(frame(timestampMs = 3_500L, levelDbFs = -60f, isSpeech = false))

        detector.onFrame(frame(timestampMs = 4_000L, levelDbFs = -10f, isSpeech = true))
        val step = detector.onFrame(frame(timestampMs = 5_200L, levelDbFs = -10f, isSpeech = true))

        assertTrue(step.cycleStarted)
        assertEquals(DetectionPhase.Active, step.phase)
    }

    @Test
    fun zeroRequiredDurationStartsOnFirstLoudFrame() {
        val detector = SpeechDetector(settings.copy(requiredLoudSpeechMs = 0L))

        val step = detector.onFrame(frame(timestampMs = 10L, levelDbFs = -10f, isSpeech = true))

        assertTrue(step.cycleStarted)
    }

    private fun startedDetector(): SpeechDetector {
        val detector = SpeechDetector(settings)
        detector.onFrame(frame(timestampMs = 0L, levelDbFs = -20f, isSpeech = true))
        val started = detector.onFrame(frame(timestampMs = 1_200L, levelDbFs = -20f, isSpeech = true))
        assertTrue(started.cycleStarted)
        return detector
    }

    private fun frame(timestampMs: Long, levelDbFs: Float, isSpeech: Boolean): SpeechFrame {
        return SpeechFrame(
            timestampMs = timestampMs,
            levelDbFs = levelDbFs,
            isSpeech = isSpeech,
        )
    }
}
