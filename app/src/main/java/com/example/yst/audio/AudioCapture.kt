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
}

interface AudioCapture {
    fun start(listener: AudioCaptureListener)

    fun stop()
}
