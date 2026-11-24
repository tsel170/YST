package com.example.yst.sounds

import com.example.yst.detection.SoundClip
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundCatalogCodecTest {
    @Test
    fun roundTripKeepsUriAndDisplayName() {
        val clips = listOf(
            SoundClip(
                id = "content://com.example.provider/audio/1",
                displayName = "Hello there.wav",
            ),
            SoundClip(
                id = "content://com.example.provider/audio/2?name=a%20b",
                displayName = "Second\tline",
            ),
        )

        val restored = SoundCatalogCodec.decode(SoundCatalogCodec.encode(clips))

        assertEquals(clips, restored)
    }

    @Test
    fun blankCatalogIsEmpty() {
        assertEquals(emptyList<SoundClip>(), SoundCatalogCodec.decode("  "))
    }
}
