package com.example.yst.audio

import com.konovalov.vad.webrtc.VadWebRTC
import com.konovalov.vad.webrtc.config.FrameSize
import com.konovalov.vad.webrtc.config.Mode
import com.konovalov.vad.webrtc.config.SampleRate

/**
 * WebRTC VAD 2.0.10.
 *
 * API used, from `com.konovalov.vad.webrtc.VadWebRTC`:
 * `VadWebRTC(sampleRate, frameSize, mode, speechDurationMs, silenceDurationMs)`
 * and `isSpeech(audioData: ShortArray): Boolean`, closed with `close()`.
 *
 * Speech and silence durations are 0. `getFramesCount` then returns 0, so the
 * library returns each frame's own result. [com.example.yst.detection.SpeechDetector]
 * owns the 1200 ms and 1500 ms timings.
 */
class WebRtcSpeechClassifier : AutoCloseable {
    private val vad = VadWebRTC(
        sampleRate = SampleRate.SAMPLE_RATE_16K,
        frameSize = FrameSize.FRAME_SIZE_320,
        mode = Mode.VERY_AGGRESSIVE,
        speechDurationMs = 0,
        silenceDurationMs = 0,
    )

    fun isSpeech(samples: ShortArray): Boolean {
        require(samples.size == AudioLevels.FRAME_SAMPLES) {
            "WebRTC VAD expects ${AudioLevels.FRAME_SAMPLES} samples"
        }
        return vad.isSpeech(samples)
    }

    override fun close() {
        vad.close()
    }
}
