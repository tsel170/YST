package com.example.yst.ui

import com.example.yst.audio.AudioCapture
import com.example.yst.audio.AudioCaptureFailure
import com.example.yst.audio.AudioCaptureListener
import com.example.yst.detection.DetectionPhase
import com.example.yst.detection.ManualClock
import com.example.yst.detection.ResponsePlayer
import com.example.yst.detection.SoundClip
import com.example.yst.detection.SpeechFrame
import com.example.yst.sounds.MemorySoundLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun settingsDoNotInventLevelOrSounds() {
        val viewModel = viewModel()

        viewModel.onThresholdChanged(-24f)

        val state = viewModel.uiState.value
        assertFalse(state.monitoringActive)
        assertNull(state.levelDbFs)
        assertNull(state.lastPlayedSound)
        assertNull(state.nextResponseIntervalMs)
        assertTrue(state.sounds.isEmpty())
        assertEquals(DetectionPhase.Idle, state.phase)
        assertEquals(-24f, state.settings.volumeThresholdDbFs)
    }

    @Test
    fun intervalSlidersStayOrderedThroughDetectionSettings() {
        val viewModel = viewModel()

        viewModel.onMinIntervalChanged(25f)
        viewModel.onMaxIntervalChanged(8f)

        val settings = viewModel.uiState.value.settings
        assertEquals(8_000L, settings.minResponseIntervalMs)
        assertEquals(8_000L, settings.maxResponseIntervalMs)
    }

    @Test
    fun failedStartDoesNotActivateMonitoring() {
        val capture = FakeAudioCapture(failure = AudioCaptureFailure.MissingPermission)
        val viewModel = viewModel(capture)

        viewModel.startMonitoring()

        assertFalse(viewModel.uiState.value.monitoringActive)
        assertNull(viewModel.uiState.value.levelDbFs)
        assertEquals(DetectionPhase.Idle, viewModel.uiState.value.phase)
    }

    @Test
    fun framesUpdateLiveLevelAndExistingDetector() {
        val capture = FakeAudioCapture()
        val viewModel = viewModel(capture)
        viewModel.onThresholdChanged(-30f)

        viewModel.startMonitoring()
        capture.emit(SpeechFrame(timestampMs = 0L, levelDbFs = -10f, isSpeech = true))

        assertTrue(viewModel.uiState.value.monitoringActive)
        assertEquals(-10f, viewModel.uiState.value.levelDbFs)
        assertEquals(DetectionPhase.Arming, viewModel.uiState.value.phase)
        assertEquals(
            MonitoringStatus.Listening,
            monitoringStatus(true, viewModel.uiState.value.phase),
        )

        capture.emit(SpeechFrame(timestampMs = 1_200L, levelDbFs = -18f, isSpeech = true))

        assertEquals(-18f, viewModel.uiState.value.levelDbFs)
        assertEquals(DetectionPhase.Active, viewModel.uiState.value.phase)
        assertEquals(
            MonitoringStatus.LoudSpeechDetected,
            monitoringStatus(true, viewModel.uiState.value.phase),
        )
    }

    @Test
    fun volumeDipKeepsTheCycleAndTheLiveLevel() {
        val capture = FakeAudioCapture()
        val viewModel = viewModel(capture)
        viewModel.onThresholdChanged(-30f)
        viewModel.startMonitoring()
        capture.emit(SpeechFrame(timestampMs = 0L, levelDbFs = -10f, isSpeech = true))
        capture.emit(SpeechFrame(timestampMs = 1_200L, levelDbFs = -10f, isSpeech = true))

        capture.emit(SpeechFrame(timestampMs = 1_500L, levelDbFs = -55f, isSpeech = true))

        assertEquals(DetectionPhase.Active, viewModel.uiState.value.phase)
        assertEquals(-55f, viewModel.uiState.value.levelDbFs)
    }

    @Test
    fun stopClearsTheLiveLevel() {
        val capture = FakeAudioCapture()
        val viewModel = viewModel(capture)
        viewModel.startMonitoring()
        capture.emit(SpeechFrame(timestampMs = 0L, levelDbFs = -12f, isSpeech = false))

        viewModel.stopMonitoring()
        capture.emit(SpeechFrame(timestampMs = 20L, levelDbFs = -3f, isSpeech = true))

        assertFalse(viewModel.uiState.value.monitoringActive)
        assertNull(viewModel.uiState.value.levelDbFs)
        assertEquals(DetectionPhase.Idle, viewModel.uiState.value.phase)
        assertTrue(capture.stopped)
    }

    @Test
    fun thresholdSliderExtendsAboveFullScale() {
        val viewModel = viewModel()

        viewModel.onThresholdChanged(30f)
        assertEquals(30f, viewModel.uiState.value.settings.volumeThresholdDbFs)

        viewModel.onThresholdChanged(80f)
        assertEquals(MainViewModel.MAX_DB, viewModel.uiState.value.settings.volumeThresholdDbFs)
    }

    @Test
    fun thresholdChangeStillGatesTheExistingDetector() {
        val capture = FakeAudioCapture()
        val viewModel = viewModel(capture)
        viewModel.onThresholdChanged(0f)
        viewModel.startMonitoring()

        capture.emit(SpeechFrame(timestampMs = 2_000L, levelDbFs = -10f, isSpeech = true))

        assertEquals(DetectionPhase.Idle, viewModel.uiState.value.phase)
        assertEquals(-10f, viewModel.uiState.value.levelDbFs)
    }

    @Test
    fun addedSoundIsListedAndPlayedWhenACycleStarts() {
        val clip = SoundClip(id = "content://sounds/alpha", displayName = "Alpha")
        val player = RecordingPlayer()
        val capture = FakeAudioCapture()
        val viewModel = MainViewModel(
            audioCapture = capture,
            taskRunner = ManualClock(),
            deliver = { block -> block() },
            soundLibrary = MemorySoundLibrary(),
            player = player,
        )

        viewModel.addSounds(listOf(clip))
        viewModel.onThresholdChanged(-30f)
        viewModel.startMonitoring()
        capture.emit(SpeechFrame(timestampMs = 0L, levelDbFs = -10f, isSpeech = true))
        capture.emit(SpeechFrame(timestampMs = 1_200L, levelDbFs = -10f, isSpeech = true))

        assertEquals(listOf(clip), viewModel.uiState.value.sounds)
        assertEquals(listOf(clip.id), player.playedIds)
        assertEquals("Alpha", viewModel.uiState.value.lastPlayedSound)

        viewModel.removeSound(clip.id)
        assertTrue(viewModel.uiState.value.sounds.isEmpty())
    }

    private fun viewModel(capture: AudioCapture = FakeAudioCapture()): MainViewModel {
        return MainViewModel(
            audioCapture = capture,
            taskRunner = ManualClock(),
            deliver = { block -> block() },
        )
    }

    private class FakeAudioCapture(
        private val failure: AudioCaptureFailure? = null,
    ) : AudioCapture {
        var listener: AudioCaptureListener? = null
        var stopped: Boolean = false

        override fun start(listener: AudioCaptureListener) {
            this.listener = listener
            val startFailure = failure
            if (startFailure != null) {
                listener.onFailure(startFailure)
                return
            }
            listener.onStarted()
        }

        override fun stop() {
            stopped = true
            listener = null
        }

        fun emit(frame: SpeechFrame) {
            listener?.onFrame(frame)
        }
    }

    private class RecordingPlayer : ResponsePlayer {
        val playedIds = mutableListOf<String>()
        override var isPlaying: Boolean = false
        private var onCompletion: () -> Unit = {}

        override fun play(clip: SoundClip) {
            check(!isPlaying)
            isPlaying = true
            playedIds += clip.id
        }

        override fun setOnCompletionListener(listener: () -> Unit) {
            onCompletion = listener
        }
    }
}
