package com.example.yst.sounds

import android.content.Context
import com.example.yst.detection.SoundClip

object BundledRawSounds {
    const val ID_PREFIX = "android.resource://"

    fun isBundled(id: String): Boolean = id.startsWith(ID_PREFIX)

    fun list(context: Context): List<SoundClip> {
        val rawClass = try {
            Class.forName("${context.packageName}.R\$raw")
        } catch (_: ClassNotFoundException) {
            return emptyList()
        }
        return rawClass.fields.mapNotNull { field ->
            if (field.type != Int::class.javaPrimitiveType) {
                return@mapNotNull null
            }
            val entryName = field.name
            SoundClip(
                id = "$ID_PREFIX${context.packageName}/raw/$entryName",
                displayName = entryName,
            )
        }.sortedBy { it.displayName }
    }
}

class MergedSoundLibrary(
    private val bundled: () -> List<SoundClip>,
    private val persisted: SoundLibrary,
) : SoundLibrary {
    override fun load(): List<SoundClip> {
        val included = bundled()
        val imported = persisted.load().filter { clip -> included.none { it.id == clip.id } }
        return included + imported
    }

    override fun add(clips: List<SoundClip>): List<SoundClip> {
        persisted.add(clips.filterNot { BundledRawSounds.isBundled(it.id) })
        return load()
    }

    override fun remove(id: String): List<SoundClip> {
        if (!BundledRawSounds.isBundled(id)) {
            persisted.remove(id)
        }
        return load()
    }
}
