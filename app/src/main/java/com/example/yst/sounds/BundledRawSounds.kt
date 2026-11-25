package com.example.yst.sounds

import android.content.Context
import com.example.yst.detection.SoundClip

object BundledRarityDefaults {
    /**
     * Shipped raw file name, without the extension, to a rarity id.
     * Example: "airhorn" to RarityIds.LEGENDARY for res/raw/airhorn.mp3.
     * Names must be lowercase letters, digits, and underscores.
     * A file that is not listed starts as Common.
     * A change the user makes later replaces this default on that device.
     */
    val byResourceName: Map<String, String> = mapOf(
        // "quiet_beep" to RarityIds.COMMON,
        // "alert" to RarityIds.RARE,
        // "airhorn" to RarityIds.LEGENDARY,
    )
}

object BundledRawSounds {
    const val ID_PREFIX = "android.resource://"

    fun resourceName(id: String): String? {
        val marker = "/raw/"
        val start = id.lastIndexOf(marker)
        if (start < 0) {
            return null
        }
        val name = id.substring(start + marker.length).substringBefore('?').substringBefore('.')
        return name.ifBlank { null }
    }

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
