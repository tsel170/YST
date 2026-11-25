package com.example.yst.audio

import com.example.yst.detection.SpeechFrame

enum class AudioCaptureFailure {
    MissingPermission,
    InitializationFailed,
    Interrupted,
}

interface AudioCaptureListener {
    fun onStarted()

    fun onFrame(frame: SpeechFrame)

    fun onFailure(failure: AudioCaptureFailure)

    fun onClientSilenced() {}

    fun onClientAudible() {}
}

interface AudioCapture {
    fun start(listener: AudioCaptureListener)

    fun stop()

    fun isCapturing(): Boolean = false

    fun onRecordingConfigurationChanged() {}
}
