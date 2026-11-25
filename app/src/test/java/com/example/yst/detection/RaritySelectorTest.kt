package com.example.yst.detection

import com.example.yst.sounds.RarityIds
import com.example.yst.sounds.defaultRarities
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RaritySelectorTest {
    private val legendary = SelectableSound(SoundClip("legend", "Legend"), RarityIds.LEGENDARY)
    private val rare = SelectableSound(SoundClip("rare-sound", "Rare"), RarityIds.RARE)
    private val commonA = SelectableSound(SoundClip("common-a", "Common A"), RarityIds.COMMON)
    private val commonB = SelectableSound(SoundClip("common-b", "Common B"), RarityIds.COMMON)
    private val selector = RaritySelector()
    private val tiers = defaultRarities()

    @Test
    fun rollBoundariesSelectTheConfiguredRarity() {
        assertEquals("legend", pick(1, listOf(legendary, rare, commonA))?.id)
        assertEquals("rare-sound", pick(2, listOf(legendary, rare, commonA))?.id)
        assertEquals("rare-sound", pick(5, listOf(legendary, rare, commonA))?.id)
        assertEquals("common-a", pick(6, listOf(legendary, rare, commonA))?.id)
        assertEquals("common-a", pick(100, listOf(legendary, rare, commonA))?.id)
    }

    @Test
    fun emptySelectedRarityFallsThroughToAMoreCommonTier() {
        val picked = pick(1, listOf(rare, commonA))

        assertEquals("rare-sound", picked?.id)
    }

    @Test
    fun emptyCommonRarityWrapsToAnAvailableRarerTier() {
        val picked = pick(100, listOf(legendary))

        assertEquals("legend", picked?.id)
    }

    @Test
    fun doesNotRepeatThePreviousSoundWhenAnotherEligibleSoundExists() {
        val random = FixedRandom(roll = 100, index = 0)
        val first = selector.pick(listOf(commonA, commonB), tiers, previousId = null, random = random)
        val second = selector.pick(listOf(commonA, commonB), tiers, previousId = first?.id, random = random)

        assertEquals("common-a", first?.id)
        assertEquals("common-b", second?.id)
    }

    @Test
    fun previousSoundInAnEmptyFallbackStillAvoidsARepeat() {
        val picked = selector.pick(
            sounds = listOf(legendary, commonA),
            rarities = tiers,
            previousId = legendary.clip.id,
            random = FixedRandom(roll = 1, index = 0),
        )

        assertEquals("common-a", picked?.id)
    }

    @Test
    fun singleEligibleSoundCanRepeat() {
        val picked = selector.pick(
            sounds = listOf(commonA),
            rarities = tiers,
            previousId = commonA.clip.id,
            random = FixedRandom(roll = 1, index = 0),
        )

        assertEquals("common-a", picked?.id)
    }

    @Test
    fun emptyCollectionSelectsNothing() {
        assertNull(selector.pick(emptyList(), tiers, previousId = null, random = FixedRandom(1, 0)))
    }

    private fun pick(roll: Int, sounds: List<SelectableSound>): SoundClip? {
        return selector.pick(sounds, tiers, previousId = null, random = FixedRandom(roll, index = 0))
    }

    private class FixedRandom(
        private val roll: Int,
        private val index: Int,
    ) : Random() {
        override fun nextBits(bitCount: Int): Int = index

        override fun nextInt(until: Int): Int = index

        override fun nextInt(from: Int, until: Int): Int = roll
    }
}
