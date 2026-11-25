package com.example.yst

import android.app.Application
import android.os.Handler
import android.os.Looper
import com.example.yst.audio.AndroidRecordingSignals
import com.example.yst.audio.AudioMonitor
import com.example.yst.audio.HandlerTaskRunner
import com.example.yst.audio.SharedMonitoringPreferences
import com.example.yst.playback.SoundPlayer
import com.example.yst.sounds.BundledRawSounds
import com.example.yst.sounds.PersistedSoundLibrary
import com.example.yst.sounds.PreferenceCollectionConfig
import com.example.yst.ui.MainViewModel
import com.example.yst.ui.MonitoringPresence

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
        val soundPlayer = SoundPlayer(application)
        return MainViewModel(
            audioCapture = AudioMonitor(application),
            taskRunner = HandlerTaskRunner(handler),
            deliver = { block -> handler.post(block) },
            soundLibrary = imported,
            player = soundPlayer,
            releasePlayer = soundPlayer::release,
            importFromDevice = imported::importUris,
            ownedByProcess = true,
            bundledSounds = { BundledRawSounds.list(application) },
            collectionConfig = PreferenceCollectionConfig(application),
            playable = imported::isReadable,
            monitoringPreferences = SharedMonitoringPreferences(application),
            recordingSignals = AndroidRecordingSignals(application, handler),
            onPresenceChanged = { presence ->
                MonitoringService.updateNotification(notificationText(application, presence))
            },
        )
    }

    private fun notificationText(application: Application, presence: MonitoringPresence): String {
        val resId = when (presence) {
            MonitoringPresence.Waiting -> R.string.monitoring_notification_waiting
            MonitoringPresence.Error -> R.string.monitoring_notification_error
            MonitoringPresence.Monitoring,
            MonitoringPresence.Stopped,
            -> R.string.monitoring_notification
        }
        return application.getString(resId)
    }
}
