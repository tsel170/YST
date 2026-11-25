package com.example.yst.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.example.yst.audio.AudioCapture
import com.example.yst.audio.AudioCaptureFailure
import com.example.yst.audio.AudioCaptureListener
import com.example.yst.audio.DEFAULT_RETRY_MS
import com.example.yst.audio.IdleRecordingSignals
import com.example.yst.audio.MemoryMonitoringPreferences
import com.example.yst.audio.MicrophoneWatch
import com.example.yst.audio.MonitoringPreferences
import com.example.yst.audio.RecordingSignals
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
    val monitoringEnabled: Boolean = false,
    val presence: MonitoringPresence = MonitoringPresence.Stopped,
    val retryIntervalMs: Long = DEFAULT_RETRY_MS,
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
    private val monitoringPreferences: MonitoringPreferences = MemoryMonitoringPreferences(),
    private val recordingSignals: RecordingSignals = IdleRecordingSignals,
    private val onPresenceChanged: (MonitoringPresence) -> Unit = {},
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
    private var levelDbFs: Float? = null
    private var phase = DetectionPhase.Idle
    private var generation = 0
    private var session: ListeningSession? = null
    private var signalsAttached = false
    private var announcedOutage = false
    private val watch = MicrophoneWatch(
        schedule = { delayMs, action -> taskRunner.postDelayed(delayMs, action) },
        startCapture = { startCapture() },
        stopCapture = { audioCapture.stop() },
        retryIntervalMs = { monitoringPreferences.retryIntervalMs.coerceIn(MIN_RETRY_MS, MAX_RETRY_MS) },
        isCapturing = { audioCapture.isCapturing() },
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<UserNotice>(extraBufferCapacity = 8)
    val messages: SharedFlow<UserNotice> = _messages.asSharedFlow()

    init {
        watch.restoreEnabledWithoutStarting(monitoringPreferences.enabled)
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

    fun onRetryIntervalChanged(seconds: Float) {
        val millis = (seconds.coerceIn(MIN_RETRY_S, MAX_RETRY_S) * 1_000f).toLong()
        monitoringPreferences.retryIntervalMs = millis
        publish()
    }

    fun onPermissionDenied() {
        if (!watch.enabled) {
            return
        }
        val alreadyFailed = watch.presence == MonitoringPresence.Error
        watch.onPermissionRevoked()
        if (!alreadyFailed) {
            _messages.tryEmit(UserNotice.PermissionDenied)
        }
        publish()
    }

    fun onPermissionGranted() {
        if (!watch.enabled) {
            return
        }
        watch.enable()
        publish()
    }

    fun requestMonitoring() {
        monitoringPreferences.enabled = true
        watch.restoreEnabledWithoutStarting(true)
        publish()
    }

    fun startMonitoring() {
        monitoringPreferences.enabled = true
        resumeMonitoring()
    }

    fun resumeMonitoring() {
        if (!watch.enabled && !monitoringPreferences.enabled) {
            return
        }
        if (!watch.enabled) {
            watch.restoreEnabledWithoutStarting(true)
        }
        attachSignals()
        watch.enable()
        publish()
    }

    fun wantsMonitoring(): Boolean = watch.enabled

    fun onRecordingConfigurationChanged() {
        audioCapture.onRecordingConfigurationChanged()
        deliver {
            if (!watch.enabled || !signalsAttached) {
                return@deliver
            }
            watch.onMicrophoneAvailable()
            publish()
        }
    }

    fun stopMonitoring() {
        monitoringPreferences.enabled = false
        announcedOutage = false
        detachSignals()
        watch.disable()
        releaseSession()
        publish()
    }

    fun onServiceLost() {
        detachSignals()
        watch.onHostLost()
        levelDbFs = null
        publish()
    }

    fun onForegroundStartDenied() {
        if (watch.enabled) {
            _messages.tryEmit(UserNotice.BackgroundCaptureUnavailable)
        }
        onServiceLost()
    }

    override fun onCleared() {
        if (!ownedByProcess) {
            stopMonitoring()
            releasePlayer()
        }
        super.onCleared()
    }

    private fun releaseSession() {
        generation += 1
        session?.controller?.stop()
        playback.stop()
        session?.runner?.cancelAll()
        session = null
        levelDbFs = null
        phase = DetectionPhase.Idle
        audioCapture.stop()
    }

    private fun attachSignals() {
        if (signalsAttached) {
            return
        }
        signalsAttached = true
        recordingSignals.ensureListening { onRecordingConfigurationChanged() }
    }

    private fun detachSignals() {
        if (!signalsAttached) {
            return
        }
        signalsAttached = false
        recordingSignals.stopListening()
    }

    private fun startCapture() {
        val listening = ensureSession()
        audioCapture.start(captureListener(listening))
    }

    private fun ensureSession(): ListeningSession {
        session?.let { return it }
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
        val created = ListeningSession(controller, trackedRunner)
        session = created
        return created
    }

    private fun captureListener(listening: ListeningSession) = object : AudioCaptureListener {
        override fun onStarted() {
            deliver {
                if (!sameSession(listening)) {
                    return@deliver
                }
                announcedOutage = false
                watch.onCaptureStarted()
                phase = listening.controller.phase
                publish()
            }
        }

        override fun onFrame(frame: SpeechFrame) {
            deliver {
                if (!sameSession(listening) || watch.presence != MonitoringPresence.Monitoring) {
                    return@deliver
                }
                levelDbFs = frame.levelDbFs
                phase = listening.controller.onFrame(frame).phase
                publish()
            }
        }

        override fun onFailure(failure: AudioCaptureFailure) {
            deliver {
                if (!sameSession(listening)) {
                    return@deliver
                }
                when (failure) {
                    AudioCaptureFailure.MissingPermission -> onPermissionDenied()
                    AudioCaptureFailure.InitializationFailed -> {
                        watch.onTemporarilyUnavailable()
                        announceOutage(UserNotice.CaptureFailed)
                        publish()
                    }
                    AudioCaptureFailure.Interrupted -> {
                        watch.onTemporarilyUnavailable()
                        announceOutage(UserNotice.CaptureInterrupted)
                        publish()
                    }
                }
            }
        }

        override fun onClientSilenced() {
            deliver {
                if (!sameSession(listening)) {
                    return@deliver
                }
                levelDbFs = null
                watch.onSilenced()
                publish()
            }
        }

        override fun onClientAudible() {
            deliver {
                if (!sameSession(listening)) {
                    return@deliver
                }
                watch.onSilenceCleared()
                publish()
            }
        }
    }

    private fun sameSession(listening: ListeningSession): Boolean {
        return watch.enabled && session === listening
    }

    private fun announceOutage(notice: UserNotice) {
        if (announcedOutage) {
            return
        }
        announcedOutage = true
        _messages.tryEmit(notice)
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
        val presence = watch.presence
        val captureRunning = presence == MonitoringPresence.Monitoring
        _uiState.value = MainUiState(
            settings = settings,
            phase = if (captureRunning) phase else DetectionPhase.Idle,
            monitoringActive = captureRunning,
            monitoringEnabled = watch.enabled,
            presence = presence,
            retryIntervalMs = monitoringPreferences.retryIntervalMs.coerceIn(MIN_RETRY_MS, MAX_RETRY_MS),
            levelDbFs = if (captureRunning) levelDbFs else null,
            lastPlayedSound = lastPlayedSound,
            nextResponseIntervalMs = null,
            sounds = sounds,
            managedSounds = managedSounds,
            rarities = rarities,
        )
        onPresenceChanged(presence)
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

        override fun stop() {
            player.stop()
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
        const val MIN_RETRY_S = 1f
        const val MAX_RETRY_S = 30f
        const val MIN_RETRY_MS = 1_000L
        const val MAX_RETRY_MS = 30_000L
    }
}
