package com.example.yst

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.example.yst.audio.AudioMonitor
import com.example.yst.audio.HandlerTaskRunner
import com.example.yst.playback.SoundPlayer
import com.example.yst.sounds.BundledRawSounds
import com.example.yst.sounds.MergedSoundLibrary
import com.example.yst.sounds.PersistedSoundLibrary
import com.example.yst.ui.MainViewModel

/**
 * One monitoring session for the whole process. The activity can close without
 * discarding the microphone, the detector, or playback.
 */
object MonitoringRegistry {
    private val lock = Any()

    @Volatile
    private var viewModel: MainViewModel? = null

    fun isRunning(): Boolean = viewModel != null

    fun get(application: Application): MainViewModel {
        viewModel?.let { return it }
        return synchronized(lock) {
            viewModel ?: create(application).also { viewModel = it }
        }
    }

    private fun create(application: Application): MainViewModel {
        val handler = Handler(Looper.getMainLooper())
        val imported = PersistedSoundLibrary(application)
        val sounds = MergedSoundLibrary(
            bundled = { BundledRawSounds.list(application) },
            persisted = imported,
        )
        val soundPlayer = SoundPlayer(application)
        return MainViewModel(
            audioCapture = AudioMonitor(application),
            taskRunner = HandlerTaskRunner(handler),
            deliver = { block -> handler.post(block) },
            soundLibrary = sounds,
            player = soundPlayer,
            releasePlayer = soundPlayer::release,
            importFromDevice = { uris ->
                imported.importUris(uris)
                sounds.load()
            },
            ownedByProcess = true,
        )
    }
}
