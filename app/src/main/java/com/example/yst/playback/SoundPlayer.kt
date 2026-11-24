package com.example.yst.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.example.yst.detection.ResponsePlayer
import com.example.yst.detection.SoundClip

class SoundPlayer(
    context: Context,
) : ResponsePlayer {
    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null
    private var onCompletion: () -> Unit = {}

    override var isPlaying: Boolean = false
        private set

    override fun setOnCompletionListener(listener: () -> Unit) {
        onCompletion = listener
    }

    override fun play(clip: SoundClip) {
        if (isPlaying) {
            return
        }
        val uri = runCatching { Uri.parse(clip.id) }.getOrNull() ?: return
        val player = MediaPlayer()
        mediaPlayer = player
        isPlaying = true
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        player.setOnCompletionListener { finished ->
            finish(finished)
        }
        player.setOnErrorListener { failed, _, _ ->
            finish(failed)
            true
        }
        try {
            player.setDataSource(appContext, uri)
            player.setOnPreparedListener { prepared ->
                if (mediaPlayer !== prepared) {
                    prepared.release()
                    return@setOnPreparedListener
                }
                prepared.start()
            }
            player.prepareAsync()
        } catch (_: Exception) {
            finish(player)
        }
    }

    fun release() {
        mediaPlayer?.let { finish(it) }
    }

    private fun finish(player: MediaPlayer) {
        val current = mediaPlayer === player
        if (current) {
            isPlaying = false
            mediaPlayer = null
        }
        try {
            player.release()
        } catch (_: IllegalStateException) {
            // Already released.
        }
        if (current) {
            onCompletion()
        }
    }
}
