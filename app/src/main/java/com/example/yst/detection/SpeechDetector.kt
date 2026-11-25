package com.example.yst.detection

/**
 * Turns a stream of speech frames into detection cycles.
 *
 * The first cycle starts only after speech stays at or above [DetectionSettings.volumeThresholdDbFs]
 * for [DetectionSettings.requiredLoudSpeechMs]. A later cycle starts after a short confirmation.
 * A brief dip below the threshold does not throw the detector back to idle.
 *
 * After the cycle starts, a frame keeps it alive only while speech stays at or above the
 * volume threshold. Audio below the threshold counts as silence. The cycle ends once that
 * silence has lasted [DetectionSettings.silenceTimeoutMs].
 *
 * Frames must arrive with non-decreasing timestamps. The detector itself is single-threaded.
 * A future speaker filter can sit in front of this class and pass only the frames that
 * should count as speech; this class does not identify speakers.
 */
class SpeechDetector(
    settings: DetectionSettings = DetectionSettings(),
) {
    private var settings: DetectionSettings = settings.sanitized()
    private var phase: DetectionPhase = DetectionPhase.Idle
    private var loudSpeechStartMs: Long? = null
    private var silenceStartMs: Long? = null
    private var gapStartedMs: Long? = null
    private var awaitingRearm = false

    val currentPhase: DetectionPhase
        get() = phase

    fun updateSettings(settings: DetectionSettings) {
        this.settings = settings.sanitized()
    }

    fun onFrame(frame: SpeechFrame): DetectorStep {
        val loudSpeech = frame.isSpeech && frame.levelDbFs >= settings.volumeThresholdDbFs
        return when (phase) {
            DetectionPhase.Idle -> onIdle(frame, loudSpeech)
            DetectionPhase.Arming -> onArming(frame, loudSpeech)
            DetectionPhase.Active -> onActive(frame, loudSpeech)
        }
    }

    private fun onIdle(frame: SpeechFrame, loudSpeech: Boolean): DetectorStep {
        if (!loudSpeech) {
            return step()
        }
        gapStartedMs = null
        loudSpeechStartMs = frame.timestampMs
        if (loudDurationMs() == 0L) {
            return beginCycle()
        }
        phase = DetectionPhase.Arming
        return step()
    }

    private fun onArming(frame: SpeechFrame, loudSpeech: Boolean): DetectorStep {
        if (!loudSpeech) {
            val gapStarted = gapStartedMs ?: frame.timestampMs
            gapStartedMs = gapStarted
            if (frame.timestampMs - gapStarted > ARMING_GAP_MS) {
                return failArming()
            }
            return step()
        }
        val gapStarted = gapStartedMs
        gapStartedMs = null
        if (gapStarted != null && frame.timestampMs - gapStarted > ARMING_GAP_MS) {
            loudSpeechStartMs = frame.timestampMs
            return step()
        }
        val startedAt = loudSpeechStartMs ?: frame.timestampMs
        loudSpeechStartMs = startedAt
        if (frame.timestampMs - startedAt >= loudDurationMs()) {
            return beginCycle()
        }
        return step()
    }

    private fun onActive(frame: SpeechFrame, loudSpeech: Boolean): DetectorStep {
        if (loudSpeech) {
            silenceStartMs = null
            return step()
        }
        val startedAt = silenceStartMs
        if (startedAt == null) {
            silenceStartMs = frame.timestampMs
            if (settings.silenceTimeoutMs == 0L) {
                return endCycle()
            }
            return step()
        }
        val elapsed = frame.timestampMs - startedAt
        if (elapsed >= settings.silenceTimeoutMs) {
            return endCycle()
        }
        return step()
    }

    private fun beginCycle(): DetectorStep {
        phase = DetectionPhase.Active
        awaitingRearm = false
        loudSpeechStartMs = null
        silenceStartMs = null
        gapStartedMs = null
        return step(cycleStarted = true)
    }

    private fun endCycle(): DetectorStep {
        phase = DetectionPhase.Idle
        awaitingRearm = true
        loudSpeechStartMs = null
        silenceStartMs = null
        gapStartedMs = null
        return step(cycleEnded = true)
    }

    private fun failArming(): DetectorStep {
        phase = DetectionPhase.Idle
        loudSpeechStartMs = null
        gapStartedMs = null
        return step()
    }

    private fun loudDurationMs(): Long {
        val required = settings.requiredLoudSpeechMs
        return if (awaitingRearm) minOf(required, REARM_LOUD_MS) else required
    }

    private fun step(
        cycleStarted: Boolean = false,
        cycleEnded: Boolean = false,
    ): DetectorStep {
        return DetectorStep(
            phase = phase,
            cycleStarted = cycleStarted,
            cycleEnded = cycleEnded,
        )
    }

    private companion object {
        const val ARMING_GAP_MS = 300L
        const val REARM_LOUD_MS = 200L
    }
}
