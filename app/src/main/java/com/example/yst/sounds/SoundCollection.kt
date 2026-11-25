package com.example.yst.sounds

import com.example.yst.detection.SelectableSound
import com.example.yst.detection.SoundClip
import java.util.UUID

data class ManagedSound(
    val clip: SoundClip,
    val rarityId: String,
    val bundled: Boolean,
    val playable: Boolean,
)

data class CollectionSnapshot(
    val sounds: List<ManagedSound>,
    val rarities: List<RarityTier>,
) {
    fun playableSelections(): List<SelectableSound> {
        return sounds.filter { it.playable }.map { sound ->
            SelectableSound(clip = sound.clip, rarityId = sound.rarityId)
        }
    }
}

interface CollectionConfig {
    fun hiddenBundledIds(): Set<String>

    fun setHiddenBundledIds(ids: Set<String>)

    fun rarities(): List<RarityTier>

    fun setRarities(tiers: List<RarityTier>)

    fun assignments(): Map<String, String>

    fun setAssignments(assignments: Map<String, String>)
}

class MemoryCollectionConfig(
    hidden: Set<String> = emptySet(),
    rarities: List<RarityTier> = defaultRarities(),
    assignments: Map<String, String> = emptyMap(),
) : CollectionConfig {
    private var hiddenIds = hidden.toMutableSet()
    private var storedRarities = rarities
    private var storedAssignments = assignments

    override fun hiddenBundledIds(): Set<String> = hiddenIds.toSet()

    override fun setHiddenBundledIds(ids: Set<String>) {
        hiddenIds = ids.toMutableSet()
    }

    override fun rarities(): List<RarityTier> = storedRarities

    override fun setRarities(tiers: List<RarityTier>) {
        storedRarities = tiers
    }

    override fun assignments(): Map<String, String> = storedAssignments

    override fun setAssignments(assignments: Map<String, String>) {
        storedAssignments = assignments
    }
}

class SoundCollectionEditor(
    private val bundled: () -> List<SoundClip>,
    private val imported: SoundLibrary,
    private val config: CollectionConfig = MemoryCollectionConfig(),
    private val newRarityId: () -> String = { "custom-${UUID.randomUUID()}" },
    private val bundledRarities: Map<String, String> = BundledRarityDefaults.byResourceName,
) {
    fun snapshot(playable: (SoundClip) -> Boolean = { true }): CollectionSnapshot {
        val tiers = storedTiers()
        val knownIds = tiers.map { it.id }.toSet()
        val assignments = config.assignments()
        val hidden = config.hiddenBundledIds()
        val bundledSounds = bundled().filter { it.id !in hidden }.map { clip ->
            managed(clip, bundled = true, rarityId = resolvedRarity(assignments, knownIds, clip.id, bundled = true), playable = playable(clip))
        }
        val importedSounds = imported.load().map { clip ->
            managed(clip, bundled = false, rarityId = resolvedRarity(assignments, knownIds, clip.id, bundled = false), playable = playable(clip))
        }
        return CollectionSnapshot(sounds = bundledSounds + importedSounds, rarities = tiers)
    }

    fun remove(id: String) {
        if (BundledRawSounds.isBundled(id)) {
            config.setHiddenBundledIds(config.hiddenBundledIds() + id)
        } else {
            imported.remove(id)
        }
        config.setAssignments(config.assignments() - id)
    }

    fun assign(soundId: String, rarityId: String): Boolean {
        val tiers = storedTiers()
        if (tiers.none { it.id == rarityId }) {
            return false
        }
        if (snapshot().sounds.none { it.clip.id == soundId }) {
            return false
        }
        config.setAssignments(config.assignments() + (soundId to rarityId))
        return true
    }

    fun renameRarity(id: String, name: String): Boolean {
        val updated = storedTiers().renamed(id, name) ?: return false
        config.setRarities(updated)
        return true
    }

    fun setProbability(id: String, percent: Int): Boolean {
        val updated = storedTiers().withProbability(id, percent) ?: return false
        if (!updated.isValidRarityConfiguration()) {
            return false
        }
        config.setRarities(updated)
        return true
    }

    fun addRarity(name: String): RarityTier {
        val created = storedTiers().plusCustom(name, newRarityId())
        config.setRarities(created)
        return created.last()
    }

    fun deleteRarity(id: String): Boolean {
        val current = storedTiers()
        val updated = current.withoutCustom(id) ?: return false
        if (!updated.isValidRarityConfiguration()) {
            return false
        }
        val receiver = updated.maxBy { it.order }.id
        config.setRarities(updated)
        config.setAssignments(config.assignments().mapValues { (_, assigned) ->
            if (assigned == id) receiver else assigned
        })
        return true
    }

    fun restoreDefaultRarities() {
        val defaults = defaultRarities()
        val known = defaults.map { it.id }.toSet()
        config.setRarities(defaults)
        config.setAssignments(config.assignments().mapValues { (_, assigned) ->
            if (assigned in known) assigned else RarityIds.COMMON
        })
    }

    private fun storedTiers(): List<RarityTier> {
        val stored = config.rarities()
        return if (stored.isValidRarityConfiguration()) stored else defaultRarities()
    }

    private fun resolvedRarity(
        assignments: Map<String, String>,
        knownIds: Set<String>,
        soundId: String,
        bundled: Boolean,
    ): String {
        val assigned = assignments[soundId]
        if (assigned != null && assigned in knownIds) {
            return assigned
        }
        if (bundled) {
            val preset = BundledRawSounds.resourceName(soundId)?.let { bundledRarities[it] }
            if (preset != null && preset in knownIds) {
                return preset
            }
        }
        return RarityIds.COMMON
    }

    private fun managed(
        clip: SoundClip,
        bundled: Boolean,
        rarityId: String,
        playable: Boolean,
    ): ManagedSound {
        return ManagedSound(
            clip = clip,
            rarityId = rarityId,
            bundled = bundled,
            playable = playable && (bundled || clip.id.isNotBlank()),
        )
    }
}
