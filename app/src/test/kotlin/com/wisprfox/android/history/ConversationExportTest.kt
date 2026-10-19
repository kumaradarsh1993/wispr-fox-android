package com.wisprfox.android.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ConversationExportTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val created = LocalDateTime.of(2026, 10, 19, 14, 5).atZone(zone).toInstant().toEpochMilli()

    private val fullDoc = SpeakerTurnsDocument(
        turns = listOf(
            SpeakerTurn("Speaker 1", "Shall we start?", 0.0, 2.0),
            SpeakerTurn("Speaker 2", "Yes, the launch is Friday.", 42.4, 45.0),
            SpeakerTurn("Speaker 3", "Agreed.", 61.0, 62.0),
        ),
        engine = EngineInfo("deepgram", "nova-3"),
        accuracy = AccuracyTranscript("Shall we start? Yes, the launch is Friday. Agreed.", "groq", "whisper-large-v3"),
    )

    @Test fun markdownMatchesTheContract() {
        val input = ConversationExport.Input(
            title = "Launch sync",
            createdAtMillis = created,
            durationMs = 3_723_000,
            meetingNotes = "## Summary\n- Speaker 2 confirmed Friday.",
            doc = fullDoc,
            names = mapOf("Speaker 1" to "Asha", "Speaker 2" to "Rohan"),
            zone = zone,
        )
        val expected = """
            |# Launch sync
            |
            |- Date: 2026-10-19 14:05
            |- Length: 1:02:03
            |- Speakers: Asha, Rohan, Speaker 3
            |- Speaker engine: Deepgram nova-3
            |- Word engine: Groq whisper-large-v3
            |
            |> Two transcripts of the same recording follow. The speaker transcript
            |> knows who spoke but can mishear words. The Whisper transcript has more
            |> accurate wording but no speakers. Use the speaker transcript for who said
            |> what, and the Whisper transcript to correct the wording.
            |
            |## Meeting notes
            |
            |### Summary
            |- Rohan confirmed Friday.
            |
            |## Speaker transcript
            |
            |**[00:00] Asha:** Shall we start?
            |
            |**[00:42] Rohan:** Yes, the launch is Friday.
            |
            |**[01:01] Speaker 3:** Agreed.
            |
            |## Whisper transcript
            |
            |Shall we start? Yes, the launch is Friday. Agreed.
            |
        """.trimMargin()
        assertEquals(expected, ConversationExport.markdown(input))
        assertEquals("2026-10-19 Launch sync.md", ConversationExport.fileName(input))
    }

    @Test fun missingSectionsSayNotGenerated() {
        val input = ConversationExport.Input(
            title = "Call",
            createdAtMillis = created,
            durationMs = 90_000,
            meetingNotes = null,
            doc = null,
            names = emptyMap(),
            zone = zone,
        )
        val md = ConversationExport.markdown(input)
        assertTrue(md.contains("- Speakers: (not generated)"))
        assertTrue(md.contains("- Speaker engine: (not generated)"))
        assertTrue(md.contains("- Word engine: (not generated)"))
        assertTrue(md.contains("## Meeting notes\n\n(not generated)\n"))
        assertTrue(md.contains("## Speaker transcript\n\n(not generated)\n"))
        assertTrue(md.endsWith("## Whisper transcript\n\n(not generated)\n"))
        assertTrue(md.contains("- Length: 0:01:30"))
    }

    @Test fun titlesAreSanitisedForFilenames() {
        assertEquals("Q3 - plan- draft", ConversationExport.sanitizeTitle("Q3 / plan: draft?"))
        assertEquals("Conversation", ConversationExport.sanitizeTitle("  ///  "))
    }

    @Test fun timestampsKeepCountingMinutes() {
        assertEquals("75:12", ConversationExport.mmss(4512.9))
        assertEquals("00:00", ConversationExport.mmss(-1.0))
    }

    @Test fun speakersCopyUsesNamesAndTimestamps() {
        val text = ConversationExport.speakersPlain(fullDoc.turns.take(2), mapOf("Speaker 2" to "Rohan"))
        assertEquals("[00:00] Speaker 1: Shall we start?\n\n[00:42] Rohan: Yes, the launch is Friday.", text)
    }
}
