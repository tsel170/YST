package com.example.yst.detection

import kotlin.random.Random

class SoundSelector {
    fun pick(clips: List<SoundClip>, previousId: String?, random: Random): SoundClip? {
        if (clips.isEmpty()) {
            return null
        }
        if (clips.size == 1 || previousId == null) {
            return clips[random.nextInt(clips.size)]
        }
        val withoutPrevious = clips.filter { it.id != previousId }
        val choices = withoutPrevious.ifEmpty { clips }
        return choices[random.nextInt(choices.size)]
    }
}
