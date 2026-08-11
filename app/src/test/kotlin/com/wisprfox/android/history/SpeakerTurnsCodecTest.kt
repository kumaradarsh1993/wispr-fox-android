package com.wisprfox.android.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerTurnsCodecTest {
    @Test fun versionedTurnsRoundTrip() {
        val source = listOf(
            SpeakerTurn("Speaker 1", "Hello", 0.0, 1.2),
            SpeakerTurn("Speaker 2", "Hi there", 1.3, 2.0),
        )
        val encoded = SpeakerTurnsCodec.encode(source)
        assertTrue(encoded.contains("\"version\":1"))
        assertEquals(source, SpeakerTurnsCodec.decode(encoded))
    }

    @Test fun namesAreAppliedOnlyAtRenderTime() {
        val turns = SpeakerTurnsCodec.encode(listOf(SpeakerTurn("Speaker 1", "Status is green.")))
        val names = SpeakerTurnsCodec.encodeNames(mapOf("Speaker 1" to "Adarsh"))
        assertEquals("Adarsh:\nStatus is green.", SpeakerTurnsCodec.render(turns, names))
        assertEquals("Adarsh: approved", SpeakerTurnsCodec.applyNames("Speaker 1: approved", names))
        assertEquals("Speaker 1", SpeakerTurnsCodec.decode(turns).single().speaker)
    }

    @Test fun malformedRemoteJsonDegradesToEmpty() {
        assertTrue(SpeakerTurnsCodec.decode("not-json").isEmpty())
        assertTrue(SpeakerTurnsCodec.names("[]").isEmpty())
    }
}
