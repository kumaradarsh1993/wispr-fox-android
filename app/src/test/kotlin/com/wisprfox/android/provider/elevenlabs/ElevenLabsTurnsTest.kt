package com.wisprfox.android.provider.elevenlabs

import com.wisprfox.android.history.SpeakerTurn
import org.junit.Assert.assertEquals
import org.junit.Test

class ElevenLabsTurnsTest {
    @Test fun groupsWordsBySpeakerInOrderOfAppearance() {
        val words = listOf(
            ElevenLabsWord("Hello", "word", 0.0, 0.4, "speaker_1"),
            ElevenLabsWord(" ", "spacing", 0.4, 0.5, "speaker_1"),
            ElevenLabsWord("there.", "word", 0.5, 0.9, "speaker_1"),
            ElevenLabsWord(" ", "spacing", 0.9, 1.0, "speaker_1"),
            ElevenLabsWord("(laughs)", "audio_event", 1.0, 1.2, "speaker_0"),
            ElevenLabsWord("Hi", "word", 1.2, 1.4, "speaker_0"),
            ElevenLabsWord(" ", "spacing", 1.4, 1.5, "speaker_0"),
            ElevenLabsWord("Back", "word", 2.0, 2.2, "speaker_1"),
        )
        assertEquals(
            listOf(
                SpeakerTurn("Speaker 1", "Hello there.", 0.0, 0.9),
                SpeakerTurn("Speaker 2", "Hi", 1.2, 1.4),
                SpeakerTurn("Speaker 1", "Back", 2.0, 2.2),
            ),
            ElevenLabsTurns.fromWords(words),
        )
    }
}
