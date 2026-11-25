package com.example.yst.detection

import kotlin.random.Random

/**
 * Owns one detection cycle's responses.
 *
 * The first response plays as soon as [SpeechDetector] starts a cycle. Later responses are
 * posted on [runner] and are not rescheduled when speech merely falls below the volume
 * threshold. Playback never overlaps: a response that comes due while audio is still
 * playing waits until that playback ends, then starts, and only then schedules the next gap.
 */
class DetectionController(
    private val detector: SpeechDetector = SpeechDetector(),
    private val player: ResponsePlayer,
    private val clips: () -> List<SoundClip>,
    private val settings: () -> DetectionSettings,
    private val runner: TaskRunner,
    private val random: Random = Random.Default,
    private val selector: SoundSelector = SoundSelector(),
    private val chooseClip: ((previousId: String?, random: Random) -> SoundClip?)? = null,
) {
    val phase: DetectionPhase
        get() = detector.currentPhase

    private var cycleActive = false
    private var pending: Cancellable? = null
    private var playWhenIdle = false
    private var lastClipId: String? = null

    init {
        player.setOnCompletionListener { onPlaybackFinished() }
    }

    fun onFrame(frame: SpeechFrame): DetectorStep {
        detector.updateSettings(settings())
        val step = detector.onFrame(frame)
        when {
            step.cycleStarted -> {
                cycleActive = true
                triggerResponse()
            }
            step.cycleEnded -> {
                stopScheduling()
            }
        }
        return step
    }

    private fun stopScheduling() {
        cycleActive = false
        playWhenIdle = false
        pending?.cancel()
        pending = null
    }

    private fun triggerResponse() {
        if (!cycleActive) {
            return
        }
        pending?.cancel()
        pending = null
        if (player.isPlaying) {
            playWhenIdle = true
            return
        }
        playWhenIdle = false
        val clip = chooseClip?.invoke(lastClipId, random) ?: selector.pick(clips(), lastClipId, random)
        if (clip != null) {
            lastClipId = clip.id
            player.play(clip)
        }
        scheduleNext()
    }

    private fun scheduleNext() {
        if (!cycleActive) {
            return
        }
        val current = settings().sanitized()
        val minimum = current.minResponseIntervalMs
        val maximum = current.maxResponseIntervalMs
        val delay = if (minimum >= maximum) {
            minimum
        } else {
            random.nextLong(minimum, maximum + 1)
        }
        pending = runner.postDelayed(delay) {
            pending = null
            triggerResponse()
        }
    }

    private fun onPlaybackFinished() {
        if (!playWhenIdle || !cycleActive) {
            return
        }
        triggerResponse()
    }
}
