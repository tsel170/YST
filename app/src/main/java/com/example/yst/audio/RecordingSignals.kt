package com.example.yst.audio

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Handler
import android.os.Looper

fun interface RecordingSignals {
    fun ensureListening(onChange: () -> Unit)

    fun stopListening() {}
}

object IdleRecordingSignals : RecordingSignals {
    override fun ensureListening(onChange: () -> Unit) = Unit
}

/**
 * One recording-configuration callback. A change means some client started,
 * stopped, or was silenced. It does not by itself mean the microphone is free.
 */
class AndroidRecordingSignals(
    context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : RecordingSignals {
    private val manager = context.applicationContext.getSystemService(AudioManager::class.java)
    private var callback: AudioManager.AudioRecordingCallback? = null

    override fun ensureListening(onChange: () -> Unit) {
        if (callback != null || manager == null) {
            return
        }
        val registered = object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                onChange()
            }
        }
        callback = registered
        manager.registerAudioRecordingCallback(registered, handler)
    }

    override fun stopListening() {
        val registered = callback ?: return
        callback = null
        manager?.unregisterAudioRecordingCallback(registered)
    }
}
