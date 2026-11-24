package com.example.yst.sounds

import com.example.yst.detection.SoundClip
import org.junit.Assert.assertEquals
import org.junit.Test

class MergedSoundLibraryTest {
    @Test
    fun bundledSoundsStayAvailableAndImportedSoundsCanBeRemoved() {
        val persisted = MemorySoundLibrary()
        val bundled = SoundClip(
            id = "android.resource://com.example.yst/raw/bell",
            displayName = "bell",
        )
        val library = MergedSoundLibrary(
            bundled = { listOf(bundled) },
            persisted = persisted,
        )

        assertEquals(listOf(bundled), library.load())

        library.remove(bundled.id)
        assertEquals(listOf(bundled), library.load())

        val imported = SoundClip(id = "content://sounds/a", displayName = "A")
        assertEquals(listOf(bundled, imported), library.add(listOf(imported)))

        assertEquals(listOf(bundled), library.remove(imported.id))
    }
}
