package com.example.yst.detection

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionControllerTest {
    private val alpha = SoundClip(id = "alpha", displayName = "Alpha")
    private val beta = SoundClip(id = "beta", displayName = "Beta")

    @Test
    fun playsImmediatelyWhenCycleStartsAndAgainAfterInterval() {
        val harness = Harness(minIntervalMs = 10_000L, maxIntervalMs = 10_000L)

        harness.loudUntil(1_200L)
        harness.player.finish()
        harness.controller.onFrame(SpeechFrame(timestampMs = 1_400L, levelDbFs = -10f, isSpeech = true))

        assertEquals(listOf("alpha"), harness.player.playedIds)
        harness.clock.advanceBy(10_000L)
        assertEquals(listOf("alpha", "beta"), harness.player.playedIds)
    }

    @Test
    fun volumeDipDoesNotResetResponseTimer() {
        val harness = Harness(minIntervalMs = 10_000L, maxIntervalMs = 10_000L)
        harness.loudUntil(1_200L)
        harness.player.finish()

        harness.controller.onFrame(SpeechFrame(timestampMs = 4_000L, levelDbFs = -70f, isSpeech = true))
        harness.controller.onFrame(SpeechFrame(timestampMs = 4_200L, levelDbFs = -70f, isSpeech = false))
        harness.controller.onFrame(SpeechFrame(timestampMs = 4_400L, levelDbFs = -10f, isSpeech = true))
        harness.clock.advanceBy(9_999L)

        assertEquals(listOf("alpha"), harness.player.playedIds)
        harness.clock.advanceBy(1L)
        assertEquals(listOf("alpha", "beta"), harness.player.playedIds)
    }

    @Test
    fun doesNotOverlapPlayback() {
        val harness = Harness(minIntervalMs = 10_000L, maxIntervalMs = 10_000L)
        harness.loudUntil(1_200L)

        harness.clock.advanceBy(10_000L)
        assertEquals(listOf("alpha"), harness.player.playedIds)

        harness.player.finish()
        harness.controller.onFrame(SpeechFrame(timestampMs = 1_400L, levelDbFs = -10f, isSpeech = true))
        assertEquals(listOf("alpha"), harness.player.playedIds)
        harness.clock.advanceBy(10_000L)
        assertEquals(listOf("alpha", "beta"), harness.player.playedIds)
    }

    @Test
    fun shortIntervalDoesNotKeepPlayingAfterSpeechStops() {
        val harness = Harness(minIntervalMs = 1_000L, maxIntervalMs = 1_000L)
        harness.loudUntil(1_200L)
        harness.player.finish()

        harness.controller.onFrame(SpeechFrame(timestampMs = 1_400L, levelDbFs = -60f, isSpeech = false))
        harness.clock.advanceBy(1_000L)
        assertEquals(listOf("alpha"), harness.player.playedIds)

        harness.controller.onFrame(SpeechFrame(timestampMs = 2_900L, levelDbFs = -60f, isSpeech = false))
        harness.clock.advanceBy(10_000L)

        assertEquals(listOf("alpha"), harness.player.playedIds)
        assertEquals(DetectionPhase.Idle, harness.controller.phase)
    }

    @Test
    fun speakerAudioDoesNotCountAsContinuedSpeech() {
        val harness = Harness(minIntervalMs = 1_000L, maxIntervalMs = 1_000L)
        harness.loudUntil(1_200L)
        harness.controller.onFrame(SpeechFrame(timestampMs = 2_000L, levelDbFs = -5f, isSpeech = true))
        harness.player.finish()
        harness.controller.onFrame(SpeechFrame(timestampMs = 2_200L, levelDbFs = -60f, isSpeech = false))
        harness.controller.onFrame(SpeechFrame(timestampMs = 3_700L, levelDbFs = -60f, isSpeech = false))
        harness.clock.advanceBy(10_000L)

        assertEquals(listOf("alpha"), harness.player.playedIds)
        assertEquals(DetectionPhase.Idle, harness.controller.phase)
    }

    @Test
    fun stopPreventsAnotherFileAfterTheCurrentOne() {
        val harness = Harness(minIntervalMs = 1_000L, maxIntervalMs = 1_000L)
        harness.loudUntil(1_200L)
        harness.controller.stop()
        harness.player.finish()
        harness.controller.onFrame(SpeechFrame(timestampMs = 2_000L, levelDbFs = -10f, isSpeech = true))
        harness.clock.advanceBy(10_000L)

        assertEquals(listOf("alpha"), harness.player.playedIds)
    }

    @Test
    fun endingCycleCancelsPendingResponse() {
        val harness = Harness(minIntervalMs = 10_000L, maxIntervalMs = 10_000L)
        harness.loudUntil(1_200L)
        harness.player.finish()

        harness.controller.onFrame(SpeechFrame(timestampMs = 2_000L, levelDbFs = -60f, isSpeech = false))
        harness.controller.onFrame(SpeechFrame(timestampMs = 3_500L, levelDbFs = -60f, isSpeech = false))
        harness.clock.advanceBy(10_000L)

        assertEquals(listOf("alpha"), harness.player.playedIds)
        assertEquals(DetectionPhase.Idle, harness.controller.phase)
    }

    @Test
    fun playbackFinishingAfterCycleEndDoesNotStartAnotherResponse() {
        val harness = Harness(minIntervalMs = 10_000L, maxIntervalMs = 10_000L)
        harness.loudUntil(1_200L)
        harness.controller.stop()
        harness.player.finish()

        assertEquals(listOf("alpha"), harness.player.playedIds)
    }

    @Test
    fun scheduledDelayStaysInsideConfiguredRange() {
        val captured = mutableListOf<Long>()
        val runner = object : TaskRunner {
            override fun postDelayed(delayMs: Long, action: () -> Unit): Cancellable {
                captured += delayMs
                return Cancellable { }
            }
        }
        val player = FakePlayer()
        val controller = DetectionController(
            player = player,
            clips = { listOf(alpha, beta) },
            settings = {
                DetectionSettings(
                    minResponseIntervalMs = 10_000L,
                    maxResponseIntervalMs = 20_000L,
                )
            },
            runner = runner,
            random = Random(7),
        )

        controller.onFrame(SpeechFrame(timestampMs = 0L, levelDbFs = -10f, isSpeech = true))
        controller.onFrame(SpeechFrame(timestampMs = 1_200L, levelDbFs = -10f, isSpeech = true))
        player.finish()

        assertTrue(captured.single() in 10_000L..20_000L)
    }

    private class Harness(
        minIntervalMs: Long,
        maxIntervalMs: Long,
    ) {
        val clock = ManualClock()
        val player = FakePlayer()
        val controller = DetectionController(
            player = player,
            clips = {
                listOf(
                    SoundClip(id = "alpha", displayName = "Alpha"),
                    SoundClip(id = "beta", displayName = "Beta"),
                )
            },
            settings = {
                DetectionSettings(
                    minResponseIntervalMs = minIntervalMs,
                    maxResponseIntervalMs = maxIntervalMs,
                )
            },
            runner = clock,
            random = object : Random() {
                override fun nextBits(bitCount: Int): Int = 0
            },
        )

        fun loudUntil(timestampMs: Long) {
            controller.onFrame(SpeechFrame(timestampMs = 0L, levelDbFs = -10f, isSpeech = true))
            controller.onFrame(SpeechFrame(timestampMs = timestampMs, levelDbFs = -10f, isSpeech = true))
        }
    }

    private class FakePlayer : ResponsePlayer {
        val playedIds = mutableListOf<String>()
        override var isPlaying: Boolean = false
        private var onCompletion: () -> Unit = {}

        override fun play(clip: SoundClip) {
            check(!isPlaying) { "Playback overlapped" }
            isPlaying = true
            playedIds += clip.id
        }

        override fun setOnCompletionListener(listener: () -> Unit) {
            onCompletion = listener
        }

        fun finish() {
            check(isPlaying)
            isPlaying = false
            onCompletion()
        }
    }
}
