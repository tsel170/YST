package com.example.yst.ui

import com.example.yst.detection.DetectionPhase

enum class MonitoringPresence {
    Stopped,
    Waiting,
    Monitoring,
    Error,
}

enum class MonitoringStatus {
    Stopped,
    Waiting,
    Listening,
    LoudSpeechDetected,
    Error,
}

/**
 * Maps the real detector phase to the three status labels.
 * Monitoring that has not started stays Stopped even if a caller passes another phase.
 * Arming is still Listening: the loud-speech cycle has not started.
 */
fun monitoringStatus(monitoringActive: Boolean, phase: DetectionPhase): MonitoringStatus {
    return monitoringStatus(
        presence = if (monitoringActive) MonitoringPresence.Monitoring else MonitoringPresence.Stopped,
        phase = phase,
    )
}

/**
 * Stopped is only a manual stop. Waiting means the user still wants monitoring
 * but capture is not running. Error is a permission or permanent failure.
 */
fun monitoringStatus(presence: MonitoringPresence, phase: DetectionPhase): MonitoringStatus {
    return when (presence) {
        MonitoringPresence.Stopped -> MonitoringStatus.Stopped
        MonitoringPresence.Waiting -> MonitoringStatus.Waiting
        MonitoringPresence.Error -> MonitoringStatus.Error
        MonitoringPresence.Monitoring -> when (phase) {
            DetectionPhase.Active -> MonitoringStatus.LoudSpeechDetected
            DetectionPhase.Idle,
            DetectionPhase.Arming,
            -> MonitoringStatus.Listening
        }
    }
}
