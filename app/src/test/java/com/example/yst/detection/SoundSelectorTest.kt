package com.example.yst.detection

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundSelectorTest {
    private val first = SoundClip(id = "a", displayName = "A")
    private val second = SoundClip(id = "b", displayName = "B")
    private val selector = SoundSelector()

    @Test
    fun emptyCollectionSelectsNothing() {
        assertNull(selector.pick(emptyList(), previousId = null, random = Random(1)))
    }

    @Test
    fun singleClipCanRepeat() {
        val picked = selector.pick(listOf(first), previousId = first.id, random = Random(1))

        assertEquals(first.id, picked?.id)
    }

    @Test
    fun doesNotRepeatPreviousClipWhenAnotherClipExists() {
        val random = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }

        val firstPick = selector.pick(listOf(first, second), previousId = null, random = random)
        val secondPick = selector.pick(listOf(first, second), previousId = firstPick?.id, random = random)

        assertEquals("a", firstPick?.id)
        assertEquals("b", secondPick?.id)
    }
}
