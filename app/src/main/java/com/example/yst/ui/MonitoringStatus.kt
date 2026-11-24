package com.example.yst.ui

import com.example.yst.detection.DetectionPhase

enum class MonitoringStatus {
    Stopped,
    Listening,
    LoudSpeechDetected,
}

/**
 * Maps the real detector phase to the three status labels.
 * Monitoring that has not started stays Stopped even if a caller passes another phase.
 * Arming is still Listening: the loud-speech cycle has not started.
 */
fun monitoringStatus(monitoringActive: Boolean, phase: DetectionPhase): MonitoringStatus {
    if (!monitoringActive) {
        return MonitoringStatus.Stopped
    }
    return when (phase) {
        DetectionPhase.Active -> MonitoringStatus.LoudSpeechDetected
        DetectionPhase.Idle,
        DetectionPhase.Arming,
        -> MonitoringStatus.Listening
    }
}
