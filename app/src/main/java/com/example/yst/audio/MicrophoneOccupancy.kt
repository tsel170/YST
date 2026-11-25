package com.example.yst.audio

data class RecordingClient(
    val sessionId: Int,
    val silenced: Boolean,
)

/**
 * The microphone is taken when a call is active, this app's recorder is silenced,
 * or any other recording client is active.
 */
object MicrophoneOccupancy {
    fun taken(
        ourSessionId: Int?,
        clients: List<RecordingClient>,
        inCall: Boolean,
    ): Boolean {
        if (inCall) {
            return true
        }
        if (ourSessionId != null && clients.any { it.sessionId == ourSessionId && it.silenced }) {
            return true
        }
        return clients.any { ourSessionId == null || it.sessionId != ourSessionId }
    }
}
