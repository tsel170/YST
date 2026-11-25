package com.example.yst.sounds

import android.content.Context

class PreferenceCollectionConfig(
    context: Context,
) : CollectionConfig {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCE_NAME, Context.MODE_PRIVATE)

    override fun hiddenBundledIds(): Set<String> {
        return CollectionCodec.decodeIds(preferences.getString(KEY_HIDDEN, "").orEmpty())
    }

    override fun setHiddenBundledIds(ids: Set<String>) {
        preferences.edit().putString(KEY_HIDDEN, CollectionCodec.encodeIds(ids)).apply()
    }

    override fun rarities(): List<RarityTier> {
        val stored = CollectionCodec.decodeRarities(preferences.getString(KEY_RARITIES, "").orEmpty())
        return if (stored.isValidRarityConfiguration()) stored else defaultRarities()
    }

    override fun setRarities(tiers: List<RarityTier>) {
        preferences.edit().putString(KEY_RARITIES, CollectionCodec.encodeRarities(tiers)).apply()
    }

    override fun assignments(): Map<String, String> {
        return CollectionCodec.decodeAssignments(preferences.getString(KEY_ASSIGNMENTS, "").orEmpty())
    }

    override fun setAssignments(assignments: Map<String, String>) {
        preferences.edit().putString(KEY_ASSIGNMENTS, CollectionCodec.encodeAssignments(assignments)).apply()
    }

    private companion object {
        const val PREFERENCE_NAME = "voice_trigger_sounds"
        const val KEY_HIDDEN = "hidden_bundled"
        const val KEY_RARITIES = "rarities"
        const val KEY_ASSIGNMENTS = "assignments"
    }
}
