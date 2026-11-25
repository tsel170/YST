package com.example.yst.sounds

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionCodecTest {
    @Test
    fun raritiesAndAssignmentsRoundTrip() {
        val tiers = defaultRarities().map { tier ->
            if (tier.id == RarityIds.LEGENDARY) tier.copy(name = "Mythic\tOne") else tier
        }
        val assignments = mapOf(
            "android.resource://com.example.yst/raw/bell" to RarityIds.RARE,
            "content://sounds/a?x=1" to RarityIds.LEGENDARY,
        )

        assertEquals(tiers, CollectionCodec.decodeRarities(CollectionCodec.encodeRarities(tiers)))
        assertEquals(assignments, CollectionCodec.decodeAssignments(CollectionCodec.encodeAssignments(assignments)))
        assertEquals(
            setOf("android.resource://com.example.yst/raw/bell"),
            CollectionCodec.decodeIds(CollectionCodec.encodeIds(setOf("android.resource://com.example.yst/raw/bell"))),
        )
    }

    @Test
    fun brokenLinesAreSkipped() {
        assertEquals(emptyList<RarityTier>(), CollectionCodec.decodeRarities("not-a-tier"))
        assertEquals(emptyMap<String, String>(), CollectionCodec.decodeAssignments("only-one-column"))
    }
}
