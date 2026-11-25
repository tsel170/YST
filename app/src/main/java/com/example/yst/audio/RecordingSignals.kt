package com.example.yst.audio

import android.content.Context
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

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
    private var modeListener: Any? = null

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modeListener = CallModeSignals.register(
                manager,
                Executor { command -> handler.post(command) },
                onChange,
            )
        }
    }

    override fun stopListening() {
        val registered = callback ?: return
        callback = null
        manager?.unregisterAudioRecordingCallback(registered)
        val audioManager = manager
        val listener = modeListener
        modeListener = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && audioManager != null && listener != null) {
            CallModeSignals.unregister(audioManager, listener)
        }
    }
}

private object CallModeSignals {
    fun register(manager: AudioManager, executor: Executor, onChange: () -> Unit): Any {
        val listener = AudioManager.OnModeChangedListener { onChange() }
        manager.addOnModeChangedListener(executor, listener)
        return listener
    }

    fun unregister(manager: AudioManager, listener: Any) {
        manager.removeOnModeChangedListener(listener as AudioManager.OnModeChangedListener)
    }
}
