package com.example.yst.sounds

import com.example.yst.detection.SoundClip
import java.net.URLDecoder
import java.net.URLEncoder

interface SoundLibrary {
    fun load(): List<SoundClip>

    fun add(clips: List<SoundClip>): List<SoundClip>

    fun remove(id: String): List<SoundClip>
}

class MemorySoundLibrary(
    initial: List<SoundClip> = emptyList(),
) : SoundLibrary {
    private val items = initial.toMutableList()

    override fun load(): List<SoundClip> = items.toList()

    override fun add(clips: List<SoundClip>): List<SoundClip> {
        for (clip in clips) {
            if (items.none { it.id == clip.id }) {
                items += clip
            }
        }
        return items.toList()
    }

    override fun remove(id: String): List<SoundClip> {
        items.removeAll { it.id == id }
        return items.toList()
    }
}

internal object SoundCatalogCodec {
    fun encode(clips: List<SoundClip>): String {
        return clips.joinToString("\n") { clip ->
            encodeComponent(clip.id) + "\t" + encodeComponent(clip.displayName)
        }
    }

    fun decode(stored: String): List<SoundClip> {
        if (stored.isBlank()) {
            return emptyList()
        }
        return stored.lineSequence().mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size != 2) {
                return@mapNotNull null
            }
            val id = decodeComponent(parts[0])
            if (id.isBlank()) {
                return@mapNotNull null
            }
            val name = decodeComponent(parts[1]).ifBlank { id }
            SoundClip(id = id, displayName = name)
        }.toList()
    }

    private fun encodeComponent(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun decodeComponent(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}
