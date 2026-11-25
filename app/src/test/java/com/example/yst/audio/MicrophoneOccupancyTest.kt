package com.example.yst.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrophoneOccupancyTest {
    @Test
    fun ourOwnRecorderIsNotTreatedAsSomeoneElse() {
        val clients = listOf(RecordingClient(sessionId = 7, silenced = false))

        assertFalse(MicrophoneOccupancy.taken(ourSessionId = 7, clients = clients, inCall = false))
    }

    @Test
    fun anotherRecorderMeansTheMicrophoneIsTaken() {
        val clients = listOf(
            RecordingClient(sessionId = 7, silenced = false),
            RecordingClient(sessionId = 9, silenced = false),
        )

        assertTrue(MicrophoneOccupancy.taken(ourSessionId = 7, clients = clients, inCall = false))
    }

    @Test
    fun silencedRecorderMeansTheMicrophoneIsTaken() {
        val clients = listOf(RecordingClient(sessionId = 7, silenced = true))

        assertTrue(MicrophoneOccupancy.taken(ourSessionId = 7, clients = clients, inCall = false))
    }

    @Test
    fun aCallMeansTheMicrophoneIsTaken() {
        assertTrue(
            MicrophoneOccupancy.taken(
                ourSessionId = 7,
                clients = listOf(RecordingClient(sessionId = 7, silenced = false)),
                inCall = true,
            ),
        )
    }

    @Test
    fun anEmptyListAfterWeReleaseMeansTheMicrophoneIsFree() {
        assertFalse(MicrophoneOccupancy.taken(ourSessionId = null, clients = emptyList(), inCall = false))
    }

    @Test
    fun aRemainingClientAfterWeReleaseKeepsTheMicrophoneTaken() {
        assertTrue(
            MicrophoneOccupancy.taken(
                ourSessionId = null,
                clients = listOf(RecordingClient(sessionId = 9, silenced = false)),
                inCall = false,
            ),
        )
    }
}
