package com.example.yst.ui

import com.example.yst.detection.DetectionPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MonitoringStatusTest {
    @Test
    fun inactiveMonitoringStaysStopped() {
        assertEquals(
            MonitoringStatus.Stopped,
            monitoringStatus(monitoringActive = false, phase = DetectionPhase.Active),
        )
    }

    @Test
    fun monitoringBeforeACycleIsListening() {
        assertEquals(
            MonitoringStatus.Listening,
            monitoringStatus(monitoringActive = true, phase = DetectionPhase.Idle),
        )
        assertEquals(
            MonitoringStatus.Listening,
            monitoringStatus(monitoringActive = true, phase = DetectionPhase.Arming),
        )
    }

    @Test
    fun activeCycleIsLoudSpeech() {
        assertEquals(
            MonitoringStatus.LoudSpeechDetected,
            monitoringStatus(monitoringActive = true, phase = DetectionPhase.Active),
        )
    }
}

class MainViewModelTest {
    @Test
    fun controlsDoNotInventMonitoringLevelOrSounds() {
        val viewModel = MainViewModel()

        viewModel.onThresholdChanged(-24f)
        viewModel.onMonitoringClicked()
        viewModel.onAddFilesClicked()

        val state = viewModel.uiState.value
        assertFalse(state.monitoringActive)
        assertNull(state.levelDbFs)
        assertNull(state.lastPlayedSound)
        assertNull(state.nextResponseIntervalMs)
        assertEquals(emptyList<Any>(), state.sounds)
        assertEquals(DetectionPhase.Idle, state.phase)
        assertEquals(-24f, state.settings.volumeThresholdDbFs)
        assertEquals(
            MonitoringStatus.Stopped,
            monitoringStatus(state.monitoringActive, state.phase),
        )
    }

    @Test
    fun intervalSlidersStayOrderedThroughDetectionSettings() {
        val viewModel = MainViewModel()

        viewModel.onMinIntervalChanged(25f)
        viewModel.onMaxIntervalChanged(8f)

        val settings = viewModel.uiState.value.settings
        assertEquals(8_000L, settings.minResponseIntervalMs)
        assertEquals(8_000L, settings.maxResponseIntervalMs)
    }
}
