package com.example.yst.detection

data class DetectionSettings(
    val volumeThresholdDbFs: Float = -10f,
    val requiredLoudSpeechMs: Long = 1_200L,
    val silenceTimeoutMs: Long = 1_500L,
    val minResponseIntervalMs: Long = 20_000L,
    val maxResponseIntervalMs: Long = 60_000L,
) {
    fun sanitized(): DetectionSettings {
        val minimumInterval = minResponseIntervalMs.coerceAtLeast(0L)
        val maximumInterval = maxResponseIntervalMs.coerceAtLeast(minimumInterval)
        return copy(
            requiredLoudSpeechMs = requiredLoudSpeechMs.coerceAtLeast(0L),
            silenceTimeoutMs = silenceTimeoutMs.coerceAtLeast(0L),
            minResponseIntervalMs = minimumInterval,
            maxResponseIntervalMs = maximumInterval,
        )
    }
}

enum class DetectionPhase {
    Idle,
    Arming,
    Active,
}

data class SpeechFrame(
    val timestampMs: Long,
    val levelDbFs: Float,
    val isSpeech: Boolean,
)

data class DetectorStep(
    val phase: DetectionPhase,
    val cycleStarted: Boolean = false,
    val cycleEnded: Boolean = false,
)

data class SoundClip(
    val id: String,
    val displayName: String,
)

interface ResponsePlayer {
    val isPlaying: Boolean

    fun play(clip: SoundClip)

    fun setOnCompletionListener(listener: () -> Unit)

    fun stop() {}
}

fun interface Cancellable {
    fun cancel()
}

fun interface TaskRunner {
    fun postDelayed(delayMs: Long, action: () -> Unit): Cancellable
}
