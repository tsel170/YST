package com.example.yst.sounds

import com.example.yst.detection.SoundClip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundCollectionEditorTest {
    private val bundled = SoundClip(
        id = "android.resource://com.example.yst/raw/bell",
        displayName = "bell",
    )
    private val imported = SoundClip(id = "content://sounds/a", displayName = "A")

    @Test
    fun removingABundledSoundHidesItWithoutDroppingTheResource() {
        val source = mutableListOf(bundled)
        val config = MemoryCollectionConfig()
        val editor = editor(source, config)

        editor.remove(bundled.id)

        assertEquals(emptyList<String>(), editor.snapshot().sounds.map { it.clip.id })
        assertEquals(listOf(bundled), source.toList())
        val reloaded = editor(source, config)
        assertEquals(emptyList<String>(), reloaded.snapshot().sounds.map { it.clip.id })
    }

    @Test
    fun removingAnImportedSoundDropsItFromSelectionImmediately() {
        val library = MemorySoundLibrary(listOf(imported))
        val editor = SoundCollectionEditor(bundled = { emptyList() }, imported = library)

        editor.remove(imported.id)

        assertTrue(editor.snapshot().playableSelections().isEmpty())
        assertTrue(library.load().isEmpty())
    }

    @Test
    fun unplayableImportStaysListedButIsNotSelectable() {
        val library = MemorySoundLibrary(listOf(imported))
        val editor = SoundCollectionEditor(bundled = { emptyList() }, imported = library)

        val snapshot = editor.snapshot { false }

        assertEquals(listOf(imported.id), snapshot.sounds.map { it.clip.id })
        assertFalse(snapshot.sounds.single().playable)
        assertTrue(snapshot.playableSelections().isEmpty())
    }

    @Test
    fun rarityAssignmentSurvivesReloadAndRenameKeepsTheId() {
        val config = MemoryCollectionConfig()
        val library = MemorySoundLibrary(listOf(imported))
        val editor = SoundCollectionEditor(bundled = { emptyList() }, imported = library, config = config)

        assertTrue(editor.assign(imported.id, RarityIds.LEGENDARY))
        assertTrue(editor.renameRarity(RarityIds.LEGENDARY, "Mythic"))

        val restored = SoundCollectionEditor(bundled = { emptyList() }, imported = library, config = config).snapshot()

        assertEquals(RarityIds.LEGENDARY, restored.sounds.single().rarityId)
        assertEquals("Mythic", restored.rarities.first { it.id == RarityIds.LEGENDARY }.name)
    }

    @Test
    fun probabilityChangeStaysAtOneHundredAndRejectsAnImpossibleValue() {
        val editor = SoundCollectionEditor(bundled = { emptyList() }, imported = MemorySoundLibrary())

        assertTrue(editor.setProbability(RarityIds.LEGENDARY, 10))
        val updated = editor.snapshot().rarities
        assertEquals(100, updated.sumOf { it.probabilityPercent })
        assertEquals(10, updated.first { it.id == RarityIds.LEGENDARY }.probabilityPercent)
        assertEquals(86, updated.first { it.id == RarityIds.COMMON }.probabilityPercent)
        assertFalse(editor.setProbability(RarityIds.LEGENDARY, 101))
    }

    @Test
    fun deletingACustomRarityReassignsItsSoundsAndProbability() {
        val library = MemorySoundLibrary(listOf(imported))
        val editor = SoundCollectionEditor(
            bundled = { emptyList() },
            imported = library,
            newRarityId = { "custom-1" },
        )
        val created = editor.addRarity("Epic")
        assertTrue(editor.setProbability(created.id, 10))
        assertTrue(editor.assign(imported.id, created.id))

        assertTrue(editor.deleteRarity(created.id))
        val snapshot = editor.snapshot()

        assertEquals(RarityIds.COMMON, snapshot.sounds.single().rarityId)
        assertTrue(snapshot.rarities.none { it.id == created.id })
        assertEquals(100, snapshot.rarities.sumOf { it.probabilityPercent })
        assertFalse(editor.deleteRarity(RarityIds.COMMON))
    }

    @Test
    fun restoreDefaultsDropsCustomTiersAndKeepsKnownAssignments() {
        val library = MemorySoundLibrary(listOf(imported, SoundClip("content://sounds/b", "B")))
        val editor = SoundCollectionEditor(
            bundled = { emptyList() },
            imported = library,
            newRarityId = { "custom-1" },
        )
        editor.assign(imported.id, RarityIds.RARE)
        val created = editor.addRarity("Epic")
        editor.assign("content://sounds/b", created.id)
        editor.renameRarity(RarityIds.COMMON, "Ordinary")

        editor.restoreDefaultRarities()
        val snapshot = editor.snapshot()

        assertEquals(defaultRarities(), snapshot.rarities)
        assertEquals(RarityIds.RARE, snapshot.sounds.first { it.clip.id == imported.id }.rarityId)
        assertEquals(RarityIds.COMMON, snapshot.sounds.first { it.clip.id == "content://sounds/b" }.rarityId)
    }

    private fun editor(source: List<SoundClip>, config: CollectionConfig): SoundCollectionEditor {
        return SoundCollectionEditor(
            bundled = { source },
            imported = MemorySoundLibrary(),
            config = config,
        )
    }
}
