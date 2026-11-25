package com.example.yst.sounds

import java.net.URLDecoder
import java.net.URLEncoder

internal object CollectionCodec {
    fun encodeIds(ids: Set<String>): String {
        return ids.sorted().joinToString("\n") { encode(it) }
    }

    fun decodeIds(stored: String): Set<String> {
        if (stored.isBlank()) {
            return emptySet()
        }
        return stored.lineSequence().map { decode(it) }.filter { it.isNotBlank() }.toSet()
    }

    fun encodeRarities(tiers: List<RarityTier>): String {
        return tiers.joinToString("\n") { tier ->
            listOf(
                encode(tier.id),
                encode(tier.name),
                tier.probabilityPercent.toString(),
                tier.order.toString(),
                if (tier.custom) "1" else "0",
            ).joinToString("\t")
        }
    }

    fun decodeRarities(stored: String): List<RarityTier> {
        if (stored.isBlank()) {
            return emptyList()
        }
        return stored.lineSequence().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size != 5) {
                return@mapNotNull null
            }
            val id = decode(parts[0])
            val name = decode(parts[1])
            val probability = parts[2].toIntOrNull() ?: return@mapNotNull null
            val order = parts[3].toIntOrNull() ?: return@mapNotNull null
            if (id.isBlank() || name.isBlank()) {
                return@mapNotNull null
            }
            RarityTier(
                id = id,
                name = name,
                probabilityPercent = probability,
                order = order,
                custom = parts[4] == "1",
            )
        }.toList()
    }

    fun encodeAssignments(assignments: Map<String, String>): String {
        return assignments.entries.sortedBy { it.key }.joinToString("\n") { (soundId, rarityId) ->
            encode(soundId) + "\t" + encode(rarityId)
        }
    }

    fun decodeAssignments(stored: String): Map<String, String> {
        if (stored.isBlank()) {
            return emptyMap()
        }
        return stored.lineSequence().mapNotNull { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size != 2) {
                return@mapNotNull null
            }
            val soundId = decode(parts[0])
            val rarityId = decode(parts[1])
            if (soundId.isBlank() || rarityId.isBlank()) {
                return@mapNotNull null
            }
            soundId to rarityId
        }.toMap()
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun decode(value: String): String {
        return URLDecoder.decode(value, Charsets.UTF_8.name())
    }
}
