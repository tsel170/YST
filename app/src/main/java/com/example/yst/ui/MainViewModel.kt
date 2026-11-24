package com.example.yst.ui

import androidx.lifecycle.ViewModel
import com.example.yst.detection.DetectionPhase
import com.example.yst.detection.DetectionSettings
import com.example.yst.detection.SoundClip
import com.example.yst.detection.SpeechDetector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UserNotice {
    MonitoringUnavailable,
    ImportUnavailable,
}

data class MainUiState(
    val settings: DetectionSettings = DetectionSettings(),
    val phase: DetectionPhase = DetectionPhase.Idle,
    val monitoringActive: Boolean = false,
    val levelDbFs: Float? = null,
    val lastPlayedSound: String? = null,
    val nextResponseIntervalMs: Long? = null,
    val sounds: List<SoundClip> = emptyList(),
)

class MainViewModel(
    private val detector: SpeechDetector = SpeechDetector(),
) : ViewModel() {
    private var settings = DetectionSettings()
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UserNotice>(extraBufferCapacity = 8)
    val messages: SharedFlow<UserNotice> = _messages.asSharedFlow()

    init {
        publish()
    }

    fun onThresholdChanged(decibels: Float) {
        updateSettings { it.copy(volumeThresholdDbFs = decibels.coerceIn(MIN_DB, MAX_DB)) }
    }

    fun onSpeechDurationChanged(milliseconds: Float) {
        updateSettings {
            it.copy(requiredLoudSpeechMs = milliseconds.coerceIn(MIN_SPEECH_MS, MAX_SPEECH_MS).toLong())
        }
    }

    fun onSilenceTimeoutChanged(milliseconds: Float) {
        updateSettings {
            it.copy(silenceTimeoutMs = milliseconds.coerceIn(MIN_SILENCE_MS, MAX_SILENCE_MS).toLong())
        }
    }

    fun onMinIntervalChanged(seconds: Float) {
        updateSettings { current ->
            val minimum = seconds.coerceIn(MIN_INTERVAL_S, MAX_INTERVAL_S).toMillis()
            current.copy(
                minResponseIntervalMs = minimum,
                maxResponseIntervalMs = current.maxResponseIntervalMs.coerceAtLeast(minimum),
            )
        }
    }

    fun onMaxIntervalChanged(seconds: Float) {
        updateSettings { current ->
            val maximum = seconds.coerceIn(MIN_INTERVAL_S, MAX_INTERVAL_S).toMillis()
            current.copy(
                minResponseIntervalMs = current.minResponseIntervalMs.coerceAtMost(maximum),
                maxResponseIntervalMs = maximum,
            )
        }
    }

    fun onMonitoringClicked() {
        _messages.tryEmit(UserNotice.MonitoringUnavailable)
    }

    fun onAddFilesClicked() {
        _messages.tryEmit(UserNotice.ImportUnavailable)
    }

    private fun updateSettings(transform: (DetectionSettings) -> DetectionSettings) {
        settings = transform(settings).sanitized()
        publish()
    }

    private fun publish() {
        detector.updateSettings(settings)
        _uiState.value = MainUiState(
            settings = settings,
            phase = detector.currentPhase,
            monitoringActive = false,
            levelDbFs = null,
            lastPlayedSound = null,
            nextResponseIntervalMs = null,
            sounds = emptyList(),
        )
    }

    private fun Float.toMillis(): Long = (this * 1_000f).toLong()

    companion object {
        const val MIN_DB = -60f
        const val MAX_DB = 0f
        const val MIN_SPEECH_MS = 200f
        const val MAX_SPEECH_MS = 5_000f
        const val MIN_SILENCE_MS = 200f
        const val MAX_SILENCE_MS = 8_000f
        const val MIN_INTERVAL_S = 1f
        const val MAX_INTERVAL_S = 60f
    }
}
