package com.example.yst.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.example.yst.detection.SpeechFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Captures microphone audio on a background thread and drops every frame after
 * the level and speech flag are delivered. Nothing is written to disk or sent
 * off the device.
 */
class AudioMonitor(
    private val context: Context,
) : AudioCapture {
    private val _levelDbFs = MutableStateFlow<Float?>(null)
    val levelDbFs: StateFlow<Float?> = _levelDbFs.asStateFlow()

    private val nextSessionId = AtomicInteger(0)
    private val activeSessionId = AtomicInteger(0)
    private var session: CaptureSession? = null

    override fun isCapturing(): Boolean = activeSessionId.get() != 0

    override fun onRecordingConfigurationChanged() {
        val captureSession = session ?: return
        val listener = captureSession.listener
        if (!isCurrent(captureSession)) {
            return
        }
        if (isClientSilenced(captureSession.recorder)) {
            listener.onClientSilenced()
        } else if (captureSession.recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            listener.onClientAudible()
        } else {
            reportFailure(captureSession, listener, AudioCaptureFailure.Interrupted)
        }
    }

    override fun start(listener: AudioCaptureListener) {
        if (activeSessionId.get() != 0) {
            val running = session
            if (running != null && isCurrent(running)) {
                if (isClientSilenced(running.recorder)) {
                    listener.onClientSilenced()
                } else {
                    listener.onStarted()
                }
            }
            return
        }
        if (!hasMicrophonePermission()) {
            listener.onFailure(AudioCaptureFailure.MissingPermission)
            return
        }
        val audioRecord = openRecorder()
        if (audioRecord == null) {
            listener.onFailure(AudioCaptureFailure.InitializationFailed)
            return
        }
        val classifier = openClassifier()
        if (classifier == null) {
            releaseRecorder(audioRecord)
            listener.onFailure(AudioCaptureFailure.InitializationFailed)
            return
        }
        val sessionId = nextSessionId.incrementAndGet()
        val captureSession = CaptureSession(sessionId, audioRecord, classifier, listener)
        session = captureSession
        activeSessionId.set(sessionId)
        val thread = Thread(
            { captureLoop(captureSession, listener) },
            "AudioMonitor",
        )
        captureSession.worker = thread
        thread.start()
        listener.onStarted()
    }

    override fun stop() {
        val captureSession = session
        val sessionId = captureSession?.id ?: return
        if (!activeSessionId.compareAndSet(sessionId, 0)) {
            return
        }
        captureSession.stopRequested.set(true)
        try {
            captureSession.recorder.stop()
        } catch (_: IllegalStateException) {
            // The recorder can already be stopped after an interruption.
        }
        val thread = captureSession.worker
        if (thread != null && thread !== Thread.currentThread()) {
            thread.join(JOIN_TIMEOUT_MS)
        }
        if (session === captureSession) {
            session = null
        }
    }

    private fun captureLoop(captureSession: CaptureSession, listener: AudioCaptureListener) {
        val frame = ShortArray(AudioLevels.FRAME_SAMPLES)
        var framesSinceSilenceCheck = 0
        var clientSilenced = false
        try {
            while (isCurrent(captureSession)) {
                framesSinceSilenceCheck += 1
                if (framesSinceSilenceCheck >= SILENCE_CHECK_FRAMES) {
                    framesSinceSilenceCheck = 0
                    val nowSilenced = isClientSilenced(captureSession.recorder)
                    if (nowSilenced != clientSilenced && isCurrent(captureSession)) {
                        clientSilenced = nowSilenced
                        if (clientSilenced) {
                            listener.onClientSilenced()
                        } else {
                            listener.onClientAudible()
                        }
                    } else {
                        clientSilenced = nowSilenced
                    }
                    if (
                        !clientSilenced &&
                        captureSession.recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING &&
                        isCurrent(captureSession)
                    ) {
                        reportFailure(captureSession, listener, AudioCaptureFailure.Interrupted)
                        break
                    }
                }
                var offset = 0
                var readFailed = false
                while (offset < frame.size && isCurrent(captureSession)) {
                    val count = captureSession.recorder.read(frame, offset, frame.size - offset)
                    if (count < 0) {
                        reportFailure(captureSession, listener, AudioCaptureFailure.Interrupted)
                        readFailed = true
                        break
                    }
                    if (count == 0) {
                        sleepBriefly(captureSession)
                        break
                    }
                    offset += count
                }
                if (readFailed || !isCurrent(captureSession)) {
                    break
                }
                if (offset < frame.size || clientSilenced) {
                    continue
                }
                val level = AudioLevels.dbfs(frame)
                if (isCurrent(captureSession)) {
                    _levelDbFs.value = level
                }
                val speech = try {
                    captureSession.classifier.isSpeech(frame)
                } catch (_: IllegalArgumentException) {
                    reportFailure(captureSession, listener, AudioCaptureFailure.Interrupted)
                    break
                } catch (_: IllegalStateException) {
                    reportFailure(captureSession, listener, AudioCaptureFailure.Interrupted)
                    break
                }
                if (!isCurrent(captureSession)) {
                    break
                }
                listener.onFrame(
                    SpeechFrame(
                        timestampMs = SystemClock.elapsedRealtime(),
                        levelDbFs = level,
                        isSpeech = speech,
                    ),
                )
            }
        } finally {
            captureSession.release()
            if (activeSessionId.compareAndSet(captureSession.id, 0) && session === captureSession) {
                session = null
            }
            if (activeSessionId.get() == 0) {
                _levelDbFs.value = null
            }
        }
    }

    private fun isCurrent(captureSession: CaptureSession): Boolean {
        return activeSessionId.get() == captureSession.id && !captureSession.stopRequested.get()
    }

    private fun reportFailure(
        captureSession: CaptureSession,
        listener: AudioCaptureListener,
        failure: AudioCaptureFailure,
    ) {
        if (isCurrent(captureSession)) {
            listener.onFailure(failure)
        }
    }

    private fun sleepBriefly(captureSession: CaptureSession) {
        try {
            Thread.sleep(EMPTY_READ_PAUSE_MS)
        } catch (_: InterruptedException) {
            captureSession.stopRequested.set(true)
        }
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            AudioLevels.SAMPLE_RATE_HZ,
            CHANNEL,
            ENCODING,
        )
        if (minBuffer <= 0) {
            return null
        }
        val bufferSize = maxOf(minBuffer, AudioLevels.FRAME_SAMPLES * BYTES_PER_SAMPLE * BUFFER_MULTIPLIER)
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                AudioLevels.SAMPLE_RATE_HZ,
                CHANNEL,
                ENCODING,
                bufferSize,
            )
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: SecurityException) {
            return null
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            releaseRecorder(audioRecord)
            return null
        }
        return try {
            audioRecord.startRecording()
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                releaseRecorder(audioRecord)
                null
            } else {
                audioRecord
            }
        } catch (_: IllegalStateException) {
            releaseRecorder(audioRecord)
            null
        }
    }

    private fun openClassifier(): WebRtcSpeechClassifier? {
        return try {
            WebRtcSpeechClassifier()
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: UnsatisfiedLinkError) {
            null
        }
    }

    private fun isClientSilenced(audioRecord: AudioRecord): Boolean {
        val manager = context.getSystemService(AudioManager::class.java) ?: return false
        val sessionId = audioRecord.audioSessionId
        return manager.activeRecordingConfigurations.any { configuration ->
            configuration.clientAudioSessionId == sessionId && configuration.isClientSilenced
        }
    }

    private fun releaseRecorder(audioRecord: AudioRecord) {
        try {
            if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop()
            }
        } catch (_: IllegalStateException) {
            // Already stopped.
        }
        audioRecord.release()
    }

    private class CaptureSession(
        val id: Int,
        val recorder: AudioRecord,
        val classifier: WebRtcSpeechClassifier,
        val listener: AudioCaptureListener,
    ) {
        val stopRequested = AtomicBoolean(false)
        var worker: Thread? = null
        private val released = AtomicBoolean(false)

        fun release() {
            if (!released.compareAndSet(false, true)) {
                return
            }
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (_: IllegalStateException) {
                // Already stopped.
            }
            recorder.release()
            try {
                classifier.close()
            } catch (_: IllegalArgumentException) {
                // VadWebRTC.close throws when the native session is already closed.
            }
        }
    }

    private companion object {
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val BUFFER_MULTIPLIER = 4
        const val SILENCE_CHECK_FRAMES = 10
        const val EMPTY_READ_PAUSE_MS = 5L
        const val JOIN_TIMEOUT_MS = 1_500L
    }
}
