package com.example.yst.audio

import com.example.yst.detection.DetectionPhase
import com.example.yst.detection.ManualClock
import com.example.yst.ui.MainViewModel
import com.example.yst.ui.MonitoringPresence
import com.example.yst.ui.MonitoringStatus
import com.example.yst.ui.monitoringStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrophoneWatchTest {
    @Test
    fun manualStopPreventsAvailabilityAndRetry() {
        val harness = WatchHarness()
        harness.watch.enable()
        harness.watch.onTemporarilyUnavailable()

        harness.watch.disable()
        harness.clock.advanceBy(60_000)
        harness.watch.onMicrophoneAvailable()

        assertEquals(1, harness.starts)
        assertFalse(harness.watch.enabled)
        assertEquals(MonitoringPresence.Stopped, harness.watch.presence)
    }

    @Test
    fun temporaryLossResumesWhenTheMicrophoneReturns() {
        val harness = WatchHarness()
        harness.watch.enable()
        harness.watch.onTemporarilyUnavailable()
        assertEquals(MonitoringPresence.Waiting, harness.watch.presence)

        harness.watch.onMicrophoneAvailable()

        assertEquals(2, harness.starts)
        assertEquals(MonitoringPresence.Monitoring, harness.watch.presence)
        harness.clock.advanceBy(60_000)
        assertEquals(2, harness.starts)
    }

    @Test
    fun retryUsesTheConfiguredIntervalAndDoesNotStack() {
        val harness = WatchHarness()
        harness.intervalMs = 10_000
        harness.watch.enable()
        harness.watch.onTemporarilyUnavailable()
        harness.watch.onTemporarilyUnavailable()

        assertEquals(1, harness.schedules)
        harness.clock.advanceBy(9_999)
        assertEquals(1, harness.starts)
        harness.clock.advanceBy(1)
        assertEquals(2, harness.starts)
        assertEquals(1, harness.schedules)
    }

    @Test
    fun permissionLossDoesNotRetryOrResume() {
        val harness = WatchHarness()
        harness.watch.enable()
        harness.watch.onPermissionRevoked()

        harness.watch.onMicrophoneAvailable()
        harness.clock.advanceBy(60_000)

        assertEquals(1, harness.starts)
        assertTrue(harness.watch.enabled)
        assertEquals(MonitoringPresence.Error, harness.watch.presence)
    }

    @Test
    fun microphoneUseReleasesCaptureUntilTheMicrophoneIsFree() {
        val harness = WatchHarness()
        harness.watch.enable()
        harness.microphoneFree = false
        harness.watch.onMicrophoneTaken()
        harness.watch.onMicrophoneAvailable()
        harness.clock.advanceBy(60_000)

        assertEquals(1, harness.starts)
        assertEquals(MonitoringPresence.Waiting, harness.watch.presence)
        harness.microphoneFree = true
        harness.watch.onMicrophoneAvailable()
        assertEquals(2, harness.starts)
        assertEquals(MonitoringPresence.Monitoring, harness.watch.presence)
    }

    @Test
    fun savedIntentDoesNotStartCaptureByItself() {
        val harness = WatchHarness()
        harness.watch.restoreEnabledWithoutStarting(true)

        assertEquals(0, harness.starts)
        assertEquals(MonitoringPresence.Waiting, harness.watch.presence)
        harness.clock.advanceBy(60_000)
        assertEquals(0, harness.starts)
    }

    @Test
    fun hostLossDoesNotScheduleARetry() {
        val harness = WatchHarness()
        harness.watch.enable()
        harness.watch.onHostLost()

        harness.clock.advanceBy(60_000)
        harness.watch.onMicrophoneAvailable()

        assertEquals(1, harness.starts)
        assertTrue(harness.watch.enabled)
        assertEquals(MonitoringPresence.Waiting, harness.watch.presence)
    }

    private class WatchHarness {
        val clock = ManualClock()
        var starts = 0
        var schedules = 0
        var intervalMs = 3_000L
        var microphoneFree = true
        private var active = false
        val watch = MicrophoneWatch(
            schedule = { delay, action ->
                schedules += 1
                clock.postDelayed(delay, action)
            },
            startCapture = {
                check(!active) { "second AudioRecord" }
                starts += 1
                active = true
            },
            stopCapture = { active = false },
            retryIntervalMs = { intervalMs },
            isCapturing = { active },
            microphoneFree = { microphoneFree },
        )
    }
}

class MicrophoneRecoveryTest {
    @Test
    fun waitingStaysDistinctFromStopped() {
        assertEquals(
            MonitoringStatus.Waiting,
            monitoringStatus(MonitoringPresence.Waiting, DetectionPhase.Active),
        )
        assertEquals(
            MonitoringStatus.Stopped,
            monitoringStatus(MonitoringPresence.Stopped, DetectionPhase.Active),
        )
        assertEquals(
            MonitoringStatus.Error,
            monitoringStatus(MonitoringPresence.Error, DetectionPhase.Idle),
        )
    }

    @Test
    fun manualStopBlocksRecovery() {
        val fixture = Fixture()
        fixture.viewModel.startMonitoring()
        fixture.capture.fail(AudioCaptureFailure.Interrupted)

        fixture.viewModel.stopMonitoring()
        fixture.clock.advanceBy(60_000)
        fixture.signals.emit()

        assertEquals(1, fixture.capture.starts)
        assertFalse(fixture.viewModel.uiState.value.monitoringEnabled)
        assertEquals(MonitoringPresence.Stopped, fixture.viewModel.uiState.value.presence)
        assertFalse(fixture.preferences.enabled)
    }

    @Test
    fun unavailableCaptureResumesFromTheRecordingCallback() {
        val fixture = Fixture()
        fixture.viewModel.startMonitoring()
        fixture.capture.fail(AudioCaptureFailure.Interrupted)

        assertEquals(MonitoringPresence.Waiting, fixture.viewModel.uiState.value.presence)
        assertTrue(fixture.viewModel.uiState.value.monitoringEnabled)
        assertFalse(fixture.viewModel.uiState.value.monitoringActive)

        fixture.signals.emit()

        assertEquals(2, fixture.capture.starts)
        assertEquals(MonitoringPresence.Monitoring, fixture.viewModel.uiState.value.presence)
        fixture.clock.advanceBy(60_000)
        assertEquals(2, fixture.capture.starts)
    }

    @Test
    fun retryIntervalIsLimitedAndHonored() {
        val fixture = Fixture()
        fixture.viewModel.onRetryIntervalChanged(0.2f)
        assertEquals(MainViewModel.MIN_RETRY_MS, fixture.viewModel.uiState.value.retryIntervalMs)
        fixture.viewModel.onRetryIntervalChanged(90f)
        assertEquals(MainViewModel.MAX_RETRY_MS, fixture.viewModel.uiState.value.retryIntervalMs)

        fixture.viewModel.onRetryIntervalChanged(10f)
        fixture.viewModel.startMonitoring()
        fixture.capture.fail(AudioCaptureFailure.Interrupted)
        fixture.clock.advanceBy(9_999)
        assertEquals(1, fixture.capture.starts)
        fixture.clock.advanceBy(1)
        assertEquals(2, fixture.capture.starts)
        assertEquals(10_000L, fixture.preferences.retryIntervalMs)
    }

    @Test
    fun revokedPermissionDoesNotRestart() {
        val fixture = Fixture()
        fixture.capture.failNext = AudioCaptureFailure.MissingPermission
        fixture.viewModel.startMonitoring()

        fixture.signals.emit()
        fixture.clock.advanceBy(60_000)

        val state = fixture.viewModel.uiState.value
        assertEquals(1, fixture.capture.starts)
        assertEquals(MonitoringPresence.Error, state.presence)
        assertTrue(state.monitoringEnabled)
        assertFalse(state.monitoringActive)
    }

    @Test
    fun microphoneUseStopsListeningUntilItIsFree() {
        val fixture = Fixture()
        fixture.viewModel.startMonitoring()
        fixture.capture.microphoneFree = false
        fixture.capture.silence()
        fixture.signals.emit()
        fixture.clock.advanceBy(30_000)

        assertEquals(1, fixture.capture.starts)
        assertFalse(fixture.capture.isCapturing())
        assertEquals(MonitoringPresence.Waiting, fixture.viewModel.uiState.value.presence)
        assertTrue(fixture.viewModel.uiState.value.monitoringEnabled)
        assertFalse(fixture.viewModel.uiState.value.monitoringActive)

        fixture.capture.microphoneFree = true
        fixture.signals.emit()

        assertEquals(2, fixture.capture.starts)
        assertEquals(MonitoringPresence.Monitoring, fixture.viewModel.uiState.value.presence)
    }

    @Test
    fun storedStartDoesNotCaptureUntilResume() {
        val preferences = MemoryMonitoringPreferences(enabled = true)
        val capture = ControllableCapture()
        val viewModel = MainViewModel(
            audioCapture = capture,
            taskRunner = ManualClock(),
            deliver = { it() },
            monitoringPreferences = preferences,
        )

        assertEquals(0, capture.starts)
        assertTrue(viewModel.wantsMonitoring())
        assertEquals(MonitoringPresence.Waiting, viewModel.uiState.value.presence)

        viewModel.resumeMonitoring()
        assertEquals(1, capture.starts)
        viewModel.resumeMonitoring()
        assertEquals(1, capture.starts)
    }

    private class Fixture {
        val clock = ManualClock()
        val capture = ControllableCapture()
        val signals = CountingSignals()
        val preferences = MemoryMonitoringPreferences()
        val viewModel = MainViewModel(
            audioCapture = capture,
            taskRunner = clock,
            deliver = { it() },
            monitoringPreferences = preferences,
            recordingSignals = signals,
        )
    }

    private class CountingSignals : RecordingSignals {
        private var onChange: (() -> Unit)? = null
        var listening = false

        override fun ensureListening(onChange: () -> Unit) {
            if (listening) {
                return
            }
            listening = true
            this.onChange = onChange
        }

        override fun stopListening() {
            listening = false
            onChange = null
        }

        fun emit() {
            onChange?.invoke()
        }
    }

    private class ControllableCapture : AudioCapture {
        var starts = 0
        var failNext: AudioCaptureFailure? = null
        var microphoneFree = true
        private var listener: AudioCaptureListener? = null
        private var capturing = false

        override fun start(listener: AudioCaptureListener) {
            check(!capturing) { "second AudioRecord" }
            starts += 1
            val failure = failNext
            if (failure != null) {
                failNext = null
                listener.onFailure(failure)
                return
            }
            capturing = true
            this.listener = listener
            listener.onStarted()
        }

        override fun stop() {
            capturing = false
            listener = null
        }

        override fun isCapturing(): Boolean = capturing

        override fun isMicrophoneFree(): Boolean = microphoneFree

        fun fail(failure: AudioCaptureFailure) {
            val current = listener ?: return
            capturing = false
            listener = null
            current.onFailure(failure)
        }

        fun silence() {
            listener?.onClientSilenced()
        }

        fun audible() {
            listener?.onClientAudible()
        }
    }
}
