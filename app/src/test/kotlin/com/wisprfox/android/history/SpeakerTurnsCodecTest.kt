package com.wisprfox.android.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerTurnsCodecTest {
    @Test fun versionedTurnsRoundTrip() {
        val source = listOf(
            SpeakerTurn("Speaker 1", "Hello", 0.0, 1.2),
            SpeakerTurn("Speaker 2", "Hi there", 1.3, 2.0),
        )
        val encoded = SpeakerTurnsCodec.encode(source)
        assertTrue(encoded.contains("\"version\":2"))
        assertEquals(source, SpeakerTurnsCodec.decode(encoded))
    }

    @Test fun decodesVersion1Documents() {
        val v1 = """{"version":1,"turns":[{"speaker":"Speaker 1","text":"Hi","start":0.0,"end":1.0}]}"""
        val doc = SpeakerTurnsCodec.decodeDocument(v1)!!
        assertEquals(1, doc.version)
        assertEquals("Hi", doc.turns.single().text)
        assertNull(doc.accuracy)
        assertNull(doc.engine)
        assertNull(doc.passes)
        assertNull(doc.errors)
    }

    @Test fun version2RoundTripsEveryField() {
        val doc = SpeakerTurnsDocument(
            turns = listOf(SpeakerTurn("Speaker 1", "Hello", 0.0, 4.2)),
            engine = EngineInfo("deepgram", "nova-3"),
            accuracy = AccuracyTranscript("Hello there", "groq", "whisper-large-v3"),
            passes = mapOf("speakers" to "done", "accuracy" to "done", "notes" to "failed"),
            errors = mapOf("notes" to "Groq cleanup 429: rate limit"),
        )
        val encoded = SpeakerTurnsCodec.encodeDocument(doc.copy(version = 1))
        assertTrue("writers always write v2", encoded.contains("\"version\":2"))
        assertEquals(doc, SpeakerTurnsCodec.decodeDocument(encoded))
    }

    @Test fun absentOptionalFieldsAreOmittedNotNull() {
        val encoded = SpeakerTurnsCodec.encode(listOf(SpeakerTurn("Speaker 1", "Hi")))
        assertFalse(encoded.contains("accuracy"))
        assertFalse(encoded.contains("null"))
    }

    @Test fun unknownKeysAreIgnored() {
        val raw = """{"version":3,"turns":[],"future":{"x":1},"accuracy":{"text":"a","provider":"groq","model":"m"}}"""
        assertEquals("a", SpeakerTurnsCodec.decodeDocument(raw)?.accuracy?.text)
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
        assertNull(SpeakerTurnsCodec.decodeDocument("not-json"))
        assertNull(SpeakerTurnsCodec.decodeDocument(""))
        assertTrue(SpeakerTurnsCodec.names("[]").isEmpty())
    }
}
