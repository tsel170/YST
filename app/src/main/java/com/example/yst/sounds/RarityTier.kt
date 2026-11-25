package com.example.yst.sounds

data class RarityTier(
    val id: String,
    val name: String,
    val probabilityPercent: Int,
    val order: Int,
    val custom: Boolean,
)

fun defaultRarities(): List<RarityTier> {
    return listOf(
        RarityTier(id = RarityIds.LEGENDARY, name = "Legendary", probabilityPercent = 1, order = 0, custom = false),
        RarityTier(id = RarityIds.RARE, name = "Rare", probabilityPercent = 25, order = 1, custom = false),
        RarityTier(id = RarityIds.COMMON, name = "Common", probabilityPercent = 74, order = 2, custom = false),
    )
}

object RarityIds {
    const val LEGENDARY = "legendary"
    const val RARE = "rare"
    const val COMMON = "common"
}

fun List<RarityTier>.isValidRarityConfiguration(): Boolean {
    if (isEmpty()) {
        return false
    }
    if (map { it.id }.distinct().size != size) {
        return false
    }
    if (any { it.name.isBlank() || it.probabilityPercent !in 0..100 }) {
        return false
    }
    return sumOf { it.probabilityPercent } == 100
}

fun List<RarityTier>.withProbability(id: String, percent: Int): List<RarityTier>? {
    val target = firstOrNull { it.id == id } ?: return null
    val requested = percent.coerceIn(0, 100)
    val delta = requested - target.probabilityPercent
    if (delta == 0) {
        return this
    }
    val donor = filter { it.id != id }.maxByOrNull { it.order } ?: return if (requested == 100) {
        map { tier -> if (tier.id == id) tier.copy(probabilityPercent = 100) else tier }
    } else {
        null
    }
    val donated = donor.probabilityPercent - delta
    if (donated !in 0..100) {
        return null
    }
    return map { tier ->
        when (tier.id) {
            id -> tier.copy(probabilityPercent = requested)
            donor.id -> tier.copy(probabilityPercent = donated)
            else -> tier
        }
    }
}

fun List<RarityTier>.renamed(id: String, name: String): List<RarityTier>? {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || none { it.id == id }) {
        return null
    }
    return map { tier -> if (tier.id == id) tier.copy(name = trimmed) else tier }
}

fun List<RarityTier>.plusCustom(name: String, id: String): List<RarityTier> {
    val trimmed = name.trim().ifEmpty { "Custom" }
    val nextOrder = (maxOfOrNull { it.order } ?: -1) + 1
    return this + RarityTier(
        id = id,
        name = trimmed,
        probabilityPercent = 0,
        order = nextOrder,
        custom = true,
    )
}

fun List<RarityTier>.withoutCustom(id: String): List<RarityTier>? {
    val target = firstOrNull { it.id == id } ?: return null
    if (!target.custom) {
        return null
    }
    val remaining = filter { it.id != id }
    if (remaining.isEmpty()) {
        return null
    }
    val receiver = remaining.maxBy { it.order }
    val updated = remaining.map { tier ->
        if (tier.id == receiver.id) {
            tier.copy(probabilityPercent = tier.probabilityPercent + target.probabilityPercent)
        } else {
            tier
        }
    }
    return updated.takeIf { it.isValidRarityConfiguration() }
}
