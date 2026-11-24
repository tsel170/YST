package com.example.yst.ui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.yst.audio.AudioMonitor
import com.example.yst.audio.HandlerTaskRunner
import com.example.yst.playback.SoundPlayer
import com.example.yst.sounds.BundledRawSounds
import com.example.yst.sounds.MergedSoundLibrary
import com.example.yst.sounds.PersistedSoundLibrary

class MainViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (!modelClass.isAssignableFrom(MainViewModel::class.java)) {
            error("Unknown ViewModel: ${modelClass.name}")
        }
        val handler = Handler(Looper.getMainLooper())
        val imported = PersistedSoundLibrary(application)
        val sounds = MergedSoundLibrary(
            bundled = { BundledRawSounds.list(application) },
            persisted = imported,
        )
        val soundPlayer = SoundPlayer(application)
        @Suppress("UNCHECKED_CAST")
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
        ) as T
    }
}
