package com.example.yst.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.yst.audio.AudioCapture
import com.example.yst.audio.AudioCaptureFailure
import com.example.yst.audio.AudioCaptureListener
import com.example.yst.detection.Cancellable
import com.example.yst.detection.DetectionController
import com.example.yst.detection.DetectionPhase
import com.example.yst.detection.DetectionSettings
import com.example.yst.detection.ResponsePlayer
import com.example.yst.detection.RaritySelector
import com.example.yst.detection.SoundClip
import com.example.yst.detection.SpeechDetector
import com.example.yst.detection.SpeechFrame
import com.example.yst.detection.TaskRunner
import com.example.yst.sounds.CollectionConfig
import com.example.yst.sounds.ManagedSound
import com.example.yst.sounds.MemoryCollectionConfig
import com.example.yst.sounds.MemorySoundLibrary
import com.example.yst.sounds.RarityTier
import com.example.yst.sounds.SoundCollectionEditor
import com.example.yst.sounds.SoundLibrary
import com.example.yst.sounds.defaultRarities
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class UserNotice {
    ImportFailed,
    PermissionDenied,
    CaptureFailed,
    CaptureInterrupted,
    BackgroundCaptureUnavailable,
}

data class MainUiState(
    val settings: DetectionSettings = DetectionSettings(),
    val phase: DetectionPhase = DetectionPhase.Idle,
    val monitoringActive: Boolean = false,
    val levelDbFs: Float? = null,
    val lastPlayedSound: String? = null,
    val nextResponseIntervalMs: Long? = null,
    val sounds: List<SoundClip> = emptyList(),
    val managedSounds: List<ManagedSound> = emptyList(),
    val rarities: List<RarityTier> = defaultRarities(),
)

class MainViewModel(
    private val audioCapture: AudioCapture,
    private val taskRunner: TaskRunner,
    private val deliver: (() -> Unit) -> Unit,
    private val soundLibrary: SoundLibrary = MemorySoundLibrary(),
    private val player: ResponsePlayer = IdleResponsePlayer,
    private val releasePlayer: () -> Unit = {},
    private val importFromDevice: (List<Uri>) -> List<SoundClip> = { emptyList() },
    private val ownedByProcess: Boolean = false,
    bundledSounds: () -> List<SoundClip> = { emptyList() },
    collectionConfig: CollectionConfig = MemoryCollectionConfig(),
    private val playable: (SoundClip) -> Boolean = { true },
) : ViewModel() {
    private val collection = SoundCollectionEditor(
        bundled = bundledSounds,
        imported = soundLibrary,
        config = collectionConfig,
    )
    private val raritySelector = RaritySelector()
    private var settings = DetectionSettings()
    private var sounds: List<SoundClip> = emptyList()
    private var managedSounds: List<ManagedSound> = emptyList()
    private var rarities: List<RarityTier> = defaultRarities()
    private var lastPlayedSound: String? = null
    private var monitoringActive = false
    private var startRequested = false
    private var levelDbFs: Float? = null
    private var phase = DetectionPhase.Idle
    private var generation = 0
    private var session: ListeningSession? = null

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UserNotice>(extraBufferCapacity = 8)
    val messages: SharedFlow<UserNotice> = _messages.asSharedFlow()

    init {
        reloadSounds()
    }

    fun onThresholdChanged(decibels: Float) {
        updateSettings { it.copy(volumeThresholdDbFs = decibels.coerceIn(MIN_DB, MAX_DB)) }
    }

    fun onSpeechDurationChanged(milliseconds: Float) {
        updateSettings {
            it.copy(requiredLoudSpeechMs = milliseconds.coerceIn(MIN_SPEECH_MS, MAX_SPEECH_MS).toLong())
        }
    }

    fun onSilenceTimeoutChanged(milliseconds: Float) {
        updateSettings {
            it.copy(silenceTimeoutMs = milliseconds.coerceIn(MIN_SILENCE_MS, MAX_SILENCE_MS).toLong())
        }
    }

    fun onMinIntervalChanged(seconds: Float) {
        updateSettings { current ->
            val minimum = seconds.coerceIn(MIN_INTERVAL_S, MAX_INTERVAL_S).toMillis()
            current.copy(
                minResponseIntervalMs = minimum,
                maxResponseIntervalMs = current.maxResponseIntervalMs.coerceAtLeast(minimum),
            )
        }
    }

    fun onMaxIntervalChanged(seconds: Float) {
        updateSettings { current ->
            val maximum = seconds.coerceIn(MIN_INTERVAL_S, MAX_INTERVAL_S).toMillis()
            current.copy(
                minResponseIntervalMs = current.minResponseIntervalMs.coerceAtMost(maximum),
                maxResponseIntervalMs = maximum,
            )
        }
    }

    fun addSounds(clips: List<SoundClip>) {
        if (clips.isEmpty()) {
            return
        }
        soundLibrary.add(clips)
        reloadSounds()
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) {
            return
        }
        val updated = importFromDevice(uris)
        reloadSounds()
        val imported = uris.any { uri -> updated.any { it.id == uri.toString() } || sounds.any { it.id == uri.toString() } }
        if (!imported) {
            _messages.tryEmit(UserNotice.ImportFailed)
        }
    }

    fun removeSound(id: String) {
        collection.remove(id)
        reloadSounds()
    }

    fun assignRarity(soundId: String, rarityId: String) {
        if (collection.assign(soundId, rarityId)) {
            reloadSounds()
        }
    }

    fun renameRarity(id: String, name: String) {
        if (collection.renameRarity(id, name)) {
            reloadSounds()
        }
    }

    fun setRarityProbability(id: String, percent: Int) {
        if (collection.setProbability(id, percent)) {
            reloadSounds()
        }
    }

    fun addRarity(name: String) {
        collection.addRarity(name)
        reloadSounds()
    }

    fun deleteRarity(id: String) {
        if (collection.deleteRarity(id)) {
            reloadSounds()
        }
    }

    fun restoreDefaultRarities() {
        collection.restoreDefaultRarities()
        reloadSounds()
    }

    fun onPermissionDenied() {
        _messages.tryEmit(UserNotice.PermissionDenied)
    }

    fun startMonitoring() {
        if (monitoringActive || startRequested) {
            return
        }
        startRequested = true
        val currentGeneration = ++generation
        val trackedRunner = TrackingTaskRunner(taskRunner)
        val controller = DetectionController(
            detector = SpeechDetector(settings),
            player = playback,
            clips = { sounds },
            settings = { settings },
            runner = trackedRunner,
            chooseClip = { previousId, random ->
                val snapshot = collection.snapshot(playable)
                raritySelector.pick(
                    sounds = snapshot.playableSelections(),
                    rarities = snapshot.rarities,
                    previousId = previousId,
                    random = random,
                )
            },
        )
        session = ListeningSession(controller, trackedRunner)
        audioCapture.start(
            object : AudioCaptureListener {
                override fun onStarted() {
                    deliver {
                        if (currentGeneration != generation) {
                            return@deliver
                        }
                        monitoringActive = true
                        phase = controller.phase
                        publish()
                    }
                }

                override fun onFrame(frame: SpeechFrame) {
                    deliver {
                        if (currentGeneration != generation || !monitoringActive) {
                            return@deliver
                        }
                        levelDbFs = frame.levelDbFs
                        phase = controller.onFrame(frame).phase
                        publish()
                    }
                }

                override fun onFailure(failure: AudioCaptureFailure) {
                    deliver {
                        if (currentGeneration != generation) {
                            return@deliver
                        }
                        val notice = when (failure) {
                            AudioCaptureFailure.MissingPermission -> UserNotice.PermissionDenied
                            AudioCaptureFailure.InitializationFailed -> UserNotice.CaptureFailed
                            AudioCaptureFailure.Interrupted -> UserNotice.CaptureInterrupted
                        }
                        stopMonitoring(notice)
                    }
                }
            },
        )
    }

    fun stopMonitoring() {
        stopMonitoring(notice = null)
    }

    fun onAppBackgrounded() {
        if (!monitoringActive) {
            return
        }
        stopMonitoring(UserNotice.BackgroundCaptureUnavailable)
    }

    override fun onCleared() {
        if (!ownedByProcess) {
            stopMonitoring()
            releasePlayer()
        }
        super.onCleared()
    }

    private fun stopMonitoring(notice: UserNotice?) {
        startRequested = false
        generation += 1
        session?.runner?.cancelAll()
        session = null
        monitoringActive = false
        levelDbFs = null
        phase = DetectionPhase.Idle
        audioCapture.stop()
        publish()
        if (notice != null) {
            _messages.tryEmit(notice)
        }
    }

    private fun updateSettings(transform: (DetectionSettings) -> DetectionSettings) {
        settings = transform(settings).sanitized()
        publish()
    }

    private fun reloadSounds() {
        val snapshot = collection.snapshot(playable)
        managedSounds = snapshot.sounds
        rarities = snapshot.rarities
        sounds = snapshot.sounds.map { it.clip }
        publish()
    }

    private fun publish() {
        _uiState.value = MainUiState(
            settings = settings,
            phase = if (monitoringActive) phase else DetectionPhase.Idle,
            monitoringActive = monitoringActive,
            levelDbFs = if (monitoringActive) levelDbFs else null,
            lastPlayedSound = lastPlayedSound,
            nextResponseIntervalMs = null,
            sounds = sounds,
            managedSounds = managedSounds,
            rarities = rarities,
        )
    }

    private val playback = object : ResponsePlayer {
        override val isPlaying: Boolean
            get() = player.isPlaying

        override fun setOnCompletionListener(listener: () -> Unit) {
            player.setOnCompletionListener(listener)
        }

        override fun play(clip: SoundClip) {
            lastPlayedSound = clip.displayName
            player.play(clip)
            publish()
        }
    }

    private fun Float.toMillis(): Long = (this * 1_000f).toLong()

    private class ListeningSession(
        val controller: DetectionController,
        val runner: TrackingTaskRunner,
    )

    private class TrackingTaskRunner(
        private val delegate: TaskRunner,
    ) : TaskRunner {
        private val tasks = mutableListOf<Cancellable>()

        override fun postDelayed(delayMs: Long, action: () -> Unit): Cancellable {
            lateinit var handle: Cancellable
            handle = delegate.postDelayed(delayMs) {
                tasks.remove(handle)
                action()
            }
            tasks += handle
            return handle
        }

        fun cancelAll() {
            tasks.toList().forEach { it.cancel() }
            tasks.clear()
        }
    }

    private object IdleResponsePlayer : ResponsePlayer {
        override val isPlaying: Boolean = false

        override fun play(clip: SoundClip) = Unit

        override fun setOnCompletionListener(listener: () -> Unit) = Unit
    }

    companion object {
        const val MIN_DB = -60f
        const val MAX_DB = 90f
        const val MIN_SPEECH_MS = 200f
        const val MAX_SPEECH_MS = 5_000f
        const val MIN_SILENCE_MS = 200f
        const val MAX_SILENCE_MS = 8_000f
        const val MIN_INTERVAL_S = 1f
        const val MAX_INTERVAL_S = 60f
    }
}
