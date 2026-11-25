package com.example.yst.detection

import com.example.yst.sounds.RarityTier
import com.example.yst.sounds.isValidRarityConfiguration
import kotlin.random.Random

data class SelectableSound(
    val clip: SoundClip,
    val rarityId: String,
)

/**
 * Rolls 1..100 against rarity probabilities, then picks a sound from that tier.
 * An empty tier falls through toward more common tiers, then wraps.
 * The previous sound is skipped when another eligible sound exists.
 */
class RaritySelector {
    fun pick(
        sounds: List<SelectableSound>,
        rarities: List<RarityTier>,
        previousId: String?,
        random: Random,
    ): SoundClip? {
        if (sounds.isEmpty() || !rarities.isValidRarityConfiguration()) {
            return null
        }
        val ordered = rarities.sortedBy { it.order }
        val selected = tierForRoll(ordered, random.nextInt(1, 101))
        val pool = poolFor(ordered, selected, sounds)
        if (pool.isEmpty()) {
            return null
        }
        val choices = if (sounds.size >= 2) {
            val withoutPrevious = pool.filter { it.clip.id != previousId }
            if (withoutPrevious.isNotEmpty()) {
                withoutPrevious
            } else {
                val elsewhere = poolFor(ordered, selected, sounds.filter { it.clip.id != previousId })
                elsewhere.ifEmpty { pool }
            }
        } else {
            pool
        }
        return choices[random.nextInt(choices.size)].clip
    }

    private fun tierForRoll(ordered: List<RarityTier>, roll: Int): RarityTier {
        var cursor = 0
        for (tier in ordered) {
            cursor += tier.probabilityPercent
            if (roll <= cursor) {
                return tier
            }
        }
        return ordered.last()
    }

    private fun poolFor(
        ordered: List<RarityTier>,
        selected: RarityTier,
        sounds: List<SelectableSound>,
    ): List<SelectableSound> {
        if (sounds.isEmpty()) {
            return emptyList()
        }
        val start = ordered.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
        val walk = (start until ordered.size) + (0 until start)
        for (index in walk) {
            val matches = sounds.filter { it.rarityId == ordered[index].id }
            if (matches.isNotEmpty()) {
                return matches
            }
        }
        return sounds
    }
}
