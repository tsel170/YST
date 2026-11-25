package com.example.yst.audio

import com.example.yst.detection.Cancellable
import com.example.yst.ui.MonitoringPresence

/**
 * Keeps one capture attempt and one retry in flight.
 * Manual disable cancels recovery. Permission loss does not retry.
 * A silenced recorder stays the current capture and is not replaced.
 */
class MicrophoneWatch(
    private val schedule: (delayMs: Long, action: () -> Unit) -> Cancellable,
    private val startCapture: () -> Unit,
    private val stopCapture: () -> Unit,
    private val retryIntervalMs: () -> Long,
    private val isCapturing: () -> Boolean = { false },
) {
    var enabled: Boolean = false
        private set

    var presence: MonitoringPresence = MonitoringPresence.Stopped
        private set

    private var retry: Cancellable? = null
    private var captureActive = false
    private var starting = false
    private var recover = false

    fun restoreEnabledWithoutStarting(enabled: Boolean) {
        this.enabled = enabled
        recover = false
        presence = if (enabled) MonitoringPresence.Waiting else MonitoringPresence.Stopped
    }

    fun enable() {
        enabled = true
        recover = true
        if (presence == MonitoringPresence.Error) {
            presence = MonitoringPresence.Waiting
        }
        attemptStart()
    }

    fun disable() {
        enabled = false
        recover = false
        cancelRetry()
        starting = false
        captureActive = false
        stopCapture()
        presence = MonitoringPresence.Stopped
    }

    fun onCaptureStarted() {
        if (!enabled) {
            stopCapture()
            return
        }
        starting = false
        captureActive = true
        cancelRetry()
        presence = MonitoringPresence.Monitoring
    }

    fun onSilenced() {
        if (!enabled) {
            return
        }
        starting = false
        captureActive = true
        presence = MonitoringPresence.Waiting
    }

    fun onSilenceCleared() {
        if (!enabled || !captureActive) {
            return
        }
        presence = MonitoringPresence.Monitoring
    }

    fun onTemporarilyUnavailable() {
        if (!enabled) {
            return
        }
        captureActive = false
        starting = false
        presence = MonitoringPresence.Waiting
        stopCapture()
        scheduleRetry()
    }

    fun onPermissionRevoked() {
        if (!enabled) {
            return
        }
        recover = false
        captureActive = false
        starting = false
        cancelRetry()
        stopCapture()
        presence = MonitoringPresence.Error
    }

    fun onMicrophoneAvailable() {
        if (!recover || !enabled || presence == MonitoringPresence.Error || captureActive || starting) {
            return
        }
        cancelRetry()
        attemptStart()
    }

    fun onHostLost() {
        recover = false
        cancelRetry()
        starting = false
        captureActive = false
        stopCapture()
        presence = if (enabled) MonitoringPresence.Waiting else MonitoringPresence.Stopped
    }

    private fun attemptStart() {
        if (!enabled || captureActive || starting || presence == MonitoringPresence.Error) {
            return
        }
        cancelRetry()
        starting = true
        presence = MonitoringPresence.Waiting
        startCapture()
        if (starting && isCapturing()) {
            onCaptureStarted()
        }
    }

    private fun scheduleRetry() {
        if (retry != null || !recover || !enabled || presence == MonitoringPresence.Error) {
            return
        }
        val delay = retryIntervalMs().coerceIn(MIN_RETRY_MS, MAX_RETRY_MS)
        retry = schedule(delay) {
            retry = null
            if (!recover || !enabled || captureActive || starting || presence == MonitoringPresence.Error) {
                return@schedule
            }
            attemptStart()
        }
    }

    private fun cancelRetry() {
        retry?.cancel()
        retry = null
    }

    private companion object {
        const val MIN_RETRY_MS = 1_000L
        const val MAX_RETRY_MS = 30_000L
    }
}
