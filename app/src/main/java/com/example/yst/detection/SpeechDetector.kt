package com.example.yst.detection

/**
 * Turns a stream of speech frames into detection cycles.
 *
 * A cycle starts only after speech stays at or above [DetectionSettings.volumeThresholdDbFs]
 * for [DetectionSettings.requiredLoudSpeechMs]. Until that moment, any frame that is not
 * both speech and loud returns the detector to idle.
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
        loudSpeechStartMs = frame.timestampMs
        if (settings.requiredLoudSpeechMs == 0L) {
            return beginCycle()
        }
        phase = DetectionPhase.Arming
        return step()
    }

    private fun onArming(frame: SpeechFrame, loudSpeech: Boolean): DetectorStep {
        if (!loudSpeech) {
            phase = DetectionPhase.Idle
            loudSpeechStartMs = null
            return step()
        }
        val startedAt = loudSpeechStartMs ?: frame.timestampMs
        loudSpeechStartMs = startedAt
        val elapsed = frame.timestampMs - startedAt
        if (elapsed >= settings.requiredLoudSpeechMs) {
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
        loudSpeechStartMs = null
        silenceStartMs = null
        return step(cycleStarted = true)
    }

    private fun endCycle(): DetectorStep {
        phase = DetectionPhase.Idle
        loudSpeechStartMs = null
        silenceStartMs = null
        return step(cycleEnded = true)
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
}
