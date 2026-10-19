package com.wisprfox.android.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationPassesTest {
    private val turns = listOf(SpeakerTurn("Speaker 1", "Hello", 0.0, 1.0))
    private val engine = EngineInfo("deepgram", "nova-3")

    @Test fun failureWithoutResultRecordsStateAndError() {
        val doc = ConversationPasses.failed(SpeakerTurnsDocument(), ConversationPass.NOTES, "rate limit", hasResult = false)
        assertEquals(PassState.FAILED, doc.passes?.get("notes"))
        assertEquals("rate limit", doc.errors?.get("notes"))
    }

    @Test fun failedRetryNeverDiscardsAnEarlierResult() {
        val done = ConversationPasses.speakersDone(SpeakerTurnsDocument(), turns, engine)
        val retried = ConversationPasses.failed(done, ConversationPass.SPEAKERS, "network", hasResult = true)
        assertEquals(turns, retried.turns)
        assertEquals(engine, retried.engine)
        assertEquals(PassState.DONE, retried.passes?.get("speakers"))
        assertNull(retried.errors?.get("speakers"))
    }

    @Test fun oneFailedPassLeavesTheOthersAlone() {
        var doc = ConversationPasses.speakersDone(SpeakerTurnsDocument(), turns, engine)
        doc = ConversationPasses.failed(doc, ConversationPass.ACCURACY, "Groq 500", hasResult = false)
        doc = ConversationPasses.notesDone(doc)
        assertEquals(mapOf("speakers" to "done", "accuracy" to "failed", "notes" to "done"), doc.passes)
        assertEquals(mapOf("accuracy" to "Groq 500"), doc.errors)
        assertEquals(turns, doc.turns)
    }

    @Test fun successClearsThatPassesError() {
        var doc = ConversationPasses.failed(SpeakerTurnsDocument(), ConversationPass.ACCURACY, "boom", hasResult = false)
        doc = ConversationPasses.failed(doc, ConversationPass.NOTES, "late", hasResult = false)
        doc = ConversationPasses.accuracyDone(doc, AccuracyTranscript("text", "groq", "whisper-large-v3"))
        assertEquals(PassState.DONE, doc.passes?.get("accuracy"))
        assertEquals(mapOf("notes" to "late"), doc.errors)
    }

    @Test fun lastErrorRemovedLeavesErrorsAbsent() {
        var doc = ConversationPasses.failed(SpeakerTurnsDocument(), ConversationPass.NOTES, "x", hasResult = false)
        doc = ConversationPasses.notesDone(doc)
        assertNull(doc.errors)
    }

    @Test fun retryableListsPassesWithoutResults() {
        var doc = ConversationPasses.speakersDone(SpeakerTurnsDocument(), turns, engine)
        doc = ConversationPasses.skipped(doc, ConversationPass.ACCURACY)
        doc = ConversationPasses.failed(doc, ConversationPass.NOTES, "x", hasResult = false)
        assertEquals(listOf(ConversationPass.ACCURACY, ConversationPass.NOTES), ConversationPasses.retryable(doc, null, inFlight = false))
        assertEquals(listOf(ConversationPass.ACCURACY), ConversationPasses.retryable(doc, "## Summary", inFlight = false))
        assertTrue(ConversationPasses.retryable(doc, null, inFlight = true).isEmpty())
    }

    @Test fun problemsNameFailedAndInterruptedPasses() {
        var doc = ConversationPasses.running(SpeakerTurnsDocument(), ConversationPass.SPEAKERS)
        doc = ConversationPasses.failed(doc, ConversationPass.ACCURACY, "x", hasResult = false)
        assertEquals(listOf("Speakers interrupted", "Whisper transcript failed"), ConversationPasses.problems(doc, inFlight = false))
        assertEquals(listOf("Whisper transcript failed"), ConversationPasses.problems(doc, inFlight = true))
    }

    @Test fun legacyV1DocumentHasSpeakersResult() {
        val v1 = SpeakerTurnsCodec.decodeDocument("""{"version":1,"turns":[{"speaker":"Speaker 1","text":"Hi"}]}""")
        assertTrue(ConversationPasses.hasResult(v1, ConversationPass.SPEAKERS, null))
        assertEquals(listOf(ConversationPass.ACCURACY, ConversationPass.NOTES), ConversationPasses.retryable(v1, null, false))
    }
}
